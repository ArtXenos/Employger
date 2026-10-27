// Reports page: monthly SSS / PhilHealth / Pag-IBIG / BIR 1601-C figures with
// CSV downloads, the monthly attendance and overtime summaries, 13th month
// pay, and BIR Form 2316 for one employee and year. The server
// limits these to administrators and HR managers.

const AGENCIES = [
  { key: "sss", report: "sss", body: "sssTableBody", info: "sssInfo", csv: "sssCsv", withEc: true },
  { key: "philhealth", report: "philHealth", body: "philHealthTableBody", info: "philHealthInfo", csv: "philHealthCsv", withEc: false },
  { key: "pagibig", report: "pagIbig", body: "pagIbigTableBody", info: "pagIbigInfo", csv: "pagIbigCsv", withEc: false },
];

const $ = (id) => document.getElementById(id);

document.addEventListener("DOMContentLoaded", () => {
  const today = new Date();
  const month = String(today.getMonth() + 1).padStart(2, "0");

  $("reportMonth").value = `${today.getFullYear()}-${month}`;
  $("summaryMonth").value = `${today.getFullYear()}-${month}`;
  $("form2316Year").value = today.getFullYear();
  $("thirteenthYear").value = today.getFullYear();

  $("reportMonth").addEventListener("change", loadMonthly);
  $("summaryMonth").addEventListener("change", loadSummaries);
  $("thirteenthYear").addEventListener("change", loadThirteenthMonth);
  $("form2316Form").addEventListener("submit", loadForm2316);
  $("print2316Button").addEventListener("click", () => window.print());

  $("logoutButton").addEventListener("click", (event) => {
    event.preventDefault();
    logout();
  });

  loadMonthly();
  loadSummaries();
  loadThirteenthMonth();
  loadEmployees();
});

// ---------- requests ----------

// Fetches JSON; throws an Error with the server's message on failure.
async function api(url) {
  const response = await fetch(url);

  if (response.status === 401) {
    window.location.href = "/";
    throw new Error("Please log in.");
  }

  if (response.status === 403) {
    const error = new Error("Reports are available to administrators and HR managers only.");
    error.denied = true;
    throw error;
  }

  if (!response.ok) {
    const text = (await response.text()).trim();
    throw new Error(text || `Request failed (${response.status}).`);
  }

  return response.json();
}

async function loadEmployees() {
  const select = $("form2316Employee");
  let employees = [];

  try {
    employees = await api("/api/employees");
  } catch (error) {
    showError("form2316Message", error.message);
  }

  select.innerHTML = "";

  employees
    .slice()
    .sort((a, b) => `${a.lastName} ${a.firstName}`.localeCompare(`${b.lastName} ${b.firstName}`))
    .forEach((employee) => {
      const option = document.createElement("option");
      option.value = employee.employeeId;
      option.textContent = `${employee.lastName}, ${employee.firstName} (#${employee.employeeId})`;
      select.appendChild(option);
    });
}

// ---------- monthly remittances ----------

async function loadMonthly() {
  const [year, month] = ($("reportMonth").value || "").split("-").map(Number);

  clearMessage("monthlyMessage");
  $("monthlyNotes").innerHTML = "";

  if (!year || !month) {
    $("monthlySummary").textContent = "Choose a month.";
    return;
  }

  AGENCIES.forEach((agency) => {
    setEmptyRow($(agency.body), agency.withEc ? 6 : 5, "Loading...");
    $(agency.csv).href = `/api/reports/monthly/${agency.key}.csv?year=${year}&month=${month}`;
  });

  let report;

  try {
    report = await api(`/api/reports/monthly?year=${year}&month=${month}`);
  } catch (error) {
    showError("monthlyMessage", error.message);
    $("monthlySummary").textContent = "";

    // Nothing else on the page will work for this role.
    if (error.denied) {
      document.querySelectorAll(".records-section").forEach((section, index) => {
        section.classList.toggle("hidden", index > 0);
      });
      $("reportMonth").disabled = true;
      return;
    }

    AGENCIES.forEach((agency) => setEmptyRow($(agency.body), agency.withEc ? 6 : 5, "-"));
    setEmptyRow($("birTableBody"), 2, "-");
    return;
  }

  $("monthlySummary").textContent =
    `${report.period}: ${report.payrollCount} processed or paid payroll.`;

  renderNotes("monthlyNotes", report.notes);
  AGENCIES.forEach((agency) => renderAgency(agency, report[agency.report]));
  renderBir1601c(report.bir1601c);
}

function renderAgency(agency, table) {
  const columns = agency.withEc ? 6 : 5;
  const body = $(agency.body);

  $(agency.info).textContent = `${table.form}. Due: ${table.dueDate}.`;

  if (table.rows.length === 0) {
    setEmptyRow(body, columns, "No processed or paid payroll in this month.");
    return;
  }

  body.innerHTML = "";

  table.rows.forEach((row) => {
    const tr = document.createElement("tr");
    const idNumber = row.idNumber
      ? escapeHtml(row.idNumber)
      : `<span class="badge badge-regular">Missing</span>`;

    tr.innerHTML = `
      <td>${escapeHtml(row.employeeName)}</td>
      <td>${idNumber}</td>
      <td class="money">${formatMoney(row.employeeShare)}</td>
      <td class="money">${formatMoney(row.employerShare)}</td>
      ${agency.withEc ? `<td class="money">${formatMoney(row.ec)}</td>` : ""}
      <td class="money">${formatMoney(row.total)}</td>`;

    body.appendChild(tr);
  });

  const total = document.createElement("tr");
  total.className = "total-row";
  total.innerHTML = `
    <td colspan="2">Total</td>
    <td class="money">${formatMoney(table.totalEmployeeShare)}</td>
    <td class="money">${formatMoney(table.totalEmployerShare)}</td>
    ${agency.withEc ? `<td class="money">${formatMoney(table.totalEc)}</td>` : ""}
    <td class="money">${formatMoney(table.total)}</td>`;

  body.appendChild(total);
}

function renderBir1601c(bir) {
  $("birInfo").textContent = `Monthly remittance return of income taxes withheld on compensation. Due: ${bir.dueDate}.`;

  renderFigures($("birTableBody"), [
    ["Employees paid", String(bir.employees)],
    ["Total compensation", formatMoney(bir.totalCompensation)],
    ["Statutory minimum wage (MWE)", formatMoney(bir.mweStatutoryMinimumWage)],
    ["Holiday, overtime, night differential and hazard pay (MWE)", formatMoney(bir.mweHolidayOvertimeNightHazardPay)],
    ["13th month pay and other benefits (exempt)", formatMoney(bir.exemptThirteenthMonthAndBenefits)],
    ["De minimis benefits", formatMoney(bir.deMinimisBenefits)],
    ["SSS, PhilHealth and Pag-IBIG employee contributions", formatMoney(bir.employeeContributions)],
    ["Total non-taxable compensation", formatMoney(bir.totalNonTaxable), true],
    ["Taxable compensation", formatMoney(bir.taxableCompensation), true],
    ["Tax withheld", formatMoney(bir.taxWithheld)],
    ["Less: year-end refunds", formatMoney(bir.yearEndRefunds)],
    ["Net tax to remit", formatMoney(bir.netTaxToRemit), true],
  ]);
}

// ---------- 13th month pay ----------

// ---------- attendance and overtime summaries ----------

async function loadSummaries() {
  const [year, month] = ($("summaryMonth").value || "").split("-").map(Number);
  const attendanceBody = $("attendanceSummaryTableBody");
  const overtimeBody = $("overtimeSummaryTableBody");

  clearMessage("summaryMessage");

  if (!year || !month) {
    setEmptyRow(attendanceBody, 7, "Choose a month.");
    setEmptyRow(overtimeBody, 5, "Choose a month.");
    return;
  }

  setEmptyRow(attendanceBody, 7, "Loading...");
  setEmptyRow(overtimeBody, 5, "Loading...");

  let attendance;
  let overtime;

  try {
    [attendance, overtime] = await Promise.all([
      api(`/api/reports/attendance-summary?year=${year}&month=${month}`),
      api(`/api/reports/overtime-summary?year=${year}&month=${month}`),
    ]);
  } catch (error) {
    // A denied role already sees the message in the first section.
    if (!error.denied) {
      showError("summaryMessage", error.message);
    }
    setEmptyRow(attendanceBody, 7, "-");
    setEmptyRow(overtimeBody, 5, "-");
    return;
  }

  renderAttendanceSummary(attendance);
  renderOvertimeSummary(overtime);
}

function renderAttendanceSummary(report) {
  const body = $("attendanceSummaryTableBody");

  if (report.employees.length === 0) {
    setEmptyRow(body, 7, `No employees in ${report.period}.`);
    return;
  }

  body.innerHTML = "";

  report.employees.forEach((row) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${escapeHtml(row.employeeName)}</td>
      <td>${escapeHtml(row.department)}</td>
      <td class="money">${row.present}</td>
      <td class="money">${row.late}</td>
      <td class="money">${row.absent}</td>
      <td class="money">${row.onLeave}</td>
      <td class="money">${formatHours(row.regularHours)}</td>`;
    body.appendChild(tr);
  });

  const total = document.createElement("tr");
  total.className = "total-row";
  total.innerHTML = `
    <td colspan="2">Total</td>
    <td class="money">${report.totalPresent}</td>
    <td class="money">${report.totalLate}</td>
    <td class="money">${report.totalAbsent}</td>
    <td class="money">${report.totalOnLeave}</td>
    <td class="money">${formatHours(report.totalRegularHours)}</td>`;
  body.appendChild(total);
}

function renderOvertimeSummary(report) {
  const body = $("overtimeSummaryTableBody");

  $("overtimeSummaryInfo").textContent =
    `Approved overtime in ${report.period}. Pending and rejected overtime is left out. ` +
    `Company-wide: ${formatHours(report.totalHours)} hours over ${report.employees.length} employees, ` +
    `an average of ${formatHours(report.averageHoursPerEmployee)} hours per employee ` +
    `(${report.employeesWithOvertime} worked overtime).`;

  if (report.employees.length === 0) {
    setEmptyRow(body, 5, `No employees in ${report.period}.`);
    return;
  }

  body.innerHTML = "";

  report.employees.forEach((row) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${escapeHtml(row.employeeName)}</td>
      <td>${escapeHtml(row.department)}</td>
      <td class="money">${row.overtimeDays}</td>
      <td class="money">${formatHours(row.totalHours)}</td>
      <td class="money">${row.overtimeDays > 0 ? formatHours(row.averageHours) : "-"}</td>`;
    body.appendChild(tr);
  });

  const total = document.createElement("tr");
  total.className = "total-row";
  total.innerHTML = `
    <td colspan="2">Total</td>
    <td class="money">${report.overtimeDays}</td>
    <td class="money">${formatHours(report.totalHours)}</td>
    <td class="money">${report.overtimeDays > 0 ? formatHours(report.averageHoursPerOvertimeDay) : "-"}</td>`;
  body.appendChild(total);
}

function formatHours(value) {
  return Number(value || 0).toLocaleString("en-PH", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

async function loadThirteenthMonth() {
  const year = Number($("thirteenthYear").value);
  const body = $("thirteenthTableBody");

  clearMessage("thirteenthMessage");

  if (!year) {
    setEmptyRow(body, 7, "Choose a year.");
    return;
  }

  setEmptyRow(body, 7, "Loading...");
  $("thirteenthCsv").href = `/api/reports/thirteenth-month.csv?year=${year}`;

  let report;

  try {
    report = await api(`/api/reports/thirteenth-month?year=${year}`);
  } catch (error) {
    // A denied role already sees the message in the first section.
    if (!error.denied) {
      showError("thirteenthMessage", error.message);
    }
    setEmptyRow(body, 7, "-");
    return;
  }

  $("thirteenthInfo").textContent =
    `Basic salary earned / 12 (PD 851), from processed, approved and paid payroll so far. ` +
    `Due by ${report.dueDate}. Enter or top it up with Generate 13th Month Pay on the Benefits page.`;

  if (report.employees.length === 0) {
    setEmptyRow(body, 7, "No employees.");
    return;
  }

  body.innerHTML = "";

  report.employees.forEach((row) => {
    const tr = document.createElement("tr");
    const due = Number(row.balanceDue);

    tr.innerHTML = `
      <td>${escapeHtml(row.employeeName)}</td>
      <td class="money">${row.eligible ? formatMoney(row.basicSalaryEarned) : "-"}</td>
      <td class="money">${row.eligible ? formatMoney(row.earned) : "-"}</td>
      <td class="money">${row.eligible ? formatMoney(row.paid) : "-"}</td>
      <td class="money">${row.eligible ? formatMoney(row.entered) : "-"}</td>
      <td class="money">${row.eligible && due !== 0 ? formatMoney(row.balanceDue) : "-"}</td>
      <td>${escapeHtml(row.status)}</td>`;

    body.appendChild(tr);
  });

  const total = document.createElement("tr");
  total.className = "total-row";
  total.innerHTML = `
    <td colspan="2">Total</td>
    <td class="money">${formatMoney(report.totalEarned)}</td>
    <td class="money">${formatMoney(report.totalPaid)}</td>
    <td class="money">${formatMoney(report.totalEntered)}</td>
    <td class="money">${formatMoney(report.totalBalanceDue)}</td>
    <td></td>`;

  body.appendChild(total);
}

// ---------- BIR 2316 ----------

async function loadForm2316(event) {
  event.preventDefault();

  const employeeId = $("form2316Employee").value;
  const year = $("form2316Year").value;

  clearMessage("form2316Message");
  $("form2316Notes").innerHTML = "";
  $("form2316Result").classList.add("hidden");
  $("print2316Button").classList.add("hidden");

  if (!employeeId) {
    showError("form2316Message", "Choose an employee.");
    return;
  }

  let form;

  try {
    form = await api(`/api/reports/2316?year=${encodeURIComponent(year)}&employeeId=${encodeURIComponent(employeeId)}`);
  } catch (error) {
    showError("form2316Message", error.message);
    return;
  }

  renderNotes("form2316Notes", form.notes);

  const rows = [
    ["Part I - Employee", null, "heading"],
    ["Name", escapeHtml(form.employeeName)],
    ["TIN", form.tin ? escapeHtml(form.tin) : `<span class="badge badge-regular">Missing</span>`],
    ["Address", escapeHtml(form.address || "-")],
    ["Postal code", escapeHtml(form.postalCode || "-")],
    ["Date of birth", escapeHtml(form.dateOfBirth || "-")],
    ["Minimum wage earner", form.minimumWageEarner ? "Yes" : "No"],
    ["Period", `${escapeHtml(form.periodFrom || "-")} to ${escapeHtml(form.periodTo || "-")} (${form.payrollCount} payroll)`],
    ["Part II - Employer", null, "heading"],
    ["Present employer", escapeHtml(form.employerName || "-")],
    ["Previous employer", escapeHtml(form.priorEmployerName || "None")],
    ["Part IV-A - Summary", null, "heading"],
    ["Gross compensation, present employer", formatMoney(form.grossCompensation)],
    ["Less: total non-taxable compensation", formatMoney(form.totalNonTaxable)],
    ["Taxable compensation, present employer", formatMoney(form.taxablePresentEmployer)],
    ["Add: taxable compensation, previous employer", formatMoney(form.taxablePriorEmployer)],
    ["Gross taxable compensation", formatMoney(form.totalTaxable), "total"],
    [form.annualized ? "Tax due (annualized)" : "Tax due (not yet annualized)", formatMoney(form.taxDue), "total"],
    ["Tax withheld, present employer", formatMoney(form.taxWithheldPresentEmployer)],
    ["Tax withheld, previous employer", formatMoney(form.taxWithheldPriorEmployer)],
    ["Total tax withheld", formatMoney(form.totalTaxWithheld), "total"],
    ["Part IV-B - Details of non-taxable compensation", null, "heading"],
    ["Basic salary", formatMoney(form.basicSalary)],
    ["Statutory minimum wage (MWE)", formatMoney(form.mweStatutoryMinimumWage)],
    ["Holiday, overtime, night differential and hazard pay (MWE)", formatMoney(form.mweHolidayOvertimeNightHazardPay)],
    ["13th month pay and other benefits", formatMoney(form.exemptThirteenthMonthAndBenefits)],
    ["De minimis benefits", formatMoney(form.deMinimisBenefits)],
    ["SSS, PhilHealth and Pag-IBIG employee contributions", formatMoney(form.employeeContributions)],
  ];

  const body = $("form2316TableBody");
  body.innerHTML = "";

  rows.forEach(([label, value, kind]) => {
    const tr = document.createElement("tr");

    if (kind === "heading") {
      tr.className = "figures-heading";
      tr.innerHTML = `<th colspan="2" scope="colgroup">${escapeHtml(label)}</th>`;
    } else {
      if (kind === "total") {
        tr.className = "total-row";
      }
      // value is already escaped or formatted above.
      tr.innerHTML = `<th scope="row">${escapeHtml(label)}</th><td class="money">${value}</td>`;
    }

    body.appendChild(tr);
  });

  $("form2316Result").classList.remove("hidden");
  $("print2316Button").classList.remove("hidden");
}

// ---------- helpers ----------

// rows: [label, already-formatted value, isTotal]
function renderFigures(body, rows) {
  body.innerHTML = "";

  rows.forEach(([label, value, isTotal]) => {
    const tr = document.createElement("tr");

    if (isTotal) {
      tr.className = "total-row";
    }

    tr.innerHTML = `<th scope="row">${escapeHtml(label)}</th><td class="money">${escapeHtml(value)}</td>`;
    body.appendChild(tr);
  });
}

function renderNotes(id, notes) {
  const list = $(id);
  list.innerHTML = "";

  (notes || []).forEach((note) => {
    const item = document.createElement("li");
    item.textContent = note;
    list.appendChild(item);
  });
}

function showError(id, text) {
  $(id).textContent = text;
  $(id).className = "form-message report-message error";
}

function clearMessage(id) {
  $(id).textContent = "";
  $(id).className = "form-message report-message";
}

function setEmptyRow(body, columns, text) {
  body.innerHTML = `<tr><td colspan="${columns}" class="empty-row"></td></tr>`;
  body.querySelector("td").textContent = text;
}

function formatMoney(value) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }

  return "₱" + Number(value).toLocaleString("en-PH", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function escapeHtml(value) {
  const div = document.createElement("div");
  div.textContent = value === null || value === undefined ? "" : String(value);
  return div.innerHTML;
}

async function logout() {
  try {
    await fetch("/api/auth/logout", { method: "POST" });
  } finally {
    window.location.href = "/";
  }
}

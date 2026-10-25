// Employee self-service page. All data comes from /api/me/*, which the
// server limits to the employee linked to the logged-in account.
// Values are rendered with textContent only.
//
// Employees can also file overtime, print a payslip, view and print their
// BIR 2316, and change their contact details. csrf.js adds the security
// token to every write request.

const payrollTableBody = document.getElementById("payrollTableBody");
const attendanceTableBody = document.getElementById("attendanceTableBody");
const overtimeTableBody = document.getElementById("overtimeTableBody");
const leaveTableBody = document.getElementById("leaveTableBody");
const payslipDetail = document.getElementById("payslipDetail");
const payslipTitle = document.getElementById("payslipTitle");
const payslipLines = document.getElementById("payslipLines");
const pageMessage = document.getElementById("pageMessage");

let profile = null;

const peso = new Intl.NumberFormat("en-PH", { style: "currency", currency: "PHP" });

async function getJson(url) {
  const response = await fetch(url, { credentials: "same-origin" });

  if (response.status === 401) {
    window.location.href = "/";
    throw new Error("Please log in.");
  }

  if (!response.ok) {
    const message = (await response.text()).trim();
    throw new Error(message || `Unable to load ${url} (HTTP ${response.status}).`);
  }

  return response.json();
}

// Writes JSON; on failure throws the server's plain-text message.
async function sendJson(method, url, body) {
  const response = await fetch(url, {
    method,
    credentials: "same-origin",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify(body),
  });

  if (response.status === 401) {
    window.location.href = "/";
    throw new Error("Please log in.");
  }

  if (!response.ok) {
    const message = (await response.text()).trim();
    throw new Error(message || `Request failed (HTTP ${response.status}).`);
  }

  return response.json();
}

function setFeedback(element, message, kind) {
  element.textContent = message || "";
  element.className = "form-feedback" + (kind ? " " + kind : "");
}

function localDate(date) {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");
}

// Prints one document: my-payroll.css hides everything else.
function printOnly(name) {
  document.body.dataset.print = name;
  window.print();
}

window.addEventListener("afterprint", () => {
  delete document.body.dataset.print;
});

function money(value) {
  return value === null || value === undefined ? "—" : peso.format(Number(value));
}

function text(value) {
  return value === null || value === undefined || value === "" ? "—" : String(value);
}

function cell(content, className) {
  const td = document.createElement("td");

  if (content instanceof Node) {
    td.appendChild(content);
  } else {
    td.textContent = content;
  }

  if (className) {
    td.className = className;
  }

  return td;
}

function showEmpty(tableBody, columns, message) {
  const row = document.createElement("tr");
  const td = cell(message, "empty");

  td.colSpan = columns;
  row.appendChild(td);
  tableBody.replaceChildren(row);
}

function showError(message) {
  pageMessage.textContent = message;
}

// ---------- payroll ----------

function renderPayroll(records) {
  if (records.length === 0) {
    showEmpty(payrollTableBody, 6, "No processed or paid payroll yet.");
    return;
  }

  payrollTableBody.replaceChildren(
    ...records.map((payroll) => {
      const row = document.createElement("tr");
      const button = document.createElement("button");

      button.type = "button";
      button.className = "view-button";
      button.textContent = "View";
      button.setAttribute("aria-label", `View payslip for ${payroll.payCycleName}`);
      button.addEventListener("click", () => showPayslip(payroll, row));

      row.append(
        cell(text(payroll.payCycleName)),
        cell(text(payroll.payDate)),
        cell(text(payroll.status)),
        cell(money(payroll.grossPay), "amount"),
        cell(money(payroll.netPay), "amount"),
        cell(button),
      );

      return row;
    }),
  );
}

function showPayslip(payroll, row) {
  payrollTableBody
    .querySelectorAll("tr.selected")
    .forEach((selected) => selected.classList.remove("selected"));
  row.classList.add("selected");

  payslipTitle.textContent =
    `Payslip — ${payroll.payCycleName} (${payroll.cycleStartDate} to ${payroll.cycleEndDate})`;
  document.getElementById("payslipEmployee").textContent = profile
    ? `${profile.firstName} ${profile.lastName} · Employee #${profile.employeeId}`
    : "";

  const lines = [
    ["Base pay", money(payroll.basePay)],
    ["Attendance deduction", money(payroll.attendanceDeduction)],
    ["Regular pay", money(payroll.regularPay)],
    ["Overtime pay", money(payroll.overtimePay)],
    ["Bonus", money(payroll.bonusAmount)],
    ...(Number(payroll.adjustmentAmount || 0) !== 0
      ? [["Corrections of earlier pay", money(payroll.adjustmentAmount)]]
      : []),
    ["Gross pay", money(payroll.grossPay)],
    ["SSS", money(payroll.sssContribution)],
    ["PhilHealth", money(payroll.philHealthContribution)],
    ["Pag-IBIG", money(payroll.pagIbigContribution)],
    ["Total contributions", money(payroll.deductionAmount)],
    ["Withholding tax", money(payroll.taxAmount)],
    ["Net pay", money(payroll.netPay), true],
    ["Status", text(payroll.status)],
    ["Paid on", text(payroll.paymentDate ? payroll.paymentDate.slice(0, 10) : null)],
    ["Payment method", text(payroll.paymentMethod)],
  ];

  payslipLines.replaceChildren(
    ...lines.flatMap(([label, value, total]) => {
      const dt = document.createElement("dt");
      const dd = document.createElement("dd");

      dt.textContent = label;
      dd.textContent = value;

      if (total) {
        dt.className = "total";
        dd.className = "total";
      }

      return [dt, dd];
    }),
  );

  // A PDF payslip is proof of payment, so only for PAID payroll.
  const pdfButton = document.getElementById("pdfPayslipButton");
  pdfButton.hidden = payroll.status !== "PAID";
  pdfButton.onclick = () => {
    window.location.href = `/api/me/payroll/${encodeURIComponent(payroll.payrollId)}/pdf`;
  };

  payslipDetail.hidden = false;
  payslipDetail.focus();
}

// ---------- attendance and overtime ----------

function renderAttendance(records) {
  if (records.length === 0) {
    showEmpty(attendanceTableBody, 6, "No attendance recorded yet.");
    return;
  }

  attendanceTableBody.replaceChildren(
    ...records.map((record) => {
      const row = document.createElement("tr");

      row.append(
        cell(text(record.date)),
        cell(text(record.timeIn)),
        cell(text(record.timeOut)),
        cell(text(record.regularHours), "amount"),
        cell(text(record.status)),
        cell(text(record.remarks), "wrap"),
      );

      return row;
    }),
  );
}

function renderOvertime(records) {
  if (records.length === 0) {
    showEmpty(overtimeTableBody, 4, "No overtime recorded yet.");
    return;
  }

  overtimeTableBody.replaceChildren(
    ...records.map((record) => {
      const row = document.createElement("tr");

      row.append(
        cell(text(record.date)),
        cell(text(record.hours), "amount"),
        cell(money(record.amount), "amount"),
        cell(text(record.status)),
      );

      return row;
    }),
  );
}

// ---------- filing overtime ----------

const overtimeForm = document.getElementById("overtimeForm");
const overtimeDate = document.getElementById("overtimeDate");
const overtimeHours = document.getElementById("overtimeHours");
const overtimeMessage = document.getElementById("overtimeMessage");
const overtimeSubmit = document.getElementById("overtimeSubmit");

overtimeDate.max = localDate(new Date());

overtimeForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const hours = Number(overtimeHours.value);
  overtimeDate.removeAttribute("aria-invalid");
  overtimeHours.removeAttribute("aria-invalid");

  if (!overtimeDate.value) {
    overtimeDate.setAttribute("aria-invalid", "true");
    setFeedback(overtimeMessage, "Choose the date you worked overtime.", "error");
    overtimeDate.focus();
    return;
  }

  if (!overtimeHours.value || !(hours > 0) || hours > 24) {
    overtimeHours.setAttribute("aria-invalid", "true");
    setFeedback(overtimeMessage, "Enter overtime hours greater than 0 and at most 24.", "error");
    overtimeHours.focus();
    return;
  }

  overtimeSubmit.disabled = true;
  setFeedback(overtimeMessage, "Filing...", null);

  try {
    const filed = await sendJson("POST", "/api/me/overtime", { date: overtimeDate.value, hours });

    overtimeForm.reset();
    renderOvertime(await getJson("/api/me/overtime"));
    setFeedback(
      overtimeMessage,
      `Overtime filed for ${filed.date} (${filed.hours} h, ${money(filed.amount)}). Waiting for HR approval.`,
      "success",
    );
  } catch (error) {
    setFeedback(overtimeMessage, error.message, "error");
  } finally {
    overtimeSubmit.disabled = false;
  }
});

// ---------- payslip printing ----------

document.getElementById("printPayslipButton").addEventListener("click", () => printOnly("payslip"));

// ---------- BIR 2316 ----------

const form2316Form = document.getElementById("form2316Form");
const form2316Year = document.getElementById("form2316Year");
const form2316Message = document.getElementById("form2316Message");
const form2316Result = document.getElementById("form2316Result");
const print2316Button = document.getElementById("print2316Button");

form2316Year.value = new Date().getFullYear();

form2316Form.addEventListener("submit", async (event) => {
  event.preventDefault();

  const year = Number(form2316Year.value);
  form2316Result.hidden = true;
  print2316Button.hidden = true;

  if (!Number.isInteger(year) || year < 2000 || year > 2100) {
    form2316Year.setAttribute("aria-invalid", "true");
    setFeedback(form2316Message, "Enter a year between 2000 and 2100.", "error");
    return;
  }

  form2316Year.removeAttribute("aria-invalid");
  setFeedback(form2316Message, "Loading...", null);

  try {
    render2316(await getJson(`/api/me/bir-2316?year=${encodeURIComponent(year)}`));
    setFeedback(form2316Message, "", null);
  } catch (error) {
    setFeedback(form2316Message, error.message, "error");
  }
});

print2316Button.addEventListener("click", () => printOnly("form2316"));

function render2316(form) {
  document.getElementById("form2316Title").textContent = `BIR Form 2316 — ${form.year}`;

  document.getElementById("form2316Notes").replaceChildren(
    ...(form.notes || []).map((note) => {
      const item = document.createElement("li");
      item.textContent = note;
      return item;
    }),
  );

  const rows = [
    ["Part I - Employee", null, "heading"],
    ["Name", text(form.employeeName)],
    ["TIN", form.tin ? form.tin : "Missing — please give your TIN to HR"],
    ["Address", text(form.address)],
    ["Date of birth", text(form.dateOfBirth)],
    ["Minimum wage earner", form.minimumWageEarner ? "Yes" : "No"],
    ["Period", `${text(form.periodFrom)} to ${text(form.periodTo)} (${form.payrollCount} payroll)`],
    ["Part II - Employer", null, "heading"],
    ["Present employer", text(form.employerName)],
    ["Previous employer", form.priorEmployerName || "None"],
    ["Part IV-A - Summary", null, "heading"],
    ["Gross compensation, present employer", money(form.grossCompensation)],
    ["Less: total non-taxable compensation", money(form.totalNonTaxable)],
    ["Taxable compensation, present employer", money(form.taxablePresentEmployer)],
    ["Add: taxable compensation, previous employer", money(form.taxablePriorEmployer)],
    ["Gross taxable compensation", money(form.totalTaxable), "total"],
    [form.annualized ? "Tax due (annualized)" : "Tax due (not yet annualized)", money(form.taxDue), "total"],
    ["Tax withheld, present employer", money(form.taxWithheldPresentEmployer)],
    ["Tax withheld, previous employer", money(form.taxWithheldPriorEmployer)],
    ["Total tax withheld", money(form.totalTaxWithheld), "total"],
    ["Part IV-B - Details of non-taxable compensation", null, "heading"],
    ["Basic salary", money(form.basicSalary)],
    ["Statutory minimum wage (MWE)", money(form.mweStatutoryMinimumWage)],
    ["Holiday, overtime, night differential and hazard pay (MWE)", money(form.mweHolidayOvertimeNightHazardPay)],
    ["13th month pay and other benefits", money(form.exemptThirteenthMonthAndBenefits)],
    ["De minimis benefits", money(form.deMinimisBenefits)],
    ["SSS, PhilHealth and Pag-IBIG employee contributions", money(form.employeeContributions)],
  ];

  document.getElementById("form2316TableBody").replaceChildren(
    ...rows.map(([label, value, kind]) => {
      const row = document.createElement("tr");
      const th = document.createElement("th");

      th.textContent = label;

      if (kind === "heading") {
        row.className = "figures-heading";
        th.colSpan = 2;
        th.scope = "colgroup";
        row.appendChild(th);
        return row;
      }

      th.scope = "row";
      row.className = kind === "total" ? "total-row" : "";
      row.append(th, cell(value, "amount"));
      return row;
    }),
  );

  form2316Result.hidden = false;
  print2316Button.hidden = false;
}

// ---------- 13th month pay ----------

const thirteenthForm = document.getElementById("thirteenthForm");
const thirteenthYear = document.getElementById("thirteenthYear");
const thirteenthMessage = document.getElementById("thirteenthMessage");
const thirteenthResult = document.getElementById("thirteenthResult");

thirteenthYear.value = new Date().getFullYear();

thirteenthForm.addEventListener("submit", (event) => {
  event.preventDefault();
  loadThirteenthMonth();
});

async function loadThirteenthMonth() {
  const year = Number(thirteenthYear.value);
  thirteenthResult.hidden = true;

  if (!Number.isInteger(year) || year < 2000 || year > 2100) {
    thirteenthYear.setAttribute("aria-invalid", "true");
    setFeedback(thirteenthMessage, "Enter a year between 2000 and 2100.", "error");
    return;
  }

  thirteenthYear.removeAttribute("aria-invalid");
  setFeedback(thirteenthMessage, "Loading...", null);

  let status;

  try {
    status = await getJson(`/api/me/thirteenth-month?year=${encodeURIComponent(year)}`);
  } catch (error) {
    setFeedback(thirteenthMessage, error.message, "error");
    return;
  }

  setFeedback(thirteenthMessage, status.eligible ? "" : status.status, null);

  if (!status.eligible) {
    return;
  }

  const rows = [
    ["Basic salary earned so far", money(status.basicSalaryEarned)],
    ["13th month pay earned (÷ 12)", money(status.earned), "total"],
    ["Already paid", money(status.paid)],
    ["Entered, paid with an upcoming payroll", money(status.entered)],
    ["Still to be entered by HR", money(Math.max(0, Number(status.balanceDue)))],
  ];

  document.getElementById("thirteenthTableBody").replaceChildren(
    ...rows.map(([label, value, kind]) => {
      const row = document.createElement("tr");
      const th = document.createElement("th");

      th.textContent = label;
      th.scope = "row";
      row.className = kind === "total" ? "total-row" : "";
      row.append(th, cell(value, "amount"));
      return row;
    }),
  );

  thirteenthResult.hidden = false;
}

loadThirteenthMonth();

// ---------- my details ----------

const contactForm = document.getElementById("contactForm");
const contactEmail = document.getElementById("contactEmail");
const contactPhone = document.getElementById("contactPhone");
const contactAddress = document.getElementById("contactAddress");
const contactMessage = document.getElementById("contactMessage");
const contactSubmit = document.getElementById("contactSubmit");

function renderProfile(data) {
  profile = data;

  document.getElementById("profileSummary").textContent =
    [`${data.firstName} ${data.lastName}`, data.jobTitle, data.department].filter(Boolean).join(" · ");

  const details = [
    ["Name", `${data.firstName} ${data.lastName}`],
    ["Employee number", `#${data.employeeId}`],
    ["Department", text(data.department)],
    ["Job title", text(data.jobTitle)],
    ["Date hired", text(data.dateHired)],
  ];

  document.getElementById("detailsReadOnly").replaceChildren(
    ...details.flatMap(([label, value]) => {
      const dt = document.createElement("dt");
      const dd = document.createElement("dd");
      dt.textContent = label;
      dd.textContent = value;
      return [dt, dd];
    }),
  );

  contactEmail.value = data.email || "";
  contactPhone.value = data.phone || "";
  contactAddress.value = data.address || "";
}

contactForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const email = contactEmail.value.trim();
  contactEmail.removeAttribute("aria-invalid");

  if (!email || !contactEmail.checkValidity()) {
    contactEmail.setAttribute("aria-invalid", "true");
    setFeedback(contactMessage, "Enter a valid email address.", "error");
    contactEmail.focus();
    return;
  }

  contactSubmit.disabled = true;
  setFeedback(contactMessage, "Saving...", null);

  try {
    renderProfile(await sendJson("PUT", "/api/me/contact", {
      email,
      phone: contactPhone.value.trim(),
      address: contactAddress.value.trim(),
    }));
    setFeedback(contactMessage, "Your contact details were saved.", "success");
  } catch (error) {
    setFeedback(contactMessage, error.message, "error");
  } finally {
    contactSubmit.disabled = false;
  }
});

// ---------- leave ----------

const leaveForm = document.getElementById("leaveForm");
const leaveType = document.getElementById("leaveType");
const leaveStart = document.getElementById("leaveStart");
const leaveEnd = document.getElementById("leaveEnd");
const leaveReason = document.getElementById("leaveReason");
const leaveMessage = document.getElementById("leaveMessage");
const leaveSubmit = document.getElementById("leaveSubmit");
const leaveBalance = document.getElementById("leaveBalance");

const STATUS_LABELS = { PENDING: "Waiting for HR", APPROVED: "Approved", REJECTED: "Rejected" };

function renderLeave(data) {
  const paid = data.paidLeave;
  leaveBalance.innerHTML = "";
  leaveBalance.append(
    "Paid leave in " + paid.year + ": ",
    Object.assign(document.createElement("strong"), { textContent: `${paid.remaining} of ${paid.allowed} days left` }),
    ` (${paid.used} used, ${paid.pending} waiting for approval).`,
  );

  if (data.requests.length === 0) {
    showEmpty(leaveTableBody, 5, "No leave filed yet.");
    return;
  }

  leaveTableBody.replaceChildren(
    ...data.requests.map((leave) => {
      const row = document.createElement("tr");
      const dates = leave.startDate === leave.endDate ? leave.startDate : `${leave.startDate} to ${leave.endDate}`;
      let action = "";

      if (leave.status === "PENDING") {
        action = document.createElement("button");
        action.type = "button";
        action.className = "secondary-button";
        action.textContent = "Cancel";
        action.setAttribute("aria-label", `Cancel leave from ${dates}`);
        action.addEventListener("click", () => cancelLeave(leave, dates));
      }

      row.append(
        cell(dates),
        cell(leave.leaveTypeLabel),
        cell(String(leave.workingDays), "amount"),
        cell(STATUS_LABELS[leave.status] || leave.status),
        cell(action),
      );

      return row;
    }),
  );
}

async function cancelLeave(leave, dates) {
  if (!confirm(`Cancel your ${leave.leaveTypeLabel.toLowerCase()} from ${dates}?`)) {
    return;
  }

  try {
    const response = await fetch(`/api/me/leave/${encodeURIComponent(leave.leaveId)}`, {
      method: "DELETE",
      credentials: "same-origin",
    });

    if (!response.ok) {
      throw new Error((await response.text()).trim() || "This leave could not be cancelled.");
    }

    renderLeave(await getJson("/api/me/leave"));
    setFeedback(leaveMessage, `Leave from ${dates} cancelled.`, "success");
  } catch (error) {
    setFeedback(leaveMessage, error.message, "error");
  }
}

leaveStart.addEventListener("change", () => {
  if (!leaveEnd.value || leaveEnd.value < leaveStart.value) {
    leaveEnd.value = leaveStart.value;
  }
});

leaveForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  leaveStart.removeAttribute("aria-invalid");
  leaveEnd.removeAttribute("aria-invalid");

  if (!leaveStart.value || !leaveEnd.value) {
    (leaveStart.value ? leaveEnd : leaveStart).setAttribute("aria-invalid", "true");
    setFeedback(leaveMessage, "Choose the first and last day of your leave.", "error");
    return;
  }

  if (leaveEnd.value < leaveStart.value) {
    leaveEnd.setAttribute("aria-invalid", "true");
    setFeedback(leaveMessage, "The last day can't be before the first day.", "error");
    return;
  }

  leaveSubmit.disabled = true;
  setFeedback(leaveMessage, "Filing...", null);

  try {
    const filed = await sendJson("POST", "/api/me/leave", {
      leaveType: leaveType.value,
      startDate: leaveStart.value,
      endDate: leaveEnd.value,
      reason: leaveReason.value.trim(),
    });

    leaveForm.reset();
    renderLeave(await getJson("/api/me/leave"));
    setFeedback(
      leaveMessage,
      `${filed.leaveTypeLabel} filed for ${filed.workingDays} working day${filed.workingDays === 1 ? "" : "s"}. Waiting for HR approval.`,
      "success",
    );
  } catch (error) {
    setFeedback(leaveMessage, error.message, "error");
  } finally {
    leaveSubmit.disabled = false;
  }
});

// ---------- page ----------

async function loadPage() {
  try {
    renderProfile(await getJson("/api/me/profile"));
  } catch (error) {
    showError(error.message);
    showEmpty(payrollTableBody, 6, "Unavailable.");
    showEmpty(attendanceTableBody, 6, "Unavailable.");
    showEmpty(overtimeTableBody, 4, "Unavailable.");
    showEmpty(leaveTableBody, 5, "Unavailable.");
    overtimeForm.hidden = true;
    leaveForm.hidden = true;
    form2316Form.hidden = true;
    contactForm.hidden = true;
    return;
  }

  const results = await Promise.allSettled([
    getJson("/api/me/payroll").then(renderPayroll),
    getJson("/api/me/attendance").then(renderAttendance),
    getJson("/api/me/overtime").then(renderOvertime),
    getJson("/api/me/leave").then(renderLeave),
  ]);

  const failure = results.find((result) => result.status === "rejected");

  if (failure) {
    showError(failure.reason.message);
  }
}

document.getElementById("logoutButton").addEventListener("click", async () => {
  try {
    await fetch("/api/auth/logout", { method: "POST", credentials: "same-origin" });
  } finally {
    window.location.href = "/";
  }
});

loadPage();

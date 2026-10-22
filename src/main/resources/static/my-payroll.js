// Employee self-service page. All data comes from /api/me/*, which the
// server limits to the employee linked to the logged-in account.
// Values are rendered with textContent only.

const payrollTableBody = document.getElementById("payrollTableBody");
const attendanceTableBody = document.getElementById("attendanceTableBody");
const overtimeTableBody = document.getElementById("overtimeTableBody");
const payslipDetail = document.getElementById("payslipDetail");
const payslipTitle = document.getElementById("payslipTitle");
const payslipLines = document.getElementById("payslipLines");
const pageMessage = document.getElementById("pageMessage");

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

  const lines = [
    ["Base pay", money(payroll.basePay)],
    ["Attendance deduction", money(payroll.attendanceDeduction)],
    ["Regular pay", money(payroll.regularPay)],
    ["Overtime pay", money(payroll.overtimePay)],
    ["Bonus", money(payroll.bonusAmount)],
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

// ---------- page ----------

async function loadPage() {
  try {
    const profile = await getJson("/api/me/profile");

    document.getElementById("profileSummary").textContent =
      [`${profile.firstName} ${profile.lastName}`, profile.jobTitle, profile.department]
        .filter(Boolean)
        .join(" · ");
  } catch (error) {
    showError(error.message);
    showEmpty(payrollTableBody, 6, "Unavailable.");
    showEmpty(attendanceTableBody, 6, "Unavailable.");
    showEmpty(overtimeTableBody, 4, "Unavailable.");
    return;
  }

  const results = await Promise.allSettled([
    getJson("/api/me/payroll").then(renderPayroll),
    getJson("/api/me/attendance").then(renderAttendance),
    getJson("/api/me/overtime").then(renderOvertime),
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

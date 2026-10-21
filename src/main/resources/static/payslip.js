const urlParams = new URLSearchParams(window.location.search);

const payrollId = urlParams.get("payrollId");

let payslipData = null;

function formatCurrency(amount) {
  const value = Number(amount || 0);

  return new Intl.NumberFormat("en-PH", {
    style: "currency",
    currency: "PHP",
  }).format(value);
}

function formatDate(dateValue) {
  if (!dateValue) {
    return "-";
  }

  const date = new Date(dateValue);

  if (Number.isNaN(date.getTime())) {
    return dateValue;
  }

  return date.toLocaleDateString("en-PH", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}

function formatDateTime(dateValue) {
  if (!dateValue) {
    return "-";
  }

  const date = new Date(dateValue);

  if (Number.isNaN(date.getTime())) {
    return dateValue;
  }

  return date.toLocaleString("en-PH", {
    year: "numeric",
    month: "long",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

function displayValue(value) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }

  return value;
}

function showError(message) {
  document.getElementById("loadingMessage").classList.add("hidden");

  const errorMessage = document.getElementById("errorMessage");

  errorMessage.textContent = message;
  errorMessage.classList.remove("hidden");
}

function populatePayslip(payslip) {
  payslipData = payslip;

  const payroll = payslip.payroll || {};
  const employee = payroll.employee || {};
  const payCycle = payroll.payCycle || {};

  document.getElementById("employeeId").textContent = displayValue(
    employee.employeeId,
  );

  document.getElementById("employeeName").textContent =
    `${employee.firstName || ""} ${employee.lastName || ""}`.trim();

  document.getElementById("department").textContent = displayValue(
    employee.department,
  );

  document.getElementById("jobTitle").textContent = displayValue(
    employee.jobTitle,
  );

  document.getElementById("payPeriod").textContent =
    `${formatDate(payCycle.startDate)} - ${formatDate(payCycle.endDate)}`;

  document.getElementById("payDate").textContent = formatDate(payCycle.payDate);

  document.getElementById("paymentDate").textContent = formatDateTime(
    payroll.paymentDate,
  );

  document.getElementById("paymentMethod").textContent = displayValue(
    payroll.paymentMethod,
  );

  document.getElementById("paymentReference").textContent = displayValue(
    payroll.paymentReference,
  );

  document.getElementById("regularPay").textContent = formatCurrency(
    payroll.regularPay,
  );

  document.getElementById("overtimePay").textContent = formatCurrency(
    payroll.overtimePay,
  );

  document.getElementById("bonusAmount").textContent = formatCurrency(
    payroll.bonusAmount,
  );

  document.getElementById("grossPay").textContent = formatCurrency(
    payroll.grossPay,
  );

  document.getElementById("taxAmount").textContent = formatCurrency(
    payroll.taxAmount,
  );

  document.getElementById("deductionAmount").textContent = formatCurrency(
    payroll.deductionAmount,
  );

  const totalDeductions =
    Number(payroll.taxAmount || 0) + Number(payroll.deductionAmount || 0);

  document.getElementById("totalDeductions").textContent =
    formatCurrency(totalDeductions);

  document.getElementById("netPay").textContent = formatCurrency(
    payroll.netPay,
  );

  document.getElementById("payslipStatus").textContent = displayValue(
    payroll.status,
  );

  document.getElementById("generatedAt").textContent = formatDateTime(
    payslip.generatedAt,
  );

  document.getElementById("loadingMessage").classList.add("hidden");

  document.getElementById("payslipContainer").classList.remove("hidden");
}

async function loadPayslip() {
  if (!payrollId) {
    showError("No payroll ID was provided.");
    return;
  }

  try {
    const response = await fetch(`/api/payslips/payroll/${payrollId}`);

    if (!response.ok) {
      if (response.status === 404) {
        throw new Error("No payslip exists for this payroll.");
      }

      throw new Error("Unable to load the payslip.");
    }

    const payslip = await response.json();

    populatePayslip(payslip);
  } catch (error) {
    console.error("Payslip loading error:", error);

    showError(error.message || "Unable to load the payslip.");
  }
}

document.addEventListener("DOMContentLoaded", () => {
  document.getElementById("backButton").addEventListener("click", () => {
    window.location.href = "/payroll.html";
  });

  document.getElementById("printButton").addEventListener("click", () => {
    window.print();
  });

  loadPayslip();
});

document.getElementById("printPayslipButton").addEventListener("click", () => {
  window.print();
});

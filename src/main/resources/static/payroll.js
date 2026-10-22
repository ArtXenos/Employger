let payrollRecords = [];
let employees = [];
let payCycles = [];
let currentUser = null;
let currentPaymentPayrollId = null;

// =========================
// DOM ELEMENTS
// =========================

const payrollTableBody = document.getElementById("payrollTableBody");

const payCycleFilter = document.getElementById("payCycleFilter");

const statusFilter = document.getElementById("statusFilter");

const employeeSearch = document.getElementById("employeeSearch");

const payrollModal = document.getElementById("payrollModal");

const payrollForm = document.getElementById("payrollForm");

const employeeSelect = document.getElementById("employeeSelect");

const payCycleSelect = document.getElementById("payCycleSelect");

const generatePayrollButton = document.getElementById("generatePayrollButton");

const closeModalButton = document.getElementById("closeModalButton");

const cancelButton = document.getElementById("cancelButton");

const logoutButton = document.getElementById("logoutButton");

const payrollDetailsModal = document.getElementById("payrollDetailsModal");

const closeDetailsModalButton = document.getElementById(
  "closeDetailsModalButton",
);

const closeDetailsButton = document.getElementById("closeDetailsButton");

const deletePayrollButton = document.getElementById("deletePayrollButton");

const deleteConfirmModal = document.getElementById("deleteConfirmModal");

const closeDeleteConfirmButton = document.getElementById(
  "closeDeleteConfirmButton",
);

const cancelDeleteButton = document.getElementById("cancelDeleteButton");

const confirmDeleteButton = document.getElementById("confirmDeleteButton");

const deleteConfirmEmployee = document.getElementById("deleteConfirmEmployee");

const deleteConfirmPayCycle = document.getElementById("deleteConfirmPayCycle");

const deleteConfirmStatus = document.getElementById("deleteConfirmStatus");

let payrollToDelete = null;

// =========================
// INITIAL LOAD
// =========================

document.addEventListener("DOMContentLoaded", async () => {
  await loadCurrentUser();
  await loadEmployees();
  await loadPayCycles();
  await loadPayrollRecords();

  setupEventListeners();
});

// =========================
// EVENT LISTENERS
// =========================

function setupEventListeners() {
  if (payCycleFilter) {
    payCycleFilter.addEventListener("change", renderPayrollTable);
  }

  if (statusFilter) {
    statusFilter.addEventListener("change", renderPayrollTable);
  }

  if (employeeSearch) {
    employeeSearch.addEventListener("input", renderPayrollTable);
  }

  if (generatePayrollButton) {
    generatePayrollButton.addEventListener("click", openModal);
  }

  if (closeModalButton) {
    closeModalButton.addEventListener("click", closeModal);
  }

  if (cancelButton) {
    cancelButton.addEventListener("click", closeModal);
  }

  if (payrollModal) {
    payrollModal.addEventListener("click", (event) => {
      if (event.target === payrollModal) {
        closeModal();
      }
    });
  }

  if (closeDetailsModalButton) {
    closeDetailsModalButton.addEventListener("click", closePayrollDetailsModal);
  }

  if (closeDetailsButton) {
    closeDetailsButton.addEventListener("click", closePayrollDetailsModal);
  }

  if (payrollDetailsModal) {
    payrollDetailsModal.addEventListener("click", (event) => {
      if (event.target === payrollDetailsModal) {
        closePayrollDetailsModal();
      }
    });
  }

  if (deletePayrollButton) {
    deletePayrollButton.addEventListener("click", () => {
      const payrollId = Number(deletePayrollButton.dataset.payrollId);

      if (payrollId) {
        openDeleteConfirmModal(payrollId);
      }
    });
  }

  if (closeDeleteConfirmButton) {
    closeDeleteConfirmButton.addEventListener("click", closeDeleteConfirmModal);
  }

  if (cancelDeleteButton) {
    cancelDeleteButton.addEventListener("click", closeDeleteConfirmModal);
  }

  if (confirmDeleteButton) {
    confirmDeleteButton.addEventListener("click", confirmDeletePayroll);
  }

  if (deleteConfirmModal) {
    deleteConfirmModal.addEventListener("click", (event) => {
      if (event.target === deleteConfirmModal) {
        closeDeleteConfirmModal();
      }
    });
  }

  if (employeeSelect) {
    employeeSelect.addEventListener("change", updateCalculationPreview);
  }

  if (payCycleSelect) {
    payCycleSelect.addEventListener("change", updateCalculationPreview);
  }

  if (payrollForm) {
    payrollForm.addEventListener("submit", createPayroll);
  }

  if (logoutButton) {
    logoutButton.addEventListener("click", logout);
  }
}

// =========================
// CURRENT USER
// =========================

async function loadCurrentUser() {
  try {
    const response = await fetch("/api/auth/me");

    if (!response.ok) {
      window.location.href = "/index.html";
      return;
    }

    const user = await response.json();

    currentUser = user;

    const username = user.username || "Admin";

    const role = user.role || "Administrator";

    const usernameElement = document.getElementById("currentUsername");

    const roleElement = document.getElementById("currentRole");

    const avatarElement = document.getElementById("userAvatar");

    if (usernameElement) {
      usernameElement.textContent = username;
    }

    if (roleElement) {
      roleElement.textContent = formatRole(role);
    }

    if (avatarElement) {
      avatarElement.textContent = username.charAt(0).toUpperCase();
    }
  } catch (error) {
    console.error("Current user loading error:", error);
  }
}

// =========================
// EMPLOYEES
// =========================

async function loadEmployees() {
  try {
    const response = await fetch("/api/employees");

    if (response.status === 401) {
      window.location.href = "/index.html";
      return;
    }

    if (!response.ok) {
      throw new Error("Unable to load employees.");
    }

    employees = await response.json();

    populateEmployeeSelect();
  } catch (error) {
    console.error("Employee loading error:", error);
  }
}

function populateEmployeeSelect() {
  if (!employeeSelect) {
    console.error("employeeSelect element was not found.");
    return;
  }

  employeeSelect.innerHTML = '<option value="">Select employee</option>';

  employees.forEach((employee) => {
    const option = document.createElement("option");

    option.value = employee.employeeId;

    option.textContent = `${employee.firstName} ${employee.lastName}`;

    employeeSelect.appendChild(option);
  });
}

// =========================
// PAY CYCLES
// =========================

async function loadPayCycles() {
  try {
    const response = await fetch("/api/pay-cycles");

    if (response.status === 401) {
      window.location.href = "/index.html";
      return;
    }

    if (!response.ok) {
      throw new Error("Unable to load pay cycles.");
    }

    payCycles = await response.json();

    populatePayCycleSelect();

    populatePayCycleFilter();
  } catch (error) {
    console.error("Pay cycle loading error:", error);
  }
}

function populatePayCycleSelect() {
  if (!payCycleSelect) {
    console.error("payCycleSelect element was not found.");
    return;
  }

  payCycleSelect.innerHTML = '<option value="">Select pay cycle</option>';

  payCycles.forEach((cycle) => {
    const option = document.createElement("option");

    option.value = cycle.payCycleId;

    option.textContent = getPayCycleLabel(cycle);

    payCycleSelect.appendChild(option);
  });
}

function populatePayCycleFilter() {
  if (!payCycleFilter) {
    return;
  }

  payCycleFilter.innerHTML = '<option value="">All Pay Cycles</option>';

  payCycles.forEach((cycle) => {
    const option = document.createElement("option");

    option.value = cycle.payCycleId;

    option.textContent = getPayCycleLabel(cycle);

    payCycleFilter.appendChild(option);
  });
}

// =========================
// PAYROLL RECORDS
// =========================

async function loadPayrollRecords() {
  try {
    const response = await fetch("/api/payroll");

    if (response.status === 401) {
      window.location.href = "/index.html";
      return;
    }

    if (!response.ok) {
      throw new Error("Unable to load payroll records.");
    }

    payrollRecords = await response.json();

    renderPayrollTable();
    updateSummary();
  } catch (error) {
    console.error("Payroll loading error:", error);
  }
}

// =========================
// TABLE
// =========================

function renderPayrollTable() {
  if (!payrollTableBody) {
    return;
  }

  const filteredRecords = getFilteredRecords();

  if (filteredRecords.length === 0) {
    payrollTableBody.innerHTML = `
      <tr>
        <td colspan="12" class="empty-cell">
          No payroll records found.
        </td>
      </tr>
    `;

    return;
  }

  payrollTableBody.innerHTML = filteredRecords
    .map((payroll) => {
      const employee = payroll.employee || {};

      const payCycle = payroll.payCycle || {};

      const employeeName =
        `${employee.firstName || ""} ${employee.lastName || ""}`.trim() ||
        "Unknown Employee";

      const cycleLabel = getPayCycleLabel(payCycle);

      const status = payroll.status || "DRAFT";

      return `
          <tr>

            <td>
              #${payroll.payrollId}
            </td>

            <td>
              ${escapeHtml(employeeName)}
            </td>

            <td>
              ${escapeHtml(cycleLabel)}
            </td>

            <td>
              ${formatCurrency(payroll.regularPay)}
            </td>

            <td>
              ${formatCurrency(payroll.overtimePay)}
            </td>

            <td>
              ${formatCurrency(payroll.bonusAmount)}
            </td>

            <td>
              ${formatCurrency(payroll.grossPay)}
            </td>

            <td>
              ${formatCurrency(payroll.taxAmount)}
            </td>

            <td>
              ${formatCurrency(payroll.deductionAmount)}
            </td>

            <td>
              ${formatCurrency(payroll.netPay)}
            </td>

            <td>
              ${getStatusBadge(status)}
            </td>

            <td class="action-buttons">

              <button
                class="view-button"
                onclick="viewPayroll(${payroll.payrollId})"
              >
                View
              </button>

              ${
                status === "DRAFT"
                  ? `
                    <button
                      class="process-button"
                      onclick="processPayroll(${payroll.payrollId})"
                    >
                      Process
                    </button>

                    <button
                      class="delete-button"
                      onclick="deletePayroll(${payroll.payrollId})"
                    >
                      Delete
                    </button>
                  `
                  : ""
              }

              ${
                status === "PROCESSED"
                  ? `
                    <button
                      class="pay-button"
                      onclick="openPaymentModal(${payroll.payrollId})"
                    >
                      Mark as Paid
                    </button>
                  `
                  : ""
              }

              ${
                status === "PAID"
                  ? `
                    <button
                      class="payslip-button"
                      onclick="viewPayslip(${payroll.payrollId})"
                    >
                      View Payslip
                    </button>
                  `
                  : ""
              }

            </td>

          </tr>
        `;
    })
    .join("");
}

// =========================
// FILTERING
// =========================

function getFilteredRecords() {
  const selectedCycle = payCycleFilter ? payCycleFilter.value : "";

  const selectedStatus = statusFilter ? statusFilter.value : "";

  const search = employeeSearch
    ? employeeSearch.value.trim().toLowerCase()
    : "";

  return payrollRecords.filter((payroll) => {
    const employee = payroll.employee || {};

    const employeeName = `${employee.firstName || ""} ${
      employee.lastName || ""
    }`.toLowerCase();

    const cycleMatches =
      !selectedCycle ||
      String(payroll.payCycle?.payCycleId) === String(selectedCycle);

    const statusMatches = !selectedStatus || payroll.status === selectedStatus;

    const employeeMatches = !search || employeeName.includes(search);

    return cycleMatches && statusMatches && employeeMatches;
  });
}

// =========================
// SUMMARY
// =========================

function updateSummary() {
  const total = payrollRecords.length;

  const draft = payrollRecords.filter(
    (payroll) => payroll.status === "DRAFT",
  ).length;

  const processed = payrollRecords.filter(
    (payroll) => payroll.status === "PROCESSED",
  ).length;

  const paid = payrollRecords.filter(
    (payroll) => payroll.status === "PAID",
  ).length;

  const gross = payrollRecords.reduce(
    (sum, payroll) => sum + toNumber(payroll.grossPay),
    0,
  );

  const net = payrollRecords.reduce(
    (sum, payroll) => sum + toNumber(payroll.netPay),
    0,
  );

  const totalPayroll = document.getElementById("totalPayroll");

  const draftPayroll = document.getElementById("draftPayroll");

  const processedPayroll = document.getElementById("processedPayroll");

  const paidPayroll = document.getElementById("paidPayroll");

  const totalGrossPay = document.getElementById("totalGrossPay");

  const totalNetPay = document.getElementById("totalNetPay");

  if (totalPayroll) {
    totalPayroll.textContent = total;
  }

  if (draftPayroll) {
    draftPayroll.textContent = draft;
  }

  if (processedPayroll) {
    processedPayroll.textContent = processed;
  }

  if (paidPayroll) {
    paidPayroll.textContent = paid;
  }

  if (totalGrossPay) {
    totalGrossPay.textContent = formatCurrency(gross);
  }

  if (totalNetPay) {
    totalNetPay.textContent = formatCurrency(net);
  }
}

// =========================
// GENERATE PAYROLL MODAL
// =========================

function openModal() {
  if (!payrollModal) {
    return;
  }

  if (payrollForm) {
    payrollForm.reset();
  }

  resetPreview();

  payrollModal.classList.remove("hidden");
}

function closeModal() {
  if (!payrollModal) {
    return;
  }

  payrollModal.classList.add("hidden");
}

// =========================
// PAYROLL PREVIEW
// =========================

// Ignore preview responses that arrive after a newer selection was made.
let previewRequestNumber = 0;

const PREVIEW_AMOUNT_FIELDS = {
  previewBaseSalary: "baseRegularPay",
  previewAttendanceDeduction: "attendanceDeduction",
  previewRegularPay: "regularPay",
  previewOvertimePay: "overtimePay",
  previewGrossPay: "grossPay",
  previewContributions: "otherDeductions",
  previewTax: "taxAmount",
  previewNetPay: "netPay",
  previewEmployerContributions: "employerContributionTotal",
};

// Shows the server's own calculation (the same one used to generate the
// payroll), including warnings such as working days without attendance.
async function updateCalculationPreview() {
  const employeeId = employeeSelect ? employeeSelect.value : "";

  const cycleId = payCycleSelect ? payCycleSelect.value : "";

  resetPreview();

  if (!employeeId || !cycleId) {
    return;
  }

  const requestNumber = ++previewRequestNumber;

  setPreviewStatus("Calculating preview...");

  try {
    const response = await fetch(
      `/api/payroll/preview/employee/${encodeURIComponent(employeeId)}/pay-cycle/${encodeURIComponent(cycleId)}`,
    );

    if (requestNumber !== previewRequestNumber) {
      return;
    }

    if (!response.ok) {
      const errorText = (await response.text()).trim();

      setPreviewStatus(errorText || "Unable to calculate the preview.");
      return;
    }

    const preview = await response.json();

    if (requestNumber !== previewRequestNumber) {
      return;
    }

    Object.entries(PREVIEW_AMOUNT_FIELDS).forEach(([elementId, field]) => {
      const element = document.getElementById(elementId);

      if (element) {
        element.textContent = formatCurrency(preview[field]);
      }
    });

    setPreviewStatus(
      `${preview.workingDaysEmployed} of ${preview.workingDaysInCycle} working days, ` +
        `${preview.approvedOvertimeCount} approved overtime record(s).`,
    );

    const warningsList = document.getElementById("previewWarnings");

    if (warningsList) {
      (preview.warnings || []).forEach((warning) => {
        const item = document.createElement("li");

        item.textContent = warning;

        warningsList.appendChild(item);
      });
    }
  } catch (error) {
    if (requestNumber === previewRequestNumber) {
      console.error("Payroll preview error:", error);

      setPreviewStatus("Unable to reach the server for a preview.");
    }
  }
}

function setPreviewStatus(text) {
  const status = document.getElementById("previewStatus");

  if (status) {
    status.textContent = text;
  }
}

function resetPreview() {
  Object.keys(PREVIEW_AMOUNT_FIELDS).forEach((elementId) => {
    const element = document.getElementById(elementId);

    if (element) {
      element.textContent = "₱0.00";
    }
  });

  setPreviewStatus("");

  const warningsList = document.getElementById("previewWarnings");

  if (warningsList) {
    warningsList.replaceChildren();
  }
}

// =========================
// CREATE PAYROLL
// =========================

async function createPayroll(event) {
  event.preventDefault();

  const employeeId = employeeSelect.value;

  const payCycleId = payCycleSelect.value;

  if (!employeeId || !payCycleId) {
    alert("Please select an employee and pay cycle.");

    return;
  }

  const employee = employees.find(
    (item) => String(item.employeeId) === String(employeeId),
  );

  const cycle = payCycles.find(
    (item) => String(item.payCycleId) === String(payCycleId),
  );

  if (!employee || !cycle) {
    alert("Invalid employee or pay cycle.");

    return;
  }

  try {
    // Every amount is calculated by the server; nothing is sent.
    const response = await fetch(
      `/api/payroll/employee/${employeeId}/pay-cycle/${payCycleId}`,
      {
        method: "POST",
      },
    );

    if (!response.ok) {
      const errorText = await response.text();

      throw new Error(errorText || "Unable to generate payroll.");
    }

    const createdPayroll = await response.json();

    alert(`Payroll #${createdPayroll.payrollId} generated successfully.`);

    closeModal();

    await loadPayrollRecords();
  } catch (error) {
    console.error("Payroll generation error:", error);

    alert(error.message || "Unable to generate payroll.");
  }
}

// =========================
// PROCESS PAYROLL
// =========================

async function processPayroll(payrollId) {
  const payroll = payrollRecords.find((item) => item.payrollId === payrollId);

  if (!payroll) {
    return;
  }

  if (payroll.status !== "DRAFT") {
    alert("Only DRAFT payroll records can be processed.");
    return;
  }

  if (!currentUser || !currentUser.userId) {
    alert("Unable to identify the current user.");
    return;
  }

  const employee = payroll.employee || {};

  const employeeName =
    `${employee.firstName || ""} ${employee.lastName || ""}`.trim() ||
    "Unknown Employee";

  const confirmed = confirm(
    `Process Payroll #${payroll.payrollId}?\n\n` +
      `Employee: ${employeeName}\n` +
      `Net Pay: ${formatCurrency(payroll.netPay)}\n\n` +
      `Once processed, this payroll can no longer be treated as DRAFT.`,
  );

  if (!confirmed) {
    return;
  }

  try {
    const response = await fetch(
      `/api/payroll/${payrollId}/process?userId=${currentUser.userId}`,
      {
        method: "POST",
      },
    );

    if (!response.ok) {
      const errorText = await response.text();

      throw new Error(errorText || "Unable to process payroll.");
    }

    const processedPayroll = await response.json();

    alert(`Payroll #${processedPayroll.payrollId} processed successfully.`);

    await loadPayrollRecords();
  } catch (error) {
    console.error("Payroll processing error:", error);

    alert(error.message || "Unable to process payroll.");
  }
}

// =========================
// PAYMENT MODAL
// =========================

function openPaymentModal(payrollId) {
  const payroll = payrollRecords.find((item) => item.payrollId === payrollId);

  if (!payroll) {
    return;
  }

  if (payroll.status !== "PROCESSED") {
    alert("Only PROCESSED payroll records can be marked as paid.");
    return;
  }

  currentPaymentPayrollId = payrollId;

  const employee = payroll.employee || {};

  const employeeName =
    `${employee.firstName || ""} ${employee.lastName || ""}`.trim() ||
    "Unknown Employee";

  document.getElementById("paymentPayrollId").textContent =
    `#${payroll.payrollId}`;

  document.getElementById("paymentEmployee").textContent = employeeName;

  document.getElementById("paymentNetPay").textContent = formatCurrency(
    payroll.netPay,
  );

  document.getElementById("paymentMethod").value = "";
  document.getElementById("paymentReference").value = "";

  document.getElementById("paymentModal").classList.remove("hidden");
}

// =========================
// MARK PAYROLL AS PAID
// =========================

async function markPayrollAsPaid() {
  if (!currentPaymentPayrollId) {
    return;
  }

  const paymentMethod = document.getElementById("paymentMethod").value;

  const paymentReference = document
    .getElementById("paymentReference")
    .value.trim();

  if (!paymentMethod) {
    alert("Please select a payment method.");
    return;
  }

  const payroll = payrollRecords.find(
    (item) => item.payrollId === currentPaymentPayrollId,
  );

  if (!payroll) {
    alert("Payroll record not found.");
    return;
  }

  const employee = payroll.employee || {};

  const employeeName =
    `${employee.firstName || ""} ${employee.lastName || ""}`.trim() ||
    "Unknown Employee";

  const confirmed = confirm(
    `Mark Payroll #${payroll.payrollId} as PAID?\n\n` +
      `Employee: ${employeeName}\n` +
      `Net Pay: ${formatCurrency(payroll.netPay)}\n` +
      `Payment Method: ${paymentMethod}\n\n` +
      `This action will change the payroll status to PAID.`,
  );

  if (!confirmed) {
    return;
  }

  try {
    const params = new URLSearchParams();

    params.append("paymentMethod", paymentMethod);

    if (paymentReference) {
      params.append("paymentReference", paymentReference);
    }

    const response = await fetch(
      `/api/payroll/${currentPaymentPayrollId}/pay?${params.toString()}`,
      {
        method: "POST",
      },
    );

    if (!response.ok) {
      const errorText = await response.text();

      throw new Error(errorText || "Unable to mark payroll as paid.");
    }

    const paidPayroll = await response.json();

    document.getElementById("paymentModal").classList.add("hidden");

    currentPaymentPayrollId = null;

    alert(`Payroll #${paidPayroll.payrollId} marked as paid successfully.`);

    await loadPayrollRecords();
  } catch (error) {
    console.error("Payroll payment error:", error);

    alert(error.message || "Unable to mark payroll as paid.");
  }
}

// =========================
// VIEW PAYSLIP
// =========================

function viewPayslip(payrollId) {
  const payroll = payrollRecords.find((item) => item.payrollId === payrollId);

  if (!payroll) {
    alert("Payroll record not found.");
    return;
  }

  if (payroll.status !== "PAID") {
    alert("Payslip is only available for PAID payroll records.");
    return;
  }

  window.location.href = `/payslip.html?payrollId=${payrollId}`;
}

// =========================
// VIEW PAYROLL
// =========================

function viewPayroll(payrollId) {
  const payroll = payrollRecords.find((item) => item.payrollId === payrollId);

  if (!payroll) {
    return;
  }

  const employee = payroll.employee || {};

  const payCycle = payroll.payCycle || {};

  const employeeName =
    `${employee.firstName || ""} ${employee.lastName || ""}`.trim() ||
    "Unknown Employee";

  document.getElementById("detailsPayrollId").textContent =
    `Payroll #${payroll.payrollId}`;

  document.getElementById("detailsEmployee").textContent = employeeName;

  document.getElementById("detailsPayCycle").textContent =
    getPayCycleLabel(payCycle);

  document.getElementById("detailsStatus").textContent =
    payroll.status || "DRAFT";

  document.getElementById("detailsRegularPay").textContent = formatCurrency(
    payroll.regularPay,
  );

  document.getElementById("detailsOvertimePay").textContent = formatCurrency(
    payroll.overtimePay,
  );

  document.getElementById("detailsBonus").textContent = formatCurrency(
    payroll.bonusAmount,
  );

  document.getElementById("detailsGrossPay").textContent = formatCurrency(
    payroll.grossPay,
  );

  document.getElementById("detailsTax").textContent = formatCurrency(
    payroll.taxAmount,
  );

  document.getElementById("detailsDeductions").textContent = formatCurrency(
    payroll.deductionAmount,
  );

  document.getElementById("detailsNetPay").textContent = formatCurrency(
    payroll.netPay,
  );

  // Not recorded for payroll calculated before employer shares existed.
  document.getElementById("detailsEmployerContributions").textContent =
    payroll.employerContributionTotal == null
      ? "Not recorded"
      : formatCurrency(payroll.employerContributionTotal);

  deletePayrollButton.dataset.payrollId = payroll.payrollId;

  if (payroll.status === "DRAFT") {
    deletePayrollButton.style.display = "inline-block";
  } else {
    deletePayrollButton.style.display = "none";
  }

  payrollDetailsModal.classList.remove("hidden");
}

//=========================
// DELETE PAYROLL
//=========================

function openDeleteConfirmModal(payrollId) {
  const payroll = payrollRecords.find((item) => item.payrollId === payrollId);

  if (!payroll) {
    return;
  }

  if (payroll.status !== "DRAFT") {
    alert("Only DRAFT payroll records can be deleted.");
    return;
  }

  const employee = payroll.employee || {};

  const employeeName =
    `${employee.firstName || ""} ${employee.lastName || ""}`.trim() ||
    "Unknown Employee";

  deleteConfirmEmployee.textContent = employeeName;

  deleteConfirmPayCycle.textContent = getPayCycleLabel(payroll.payCycle || {});

  deleteConfirmStatus.textContent = payroll.status || "DRAFT";

  payrollToDelete = payrollId;

  deleteConfirmModal.classList.remove("hidden");
}

function closeDeleteConfirmModal() {
  payrollToDelete = null;

  if (deleteConfirmModal) {
    deleteConfirmModal.classList.add("hidden");
  }
}

async function confirmDeletePayroll() {
  if (!payrollToDelete) {
    return;
  }

  const payrollId = payrollToDelete;

  try {
    confirmDeleteButton.disabled = true;
    confirmDeleteButton.textContent = "Deleting...";

    const response = await fetch(`/api/payroll/${payrollId}`, {
      method: "DELETE",
    });

    if (!response.ok) {
      const errorText = await response.text();

      throw new Error(errorText || "Unable to delete payroll.");
    }

    closeDeleteConfirmModal();
    closePayrollDetailsModal();

    await loadPayrollRecords();

    alert(`Payroll #${payrollId} deleted successfully.`);
  } catch (error) {
    console.error("Payroll deletion error:", error);

    alert(error.message || "Unable to delete payroll.");
  } finally {
    confirmDeleteButton.disabled = false;
    confirmDeleteButton.textContent = "Delete Payroll";
  }
}

// =========================
// PAYROLL DETAILS MODAL
// =========================

function closePayrollDetailsModal() {
  if (!payrollDetailsModal) {
    return;
  }

  payrollDetailsModal.classList.add("hidden");
}

// =========================
// LOGOUT
// =========================

async function logout() {
  try {
    await fetch("/api/auth/logout", {
      method: "POST",
    });
  } finally {
    window.location.href = "/index.html";
  }
}

// =========================
// HELPER FUNCTIONS
// =========================

function getPayCycleLabel(cycle) {
  if (!cycle || !cycle.payCycleId) {
    return "Unknown Cycle";
  }

  const cycleName = cycle.cycleName || cycle.cycleType || "Pay Cycle";

  const start = cycle.startDate || "";

  const end = cycle.endDate || "";

  return `#${cycle.payCycleId} - ${cycleName} (${start} to ${end})`;
}

function getStatusBadge(status) {
  const normalized = String(status).toUpperCase();

  let className = "status-draft";

  if (normalized === "PROCESSED") {
    className = "status-processed";
  }

  if (normalized === "PAID") {
    className = "status-paid";
  }

  return `
    <span class="status-badge ${className}">
      ${escapeHtml(normalized)}
    </span>
  `;
}

function formatCurrency(value) {
  const number = toNumber(value);

  return new Intl.NumberFormat("en-PH", {
    style: "currency",
    currency: "PHP",
  }).format(number);
}

function toNumber(value) {
  const number = Number(value);

  return Number.isFinite(number) ? number : 0;
}

function formatRole(role) {
  return String(role)
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function showError(message) {
  if (!payrollTableBody) {
    return;
  }

  payrollTableBody.innerHTML = `
    <tr>
      <td colspan="12" class="empty-cell">
        ${escapeHtml(message)}
      </td>
    </tr>
  `;
}

// =========================
// PAYMENT MODAL BUTTON HANDLING
// =========================

document.addEventListener("click", function (event) {
  // Close button (X)
  if (event.target.closest("#closePaymentModalButton")) {
    document.getElementById("paymentModal").classList.add("hidden");

    currentPaymentPayrollId = null;

    return;
  }

  // Cancel button
  if (event.target.closest("#cancelPaymentButton")) {
    document.getElementById("paymentModal").classList.add("hidden");

    currentPaymentPayrollId = null;

    return;
  }

  // Mark as Paid button
  if (event.target.closest("#confirmPaymentButton")) {
    markPayrollAsPaid();

    return;
  }
});

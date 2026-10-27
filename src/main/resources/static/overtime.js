/* =========================================
   OVERTIME MANAGEMENT
========================================= */

let overtimeRecords = [];
let employees = [];
let currentEditId = null;

/* =========================================
   INITIALIZATION
========================================= */

document.addEventListener("DOMContentLoaded", async () => {
  setupEventListeners();

  await loadEmployees();

  await loadOvertimeRecords();
});

/* =========================================
   EVENT LISTENERS
========================================= */

function setupEventListeners() {
  document
    .getElementById("addOvertimeButton")
    .addEventListener("click", openAddModal);

  document
    .getElementById("closeModalButton")
    .addEventListener("click", closeModal);

  document.getElementById("cancelButton").addEventListener("click", closeModal);

  document
    .getElementById("overtimeForm")
    .addEventListener("submit", saveOvertime);

  document
    .getElementById("employeeSelect")
    .addEventListener("change", async (event) => {
      await loadAttendanceForEmployee(event.target.value);

      updateCalculationPreview();
    });

  document
    .getElementById("overtimeHours")
    .addEventListener("input", updateCalculationPreview);

  document
    .getElementById("employeeFilter")
    .addEventListener("change", applyFilters);

  document
    .getElementById("statusFilter")
    .addEventListener("change", applyFilters);

  document
    .getElementById("clearFiltersButton")
    .addEventListener("click", clearFilters);

  document.getElementById("logoutButton").addEventListener("click", logout);

  document
    .getElementById("overtimeModal")
    .addEventListener("click", (event) => {
      if (event.target.id === "overtimeModal") {
        closeModal();
      }
    });
}

/* =========================================
   LOAD EMPLOYEES
========================================= */

async function loadEmployees() {
  try {
    const response = await fetch("/api/employees");

    if (!response.ok) {
      throw new Error("Unable to load employees.");
    }

    employees = await response.json();

    populateEmployeeSelects();
  } catch (error) {
    console.error(error);

    showMessage("Unable to load employees.", "error");
  }
}

/* =========================================
   EMPLOYEE SELECTS
========================================= */

function populateEmployeeSelects() {
  const employeeSelect = document.getElementById("employeeSelect");

  const employeeFilter = document.getElementById("employeeFilter");

  employeeSelect.innerHTML = `
        <option value="">
            Select employee
        </option>
    `;

  employeeFilter.innerHTML = `
        <option value="">
            All Employees
        </option>
    `;

  employees.forEach((employee) => {
    const employeeId = employee.employeeId;

    const employeeName = `${employee.firstName} ${employee.lastName}`;

    employeeSelect.innerHTML += `
            <option value="${employeeId}">
                ${escapeHtml(employeeName)}
            </option>
        `;

    employeeFilter.innerHTML += `
            <option value="${employeeId}">
                ${escapeHtml(employeeName)}
            </option>
        `;
  });
}

/* =========================================
   LOAD OVERTIME
========================================= */

async function loadOvertimeRecords() {
  try {
    const response = await fetch("/api/overtime");

    if (!response.ok) {
      throw new Error("Unable to load overtime records.");
    }

    overtimeRecords = await response.json();

    renderOvertimeTable();

    updateSummary();
  } catch (error) {
    console.error(error);

    document.getElementById("overtimeTableBody").innerHTML = `
            <tr>
                <td colspan="9"
                    class="empty-state">
                    Unable to load overtime records.
                </td>
            </tr>
        `;
  }
}

/* =========================================
   RENDER TABLE
========================================= */

function renderOvertimeTable(records = overtimeRecords) {
  const tbody = document.getElementById("overtimeTableBody");

  if (!records.length) {
    tbody.innerHTML = `
            <tr>
                <td colspan="9"
                    class="empty-state">
                    No overtime records found.
                </td>
            </tr>
        `;

    return;
  }

  tbody.innerHTML = records
    .map((record) => {
      const status = record.status || "PENDING";

      return `
                <tr>

                    <td>
                        #${record.overtimeId}
                    </td>

                    <td>
                        ${escapeHtml(record.employeeName || "-")}
                    </td>

                    <td>
                        ${record.overtimeDate || "-"}
                    </td>

                    <td>
                        ${formatNumber(record.hours)}
                    </td>

                    <td>
                        ${formatCurrency(record.overtimeRate)}
                    </td>

                    <td>
                        ${formatCurrency(record.overtimeAmount)}
                    </td>

                    <td>
                        <span class="
                            status-badge
                            ${getStatusClass(status)}
                        ">
                            ${status}
                        </span>
                        ${renderStatusNote(record)}
                    </td>

                    <td>
                        ${escapeHtml(record.approvedBy || "-")}
                    </td>

                    <td>
                        ${renderActions(record)}
                    </td>

                </tr>
            `;
    })
    .join("");
}

/* =========================================
   ACTION BUTTONS
========================================= */

function renderActions(record) {
  let buttons = "";

  if (record.status === "PENDING") {
    buttons += `
            <button
                class="action-button action-edit"
                onclick="editOvertime(${record.overtimeId})">
                Edit
            </button>

            <button
                class="action-button action-approve"
                onclick="approveOvertime(${record.overtimeId})">
                Approve
            </button>

            <button
                class="action-button action-reject"
                onclick="rejectOvertime(${record.overtimeId})">
                Reject
            </button>
        `;
  }

  // Overtime is history: approved overtime that won't be worked is
  // cancelled with a reason, never deleted.
  if (record.status === "APPROVED") {
    buttons += `
            <button
                class="action-button action-cancel"
                onclick="cancelOvertime(${record.overtimeId})">
                Cancel
            </button>
        `;
  }

  return `
        <div class="action-buttons">
            ${buttons}
        </div>
    `;
}

/* =========================================
   OPEN ADD MODAL
========================================= */

function openAddModal() {
  currentEditId = null;

  document.getElementById("modalTitle").textContent = "Add Overtime";

  document.getElementById("overtimeForm").reset();

  document.getElementById("overtimeId").value = "";

  document.getElementById("attendanceSelect").innerHTML = `
        <option value="">
            No attendance record
        </option>
    `;

  clearFormMessage();

  document.getElementById("previewRate").textContent = "₱0.00";

  document.getElementById("previewAmount").textContent = "₱0.00";

  document.getElementById("overtimeModal").classList.remove("hidden");
}

/* =========================================
   EDIT OVERTIME
========================================= */

async function editOvertime(id) {
  const record = overtimeRecords.find((item) => item.overtimeId === id);

  if (!record) {
    return;
  }

  if (record.status !== "PENDING") {
    alert("Only pending overtime can be edited.");

    return;
  }

  currentEditId = id;

  document.getElementById("modalTitle").textContent = "Edit Overtime";

  document.getElementById("overtimeId").value = id;

  document.getElementById("employeeSelect").value = record.employeeId;

  await loadAttendanceForEmployee(record.employeeId);

  document.getElementById("attendanceSelect").value = record.attendanceId || "";

  document.getElementById("overtimeDate").value = record.overtimeDate || "";

  document.getElementById("overtimeHours").value = record.hours || "";

  updateCalculationPreview();

  clearFormMessage();

  document.getElementById("overtimeModal").classList.remove("hidden");
}

/* =========================================
   LOAD ATTENDANCE
========================================= */

async function loadAttendanceForEmployee(employeeId) {
  const attendanceSelect = document.getElementById("attendanceSelect");

  attendanceSelect.innerHTML = `
        <option value="">
            No attendance record
        </option>
    `;

  if (!employeeId) {
    return;
  }

  try {
    const response = await fetch(`/api/attendance/employee/${employeeId}`);

    if (!response.ok) {
      throw new Error("Unable to load attendance.");
    }

    const records = await response.json();

    records
      .sort((a, b) => new Date(b.attendanceDate) - new Date(a.attendanceDate))
      .forEach((record) => {
        attendanceSelect.innerHTML += `
                    <option
                        value="${record.attendanceId}">
                        ${record.attendanceDate}
                        -
                        ${record.status}
                        -
                        ${formatNumber(record.regularHours)}h
                    </option>
                `;
      });
  } catch (error) {
    console.error(error);

    attendanceSelect.innerHTML = `
            <option value="">
                Unable to load attendance
            </option>
        `;
  }
}

/* =========================================
   CALCULATION PREVIEW
========================================= */

function updateCalculationPreview() {
  const employeeId = document.getElementById("employeeSelect").value;

  const hours = parseFloat(document.getElementById("overtimeHours").value);

  const record = overtimeRecords.find((item) => item.employeeId == employeeId);

  if (!employeeId || !hours || !record) {
    document.getElementById("previewRate").textContent = "Calculated on save";

    document.getElementById("previewAmount").textContent = "Calculated on save";

    return;
  }

  const rate = parseFloat(record.overtimeRate) || 0;

  const amount = rate * hours;

  document.getElementById("previewRate").textContent = formatCurrency(rate);

  document.getElementById("previewAmount").textContent = formatCurrency(amount);
}

/* =========================================
   SAVE OVERTIME
========================================= */

async function saveOvertime(event) {
  event.preventDefault();

  const employeeId = document.getElementById("employeeSelect").value;

  const attendanceId = document.getElementById("attendanceSelect").value;

  const overtimeDate = document.getElementById("overtimeDate").value;

  const hours = parseFloat(document.getElementById("overtimeHours").value);

  if (!employeeId) {
    showFormMessage("Please select an employee.", "error");

    return;
  }

  if (!overtimeDate) {
    showFormMessage("Please select the overtime date.", "error");

    return;
  }

  if (!hours || hours <= 0) {
    showFormMessage("Overtime hours must be greater than zero.", "error");

    return;
  }

  if (hours > 24) {
    showFormMessage("Overtime hours cannot exceed 24 hours.", "error");

    return;
  }

  const requestBody = {
    attendance: attendanceId
      ? {
          attendanceId: Number(attendanceId),
        }
      : null,

    overtimeDate: overtimeDate,

    hours: hours,
  };

  try {
    let response;

    if (currentEditId) {
      response = await fetch(
        `/api/overtime/${currentEditId}/employee/${employeeId}`,
        {
          method: "PUT",

          headers: {
            "Content-Type": "application/json",
          },

          body: JSON.stringify(requestBody),
        },
      );
    } else {
      response = await fetch(`/api/overtime/employee/${employeeId}`, {
        method: "POST",

        headers: {
          "Content-Type": "application/json",
        },

        body: JSON.stringify(requestBody),
      });
    }

    if (!response.ok) {
      const errorText = (await response.text()).trim();

      throw new Error(errorText || "Unable to save overtime.");
    }

    closeModal();

    await loadOvertimeRecords();

    showMessage(
      currentEditId
        ? "Overtime updated successfully."
        : "Overtime added successfully.",
      "success",
    );
  } catch (error) {
    console.error(error);

    showFormMessage(error.message, "error");
  }
}

/* =========================================
   APPROVE OVERTIME
========================================= */

async function approveOvertime(id) {
  const confirmed = confirm("Approve this overtime record?");

  if (!confirmed) {
    return;
  }

  await updateOvertimeStatus(id, "APPROVED");
}

/* =========================================
   REJECT OVERTIME
========================================= */

async function rejectOvertime(id) {
  const reason = await askForReason({
    title: "Reject overtime",
    message: "The employee will see this reason on My Payroll.",
    confirmLabel: "Reject",
  });

  if (reason === null) {
    return;
  }

  await updateOvertimeStatus(id, "REJECTED", reason);
}

/* =========================================
   CANCEL APPROVED OVERTIME
========================================= */

async function cancelOvertime(id) {
  const reason = await askForReason({
    title: "Cancel approved overtime",
    message:
      "The overtime stays on record as Cancelled, with this reason, and is no longer paid. " +
      "Overtime already in processed or paid payroll can't be cancelled.",
    confirmLabel: "Cancel overtime",
  });

  if (reason === null) {
    return;
  }

  try {
    const response = await fetch(`/api/overtime/${id}/cancel`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ reason }),
    });

    if (!response.ok) {
      const errorText = (await response.text()).trim();

      throw new Error(errorText || "Unable to cancel overtime.");
    }

    await loadOvertimeRecords();

    showMessage("Overtime cancelled.", "success");
  } catch (error) {
    console.error(error);

    showMessage(error.message, "error");
  }
}

/* =========================================
   STATUS NOTE (reason for a rejection or cancellation)
========================================= */

function renderStatusNote(record) {
  if (record.status === "REJECTED" && record.decisionRemarks) {
    return `<small class="status-note">Reason: ${escapeHtml(record.decisionRemarks)}</small>`;
  }

  if (record.status === "CANCELLED") {
    return `<small class="status-note">By ${escapeHtml(record.cancelledBy || "-")}: ${escapeHtml(record.cancelReason || "")}</small>`;
  }

  return "";
}

/* =========================================
   UPDATE STATUS
========================================= */

async function updateOvertimeStatus(id, status, reason) {
  try {
    const response = await fetch(
      `/api/overtime/${id}/status?status=${status}`,
      {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(reason ? { reason } : {}),
      },
    );

    if (!response.ok) {
      const errorText = (await response.text()).trim();

      throw new Error(errorText || `Unable to ${status.toLowerCase()} overtime.`);
    }

    await loadOvertimeRecords();

    showMessage(`Overtime ${status.toLowerCase()} successfully.`, "success");
  } catch (error) {
    console.error(error);

    showMessage(error.message, "error");
  }
}

/* =========================================
   FILTERS
========================================= */

function applyFilters() {
  const employeeId = document.getElementById("employeeFilter").value;

  const status = document.getElementById("statusFilter").value;

  const filtered = overtimeRecords.filter((record) => {
    const employeeMatch =
      !employeeId || String(record.employeeId) === String(employeeId);

    const statusMatch = !status || record.status === status;

    return employeeMatch && statusMatch;
  });

  renderOvertimeTable(filtered);
}

/* =========================================
   CLEAR FILTERS
========================================= */

function clearFilters() {
  document.getElementById("employeeFilter").value = "";

  document.getElementById("statusFilter").value = "";

  renderOvertimeTable();
}

/* =========================================
   SUMMARY
========================================= */

function updateSummary() {
  const total = overtimeRecords.length;

  const pending = overtimeRecords.filter(
    (record) => record.status === "PENDING",
  ).length;

  const approved = overtimeRecords.filter(
    (record) => record.status === "APPROVED",
  ).length;

  const rejected = overtimeRecords.filter(
    (record) => record.status === "REJECTED",
  ).length;

  const totalHours = overtimeRecords.reduce(
    (sum, record) => sum + (parseFloat(record.hours) || 0),
    0,
  );

  const approvedAmount = overtimeRecords
    .filter((record) => record.status === "APPROVED")
    .reduce((sum, record) => sum + (parseFloat(record.overtimeAmount) || 0), 0);

  document.getElementById("totalRecords").textContent = total;

  document.getElementById("pendingRecords").textContent = pending;

  document.getElementById("approvedRecords").textContent = approved;

  document.getElementById("rejectedCount").textContent = rejected;

  document.getElementById("totalHours").textContent = formatNumber(totalHours);

  document.getElementById("approvedAmount").textContent =
    formatCurrency(approvedAmount);
}

/* =========================================
   MODAL
========================================= */

function closeModal() {
  document.getElementById("overtimeModal").classList.add("hidden");

  currentEditId = null;
}

/* =========================================
   MESSAGES
========================================= */

function showFormMessage(message, type) {
  const element = document.getElementById("formMessage");

  element.textContent = message;

  element.className = `form-message ${type}`;
}

function clearFormMessage() {
  const element = document.getElementById("formMessage");

  element.textContent = "";

  element.className = "form-message";
}

function showMessage(message, type) {
  console.log(`${type.toUpperCase()}: ${message}`);
}

/* =========================================
   LOGOUT
========================================= */

async function logout() {
  try {
    await fetch("/api/auth/logout", {
      method: "POST",
    });
  } finally {
    window.location.href = "/index.html";
  }
}

/* =========================================
   FORMATTERS
========================================= */

function formatCurrency(value) {
  const number = parseFloat(value) || 0;

  return new Intl.NumberFormat("en-PH", {
    style: "currency",
    currency: "PHP",
  }).format(number);
}

function formatNumber(value) {
  const number = parseFloat(value) || 0;

  return number.toFixed(2);
}

function getStatusClass(status) {
  switch (status) {
    case "APPROVED":
      return "status-approved";

    case "REJECTED":
      return "status-rejected";

    case "CANCELLED":
      return "status-cancelled";

    default:
      return "status-pending";
  }
}

/* =========================================
   HTML ESCAPING
========================================= */

function escapeHtml(value) {
  if (value === null || value === undefined) {
    return "";
  }

  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

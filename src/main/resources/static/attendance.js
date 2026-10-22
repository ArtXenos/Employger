let attendanceRecords = [];
let employees = [];

const tableBody = document.getElementById("attendanceTableBody");

const modal = document.getElementById("attendanceModal");

const form = document.getElementById("attendanceForm");

const employeeSelect = document.getElementById("employeeId");

const formMessage = document.getElementById("formMessage");

/* ================================
   Load Page
================================ */

document.addEventListener("DOMContentLoaded", async () => {
  await loadEmployees();

  await loadAttendance();
});

/* ================================
   Load Employees
================================ */

async function loadEmployees() {
  try {
    const response = await fetch("/api/employees");

    if (response.status === 401) {
      window.location.href = "/index.html";
      return;
    }

    if (!response.ok) {
      throw new Error("Failed to load employees.");
    }

    employees = await response.json();

    employeeSelect.innerHTML = '<option value="">Select employee</option>';

    employees.forEach((employee) => {
      const option = document.createElement("option");

      option.value = employee.employeeId;

      option.textContent = `${employee.firstName} ${employee.lastName}`;

      employeeSelect.appendChild(option);
    });
  } catch (error) {
    console.error(error);

    showMessage("Unable to load employee records.", "error");
  }
}

/* ================================
   Load Attendance
================================ */

async function loadAttendance() {
  try {
    const response = await fetch("/api/attendance");

    if (response.status === 401) {
      window.location.href = "/index.html";
      return;
    }

    if (!response.ok) {
      throw new Error("Failed to load attendance records.");
    }

    attendanceRecords = await response.json();

    renderAttendance();
  } catch (error) {
    console.error(error);

    tableBody.innerHTML = `
            <tr>
                <td colspan="9" class="empty-row">
                    Unable to load attendance records.
                </td>
            </tr>
        `;
  }
}

/* ================================
   Display Attendance
================================ */

function renderAttendance() {
  tableBody.innerHTML = "";

  if (attendanceRecords.length === 0) {
    tableBody.innerHTML = `
            <tr>
                <td colspan="9" class="empty-row">
                    No attendance records found.
                </td>
            </tr>
        `;
  } else {
    attendanceRecords.forEach((record) => {
      const employee = record.employee;

      const employeeName = employee
        ? escapeHtml(`${employee.firstName} ${employee.lastName}`)
        : "Unknown Employee";

      const row = document.createElement("tr");

      row.innerHTML = `

                <td>${record.attendanceId}</td>

                <td>
                    <strong>${employeeName}</strong>
                </td>

                <td>${record.attendanceDate ?? "-"}</td>

                <td>${record.timeIn ?? "-"}</td>

                <td>${record.timeOut ?? "-"}</td>

                <td>${record.regularHours ?? "0.00"}</td>

                <td>
                    <span class="status ${getStatusClass(record.status)}">
                        ${record.status}
                    </span>
                </td>

                <td>${escapeHtml(record.remarks ?? "-")}</td>

                <td>

                    <button
                        class="action-button edit-button"
                        onclick="editAttendance(${record.attendanceId})">
                        Edit
                    </button>

                    <button
                        class="action-button delete-button"
                        onclick="deleteAttendance(${record.attendanceId})">
                        Delete
                    </button>

                </td>
            `;

      tableBody.appendChild(row);
    });
  }

  updateSummary();
}

/* ================================
   Summary
================================ */

function updateSummary() {
  document.getElementById("totalRecords").textContent =
    attendanceRecords.length;

  document.getElementById("presentRecords").textContent =
    attendanceRecords.filter((record) => record.status === "PRESENT").length;

  document.getElementById("lateRecords").textContent = attendanceRecords.filter(
    (record) => record.status === "LATE",
  ).length;

  document.getElementById("absentRecords").textContent =
    attendanceRecords.filter((record) => record.status === "ABSENT").length;
}

/* ================================
   Status Styling
================================ */

function getStatusClass(status) {
  switch (status) {
    case "PRESENT":
      return "status-present";

    case "LATE":
      return "status-late";

    case "ABSENT":
      return "status-absent";

    case "LEAVE":
      return "status-leave";

    default:
      return "";
  }
}

/* ================================
   Open Add Modal
================================ */

document.getElementById("addAttendanceButton").addEventListener("click", () => {
  form.reset();

  document.getElementById("attendanceId").value = "";

  document.getElementById("modalTitle").textContent = "Add Attendance";

  formMessage.textContent = "";

  formMessage.className = "form-message";

  modal.classList.add("show");
});

/* ================================
   Close Modal
================================ */

function closeModal() {
  modal.classList.remove("show");
}

document
  .getElementById("closeModalButton")
  .addEventListener("click", closeModal);

document.getElementById("cancelButton").addEventListener("click", closeModal);

/* ================================
   Edit Attendance
================================ */

function editAttendance(attendanceId) {
  const record = attendanceRecords.find(
    (item) => item.attendanceId === attendanceId,
  );

  if (!record) {
    return;
  }

  document.getElementById("attendanceId").value = record.attendanceId;

  document.getElementById("employeeId").value =
    record.employee?.employeeId ?? "";

  document.getElementById("attendanceDate").value = record.attendanceDate ?? "";

  document.getElementById("timeIn").value = record.timeIn ?? "";

  document.getElementById("timeOut").value = record.timeOut ?? "";

  document.getElementById("regularHours").value = record.regularHours ?? 0;

  document.getElementById("status").value = record.status ?? "PRESENT";

  document.getElementById("remarks").value = record.remarks ?? "";

  document.getElementById("modalTitle").textContent = "Edit Attendance";

  formMessage.textContent = "";

  formMessage.className = "form-message";

  modal.classList.add("show");
}

/* ================================
   Save Attendance
================================ */

form.addEventListener("submit", async (event) => {
  event.preventDefault();

  const attendanceId = document.getElementById("attendanceId").value;

  const employeeId = document.getElementById("employeeId").value;

  const attendanceDate = document.getElementById("attendanceDate").value;

  const timeIn = document.getElementById("timeIn").value || null;

  const timeOut = document.getElementById("timeOut").value || null;

  const regularHours =
    parseFloat(document.getElementById("regularHours").value) || 0;

  const status = document.getElementById("status").value;

  const remarks = document.getElementById("remarks").value.trim();

  if (!employeeId || !attendanceDate) {
    showMessage("Please complete the required fields.", "error");

    return;
  }

  const data = {
    attendanceDate: attendanceDate,

    timeIn: timeIn,

    timeOut: timeOut,

    regularHours: regularHours,

    status: status,

    remarks: remarks,
  };

  try {
    let response;

    if (attendanceId) {
      response = await fetch(
        `/api/attendance/${attendanceId}/employee/${employeeId}`,
        {
          method: "PUT",

          headers: {
            "Content-Type": "application/json",
          },

          body: JSON.stringify(data),
        },
      );
    } else {
      response = await fetch(`/api/attendance/employee/${employeeId}`, {
        method: "POST",

        headers: {
          "Content-Type": "application/json",
        },

        body: JSON.stringify(data),
      });
    }

    if (response.status === 401) {
      window.location.href = "/index.html";

      return;
    }

    if (!response.ok) {
      const errorText = await response.text();

      throw new Error(errorText || "Unable to save attendance.");
    }

    closeModal();

    await loadAttendance();
  } catch (error) {
    console.error(error);

    showMessage(error.message, "error");
  }
});

/* ================================
   Delete Attendance
================================ */

async function deleteAttendance(attendanceId) {
  const confirmed = confirm(
    "Are you sure you want to delete this attendance record?",
  );

  if (!confirmed) {
    return;
  }

  try {
    const response = await fetch(`/api/attendance/${attendanceId}`, {
      method: "DELETE",
    });

    if (response.status === 401) {
      window.location.href = "/index.html";

      return;
    }

    if (!response.ok) {
      const errorText = await response.text();

      throw new Error(errorText || "Unable to delete attendance.");
    }

    await loadAttendance();
  } catch (error) {
    console.error(error);

    alert(error.message);
  }
}

/* ================================
   Form Message
================================ */

function showMessage(message, type) {
  formMessage.textContent = message;

  formMessage.className = `form-message ${type}`;
}

/* ================================
   Helpers
================================ */

// Escape text before inserting it into HTML so stored values such as
// names and remarks cannot inject markup or scripts into the page.
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

/* ================================
   Logout
================================ */

document.getElementById("logoutButton").addEventListener("click", async () => {
  try {
    await fetch("/api/auth/logout", {
      method: "POST",
    });
  } finally {
    window.location.href = "/index.html";
  }
});

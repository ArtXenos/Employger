const employeeTableBody = document.getElementById("employeeTableBody");

const totalEmployees = document.getElementById("totalEmployees");

const activeEmployees = document.getElementById("activeEmployees");

const inactiveEmployees = document.getElementById("inactiveEmployees");

const employeeModal = document.getElementById("employeeModal");

const employeeForm = document.getElementById("employeeForm");

const modalTitle = document.getElementById("modalTitle");

const formMessage = document.getElementById("formMessage");

const searchEmployee = document.getElementById("searchEmployee");

let employees = [];

let editingEmployeeId = null;

/* =========================
   LOAD EMPLOYEES
========================= */

async function loadEmployees() {
  try {
    const response = await fetch("/api/employees");

    if (response.status === 401) {
      window.location.href = "/";
      return;
    }

    if (!response.ok) {
      throw new Error("Failed to load employees.");
    }

    employees = await response.json();

    displayEmployees(employees);

    updateEmployeeSummary();
  } catch (error) {
    employeeTableBody.innerHTML = `
            <tr>
                <td colspan="7" class="loading">
                    ${error.message}
                </td>
            </tr>
        `;
  }
}

/* =========================
   DISPLAY EMPLOYEES
========================= */

function displayEmployees(employeeList) {
  if (employeeList.length === 0) {
    employeeTableBody.innerHTML = `
            <tr>
                <td colspan="7" class="loading">
                    No employee records found.
                </td>
            </tr>
        `;

    return;
  }

  employeeTableBody.innerHTML = employeeList
    .map((employee) => {
      const fullName = escapeHtml(`${employee.firstName} ${employee.lastName}`);

      const salary = Number(employee.baseSalary || 0).toLocaleString("en-PH", {
        style: "currency",
        currency: "PHP",
      });

      const statusClass =
        employee.employmentStatus === "ACTIVE" ? "active" : "inactive";

      return `
                <tr>

                    <td>
                        ${employee.employeeId}
                    </td>

                    <td>
                        <strong>${fullName}</strong>
                        <br>
                        <small>${escapeHtml(employee.email)}</small>
                    </td>

                    <td>
                        ${escapeHtml(employee.department || "-")}
                    </td>

                    <td>
                        ${escapeHtml(employee.jobTitle || "-")}
                    </td>

                    <td>
                        ${salary}
                    </td>

                    <td>
                        <span class="status ${statusClass}">
                            ${employee.employmentStatus}
                        </span>
                    </td>

                    <td>

                        <button
                            class="action-button edit-button"
                            onclick="editEmployee(${employee.employeeId})"
                        >
                            Edit
                        </button>

                        <button
                            class="action-button delete-button"
                            onclick="deleteEmployee(${employee.employeeId})"
                        >
                            Delete
                        </button>

                    </td>

                </tr>
            `;
    })
    .join("");
}

/* =========================
   SUMMARY
========================= */

function updateEmployeeSummary() {
  const active = employees.filter(
    (employee) => employee.employmentStatus === "ACTIVE",
  ).length;

  const inactive = employees.filter(
    (employee) => employee.employmentStatus === "INACTIVE",
  ).length;

  totalEmployees.textContent = employees.length;

  activeEmployees.textContent = active;

  inactiveEmployees.textContent = inactive;
}

/* =========================
   OPEN ADD MODAL
========================= */

document.getElementById("addEmployeeButton").addEventListener("click", () => {
  editingEmployeeId = null;

  modalTitle.textContent = "Add Employee";

  employeeForm.reset();

  document.getElementById("employmentStatus").value = "ACTIVE";

  formMessage.textContent = "";

  employeeModal.classList.add("show");
});

/* =========================
   EDIT EMPLOYEE
========================= */

window.editEmployee = function (employeeId) {
  const employee = employees.find((item) => item.employeeId === employeeId);

  if (!employee) {
    return;
  }

  editingEmployeeId = employeeId;

  modalTitle.textContent = "Edit Employee";

  document.getElementById("employeeId").value = employee.employeeId;

  document.getElementById("firstName").value = employee.firstName || "";

  document.getElementById("lastName").value = employee.lastName || "";

  document.getElementById("dateOfBirth").value = employee.dateOfBirth || "";

  document.getElementById("email").value = employee.email || "";

  document.getElementById("phone").value = employee.phone || "";

  document.getElementById("gender").value = employee.gender || "";

  document.getElementById("address").value = employee.address || "";

  document.getElementById("department").value = employee.department || "";

  document.getElementById("designation").value = employee.designation || "";

  document.getElementById("jobTitle").value = employee.jobTitle || "";

  document.getElementById("baseSalary").value = employee.baseSalary || "";

  document.getElementById("postalCode").value = employee.postalCode || "";

  document.getElementById("dateHired").value = employee.dateHired || "";

  document.getElementById("employmentStatus").value =
    employee.employmentStatus || "ACTIVE";

  formMessage.textContent = "";

  employeeModal.classList.add("show");
};

/* =========================
   SAVE EMPLOYEE
========================= */

employeeForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  formMessage.textContent = "";

  const employeeData = {
    firstName: document.getElementById("firstName").value.trim(),

    lastName: document.getElementById("lastName").value.trim(),

    dateOfBirth: document.getElementById("dateOfBirth").value,

    email: document.getElementById("email").value.trim(),

    phone: document.getElementById("phone").value.trim(),

    address: document.getElementById("address").value.trim(),

    department: document.getElementById("department").value.trim(),

    gender: document.getElementById("gender").value,

    baseSalary: Number(document.getElementById("baseSalary").value),

    postalCode: document.getElementById("postalCode").value.trim(),

    designation: document.getElementById("designation").value.trim(),

    employmentStatus: document.getElementById("employmentStatus").value,

    dateHired: document.getElementById("dateHired").value,

    jobTitle: document.getElementById("jobTitle").value.trim(),
  };

  try {
    let response;

    if (editingEmployeeId === null) {
      response = await fetch("/api/employees", {
        method: "POST",

        headers: {
          "Content-Type": "application/json",
        },

        body: JSON.stringify(employeeData),
      });
    } else {
      response = await fetch(`/api/employees/${editingEmployeeId}`, {
        method: "PUT",

        headers: {
          "Content-Type": "application/json",
        },

        body: JSON.stringify(employeeData),
      });
    }

    if (response.status === 401) {
      window.location.href = "/";
      return;
    }

    if (!response.ok) {
      const errorText = await response.text();

      throw new Error(errorText || "Failed to save employee.");
    }

    closeModal();

    await loadEmployees();
  } catch (error) {
    formMessage.textContent = error.message;
  }
});

/* =========================
   DELETE EMPLOYEE
========================= */

window.deleteEmployee = async function (employeeId) {
  const employee = employees.find((item) => item.employeeId === employeeId);

  if (!employee) {
    return;
  }

  const confirmed = confirm(
    `Are you sure you want to delete ${employee.firstName} ${employee.lastName}?`,
  );

  if (!confirmed) {
    return;
  }

  try {
    const response = await fetch(`/api/employees/${employeeId}`, {
      method: "DELETE",
    });

    if (response.status === 401) {
      window.location.href = "/";
      return;
    }

    if (!response.ok) {
      const errorText = await response.text();

      throw new Error(errorText || "Failed to delete employee.");
    }

    await loadEmployees();
  } catch (error) {
    alert(error.message);
  }
};

/* =========================
   SEARCH
========================= */

searchEmployee.addEventListener("input", () => {
  const search = searchEmployee.value.toLowerCase().trim();

  const filtered = employees.filter((employee) => {
    const name = `${employee.firstName} ${employee.lastName}`.toLowerCase();

    const email = (employee.email || "").toLowerCase();

    const department = (employee.department || "").toLowerCase();

    return (
      name.includes(search) ||
      email.includes(search) ||
      department.includes(search)
    );
  });

  displayEmployees(filtered);
});

/* =========================
   MODAL CONTROLS
========================= */

function closeModal() {
  employeeModal.classList.remove("show");

  employeeForm.reset();

  editingEmployeeId = null;

  formMessage.textContent = "";
}

document.getElementById("closeModal").addEventListener("click", closeModal);

document.getElementById("cancelButton").addEventListener("click", closeModal);

employeeModal.addEventListener("click", (event) => {
  if (event.target === employeeModal) {
    closeModal();
  }
});

/* =========================
   LOGOUT
========================= */

document.getElementById("logoutButton").addEventListener("click", async () => {
  await fetch("/api/auth/logout", {
    method: "POST",
  });

  window.location.href = "/";
});

/* =========================
   HELPERS
========================= */

// Escape text before inserting it into HTML so stored values such as
// names cannot inject markup or scripts into the page.
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

/* =========================
   INITIAL LOAD
========================= */

loadEmployees();

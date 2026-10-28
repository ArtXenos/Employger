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

// The salary when the edit form opened; a different value asks for the
// date the new salary takes effect.
let originalSalary = null;

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

  prepareChoiceLists(null);

  originalSalary = null;
  updateSalaryChangeFields();
  document.getElementById("salaryHistory").classList.add("hidden");

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

  prepareChoiceLists(employee);

  document.getElementById("designation").value = employee.designation || "";

  document.getElementById("baseSalary").value = employee.baseSalary || "";

  document.getElementById("minimumWageEarner").checked =
    employee.minimumWageEarner === true;

  document.getElementById("postalCode").value = employee.postalCode || "";

  document.getElementById("dateHired").value = employee.dateHired || "";

  document.getElementById("separationDate").value = employee.separationDate || "";

  document.getElementById("employmentStatus").value =
    employee.employmentStatus || "ACTIVE";

  document.getElementById("managerial").checked = employee.managerial === true;

  ["sssNumber", "philHealthNumber", "pagIbigNumber", "tin"].forEach((field) => {
    document.getElementById(field).value = employee[field] || "";
  });

  originalSalary = employee.baseSalary == null ? null : Number(employee.baseSalary);
  document.getElementById("salaryEffectiveDate").value = todayIso();
  document.getElementById("salaryChangeReason").value = "";
  updateSalaryChangeFields();
  loadSalaryHistory(employeeId);

  formMessage.textContent = "";

  employeeModal.classList.add("show");
};

/* =========================
   SAVE EMPLOYEE
========================= */

employeeForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  formMessage.textContent = "";

  if (addRowOpen()) {
    formMessage.textContent = "Click Add to save the new entry first, or Cancel.";
    return;
  }

  const employeeData = {
    firstName: document.getElementById("firstName").value.trim(),

    lastName: document.getElementById("lastName").value.trim(),

    dateOfBirth: document.getElementById("dateOfBirth").value,

    email: document.getElementById("email").value.trim(),

    phone: document.getElementById("phone").value.trim(),

    address: document.getElementById("address").value.trim(),

    departmentId: selectedId("department"),

    gender: document.getElementById("gender").value,

    baseSalary: Number(document.getElementById("baseSalary").value),

    minimumWageEarner: document.getElementById("minimumWageEarner").checked,

    postalCode: document.getElementById("postalCode").value.trim(),

    designation: document.getElementById("designation").value.trim(),

    employmentStatus: document.getElementById("employmentStatus").value,

    dateHired: document.getElementById("dateHired").value,

    // Empty = still employed.
    separationDate: document.getElementById("separationDate").value || null,

    jobTitleId: selectedId("jobTitle"),

    managerial: document.getElementById("managerial").checked,

    // Blank clears the number on the server.
    sssNumber: document.getElementById("sssNumber").value.trim(),

    philHealthNumber: document.getElementById("philHealthNumber").value.trim(),

    pagIbigNumber: document.getElementById("pagIbigNumber").value.trim(),

    tin: document.getElementById("tin").value.trim(),
  };

  if (salaryChanged()) {
    employeeData.salaryEffectiveDate = document.getElementById("salaryEffectiveDate").value || null;
    employeeData.salaryChangeReason = document.getElementById("salaryChangeReason").value.trim();
  }

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

    const jobTitle = (employee.jobTitle || "").toLowerCase();

    return (
      name.includes(search) ||
      email.includes(search) ||
      department.includes(search) ||
      jobTitle.includes(search)
    );
  });

  displayEmployees(filtered);
});

/* =========================
   DEPARTMENT AND JOB TITLE LISTS
========================= */

// Every department, each with its job titles, retired ones included
// (GET /api/departments).
let departments = [];

// The last item of each list: opens the row to add an entry.
const NEW_OPTION = "new";

// The two lists on the form, and how each adds an entry.
const CHOICE_LISTS = {
  department: {
    addRow: "departmentAdd",
    input: "newDepartmentName",
    url: () => "/api/departments",
    newId: (saved) => saved.departmentId,
  },
  jobTitle: {
    addRow: "jobTitleAdd",
    input: "newJobTitleName",
    url: () => `/api/departments/${selectedId("department")}/job-titles`,
    newId: (saved) => saved.jobTitleId,
  },
};

async function loadDepartments() {
  const response = await fetch("/api/departments");

  if (response.status === 401) {
    window.location.href = "/";
    return;
  }

  if (!response.ok) {
    throw new Error("Failed to load the departments.");
  }

  departments = await response.json();
}

// Administrators and HR managers may add entries (the server checks too).
function canEditLists() {
  const role = document.documentElement.dataset.role;
  return role === "ADMINISTRATOR" || role === "HR_MANAGER";
}

function findDepartment(departmentId) {
  return departments.find((department) => department.departmentId === departmentId);
}

// The chosen entry's id, or null for none and for "+ Add new...".
function selectedId(field) {
  const value = document.getElementById(field).value;
  return value === "" || value === NEW_OPTION ? null : Number(value);
}

function option(value, label) {
  return `<option value="${escapeHtml(value)}">${escapeHtml(label)}</option>`;
}

// Picks the entry, or "none" if it isn't in the list.
function choose(select, id) {
  select.value = id == null ? "" : String(id);
  if (select.selectedIndex === -1) {
    select.value = "";
  }
  select.dataset.previous = select.value;
}

// Active departments, plus the employee's own one if it was retired.
function renderDepartmentOptions(currentId) {
  const select = document.getElementById("department");

  const choices = departments
    .filter((department) => department.active || department.departmentId === currentId)
    .map((department) => option(department.departmentId,
      department.name + (department.active ? "" : " (retired)")));

  select.innerHTML = [
    option("", "No department"),
    ...choices,
    ...(canEditLists() ? [option(NEW_OPTION, "+ Add new department...")] : []),
  ].join("");

  choose(select, currentId);
}

// The chosen department's job titles; none until a department is chosen.
function renderJobTitleOptions(currentId) {
  const select = document.getElementById("jobTitle");
  const department = findDepartment(selectedId("department"));

  if (!department) {
    select.innerHTML = option("", "Choose a department first");
    select.disabled = true;
    choose(select, null);
    return;
  }

  const selectable = (title) => title.active && department.active;

  const choices = department.jobTitles
    .filter((title) => selectable(title) || title.jobTitleId === currentId)
    .map((title) => option(title.jobTitleId, title.name + (selectable(title) ? "" : " (retired)")));

  select.innerHTML = [
    option("", "No job title"),
    ...choices,
    ...(canEditLists() && department.active ? [option(NEW_OPTION, "+ Add new job title...")] : []),
  ].join("");

  select.disabled = false;
  choose(select, currentId);
}

function closeAddRows() {
  Object.values(CHOICE_LISTS).forEach((list) => {
    document.getElementById(list.addRow).classList.add("hidden");
  });
}

function addRowOpen() {
  return Object.values(CHOICE_LISTS).some(
    (list) => !document.getElementById(list.addRow).classList.contains("hidden"));
}

function openAddRow(field) {
  const list = CHOICE_LISTS[field];
  const input = document.getElementById(list.input);

  closeAddRows();
  document.getElementById(list.addRow).classList.remove("hidden");
  input.value = "";
  input.focus();
}

function cancelAddRow(field) {
  const select = document.getElementById(field);

  document.getElementById(CHOICE_LISTS[field].addRow).classList.add("hidden");
  select.value = select.dataset.previous || "";
  select.focus();
}

// Saves the new entry, then picks it on the form.
async function saveAddRow(field) {
  const list = CHOICE_LISTS[field];
  const input = document.getElementById(list.input);
  const name = input.value.trim();

  formMessage.textContent = "";

  if (name === "") {
    formMessage.textContent = "Enter a name first.";
    input.focus();
    return;
  }

  try {
    const saved = await sendJson(list.url(), "POST", { name });

    await loadDepartments();
    document.getElementById(list.addRow).classList.add("hidden");

    if (field === "department") {
      renderDepartmentOptions(list.newId(saved));
      renderJobTitleOptions(null);
    } else {
      renderJobTitleOptions(list.newId(saved));
    }

    document.getElementById(field).focus();
  } catch (error) {
    formMessage.textContent = error.message;
  }
}

document.getElementById("department").addEventListener("change", (event) => {
  if (event.target.value === NEW_OPTION) {
    openAddRow("department");
    return;
  }

  closeAddRows();
  event.target.dataset.previous = event.target.value;
  // Job titles depend on the department.
  renderJobTitleOptions(null);
});

document.getElementById("jobTitle").addEventListener("change", (event) => {
  if (event.target.value === NEW_OPTION) {
    openAddRow("jobTitle");
    return;
  }

  document.getElementById("jobTitleAdd").classList.add("hidden");
  event.target.dataset.previous = event.target.value;
});

Object.entries({
  department: ["saveNewDepartment", "cancelNewDepartment"],
  jobTitle: ["saveNewJobTitle", "cancelNewJobTitle"],
}).forEach(([field, [saveId, cancelId]]) => {
  document.getElementById(saveId).addEventListener("click", () => saveAddRow(field));
  document.getElementById(cancelId).addEventListener("click", () => cancelAddRow(field));

  // Enter adds the entry instead of saving the whole employee.
  document.getElementById(CHOICE_LISTS[field].input).addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      saveAddRow(field);
    } else if (event.key === "Escape") {
      event.preventDefault();
      cancelAddRow(field);
    }
  });
});

// Opening the form: fresh lists, with this employee's entries chosen.
async function prepareChoiceLists(employee) {
  closeAddRows();
  renderDepartmentOptions(employee ? employee.departmentId : null);
  renderJobTitleOptions(employee ? employee.jobTitleId : null);

  try {
    await loadDepartments();
  } catch (error) {
    formMessage.textContent = error.message;
    return;
  }

  renderDepartmentOptions(employee ? employee.departmentId : null);
  renderJobTitleOptions(employee ? employee.jobTitleId : null);
}

/* =========================
   MANAGE THE LISTS
========================= */

const listsModal = document.getElementById("listsModal");

const listsMessage = document.getElementById("listsMessage");

function employeeCount(count) {
  const n = count || 0;
  return n === 1 ? "1 employee" : `${n} employees`;
}

function listActions(kind, entry) {
  const id = kind === "department" ? entry.departmentId : entry.jobTitleId;
  const button = (action, label, style) =>
    `<button type="button" class="action-button ${style}" data-action="${action}" data-kind="${kind}" data-id="${id}">${label}</button>`;

  const buttons = [
    button("rename", "Rename", "edit-button"),
    entry.active ? button("retire", "Retire", "delete-button") : button("restore", "Restore", "edit-button"),
  ];

  if (kind === "department" && entry.active) {
    buttons.push(button("add-title", "+ Job title", "edit-button"));
  }

  return `<div class="list-actions action-buttons">${buttons.join("")}</div>`;
}

function retiredBadge(entry) {
  return entry.active ? "" : ` <span class="status inactive">Retired</span>`;
}

function renderLists() {
  const body = document.getElementById("listsBody");

  // How many employees have each entry, to help clean up the lists.
  const byDepartment = {};
  const byJobTitle = {};
  employees.forEach((employee) => {
    byDepartment[employee.departmentId] = (byDepartment[employee.departmentId] || 0) + 1;
    byJobTitle[employee.jobTitleId] = (byJobTitle[employee.jobTitleId] || 0) + 1;
  });

  if (departments.length === 0) {
    body.innerHTML = `<p class="list-empty">No departments yet. Add the first one above.</p>`;
    return;
  }

  body.innerHTML = departments.map((department) => `
      <section class="list-department${department.active ? "" : " retired"}">
        <div class="list-row">
          <div class="list-name">
            <strong>${escapeHtml(department.name)}</strong>${retiredBadge(department)}
            <small>${employeeCount(byDepartment[department.departmentId])}</small>
          </div>
          ${listActions("department", department)}
        </div>
        ${department.jobTitles.length === 0
          ? `<p class="list-empty">No job titles yet.</p>`
          : `<ul class="list-titles">${department.jobTitles.map((title) => `
              <li class="list-row">
                <div class="list-name">
                  ${escapeHtml(title.name)}${retiredBadge(title)}
                  <small>${employeeCount(byJobTitle[title.jobTitleId])}</small>
                </div>
                ${listActions("jobTitle", title)}
              </li>`).join("")}
            </ul>`}
      </section>`).join("");
}

async function refreshLists() {
  await Promise.all([loadDepartments(), loadEmployees()]);
  renderLists();
}

function findEntry(kind, id) {
  if (kind === "department") {
    return findDepartment(id);
  }

  for (const department of departments) {
    const title = department.jobTitles.find((item) => item.jobTitleId === id);
    if (title) {
      return title;
    }
  }

  return null;
}

// Rename, Retire, Restore and "+ Job title" in the lists window.
async function runListAction(action, kind, id) {
  const entry = findEntry(kind, id);

  if (!entry) {
    return;
  }

  const base = kind === "department" ? `/api/departments/${id}` : `/api/job-titles/${id}`;
  const label = kind === "department" ? `the department "${entry.name}"` : `the job title "${entry.name}"`;

  if (action === "rename") {
    const name = prompt(`New name for ${label}:`, entry.name);
    if (name === null || name.trim() === "" || name.trim() === entry.name) {
      return;
    }
    await sendJson(base, "PUT", { name: name.trim() });

  } else if (action === "retire") {
    if (!confirm(`Retire ${label}? Employees who have it keep it, but it can't be chosen `
      + "for anyone else until you restore it."
      + (kind === "department" ? " Its job titles can't be chosen either." : ""))) {
      return;
    }
    await sendJson(`${base}/retire`, "POST");

  } else if (action === "restore") {
    await sendJson(`${base}/restore`, "POST");

  } else if (action === "add-title") {
    const name = prompt(`New job title in ${entry.name}:`);
    if (name === null || name.trim() === "") {
      return;
    }
    await sendJson(`${base}/job-titles`, "POST", { name: name.trim() });
  }

  await refreshLists();
}

document.getElementById("listsBody").addEventListener("click", async (event) => {
  const button = event.target.closest("button[data-action]");

  if (!button) {
    return;
  }

  listsMessage.textContent = "";

  try {
    await runListAction(button.dataset.action, button.dataset.kind, Number(button.dataset.id));
  } catch (error) {
    listsMessage.textContent = error.message;
  }
});

document.getElementById("addDepartmentButton").addEventListener("click", async () => {
  const name = prompt("New department name:");

  if (name === null || name.trim() === "") {
    return;
  }

  listsMessage.textContent = "";

  try {
    await sendJson("/api/departments", "POST", { name: name.trim() });
    await refreshLists();
  } catch (error) {
    listsMessage.textContent = error.message;
  }
});

document.getElementById("manageListsButton").addEventListener("click", async () => {
  listsMessage.textContent = "";
  document.getElementById("listsBody").innerHTML = `<p class="list-empty">Loading...</p>`;
  listsModal.classList.add("show");

  try {
    await refreshLists();
  } catch (error) {
    listsMessage.textContent = error.message;
  }
});

function closeListsModal() {
  listsModal.classList.remove("show");
}

document.getElementById("closeListsModal").addEventListener("click", closeListsModal);

listsModal.addEventListener("click", (event) => {
  if (event.target === listsModal) {
    closeListsModal();
  }
});

// A JSON write; throws with the server's message when it is refused.
async function sendJson(url, method, body) {
  const response = await fetch(url, {
    method,
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 401) {
    window.location.href = "/";
    throw new Error("Please log in.");
  }

  const text = await response.text();

  if (!response.ok) {
    throw new Error(text || "The change could not be saved.");
  }

  return text ? JSON.parse(text) : null;
}

/* =========================
   MODAL CONTROLS
========================= */

/* =========================
   SALARY CHANGES
========================= */

document.getElementById("baseSalary").addEventListener("input", updateSalaryChangeFields);

function salaryChanged() {
  const value = document.getElementById("baseSalary").value;
  return editingEmployeeId !== null && value !== "" && Number(value) !== originalSalary;
}

function updateSalaryChangeFields() {
  const changed = salaryChanged();
  document.getElementById("salaryChangeFields").classList.toggle("hidden", !changed);
  document.getElementById("salaryEffectiveDate").required = changed;
}

async function loadSalaryHistory(employeeId) {
  const section = document.getElementById("salaryHistory");
  const body = document.getElementById("salaryHistoryBody");

  section.classList.add("hidden");

  try {
    const response = await fetch(`/api/employees/${employeeId}/salary-history`);

    if (!response.ok || editingEmployeeId !== employeeId) {
      return;
    }

    const changes = await response.json();

    if (changes.length === 0) {
      return;
    }

    body.innerHTML = "";

    changes.forEach((change) => {
      const row = document.createElement("tr");
      row.innerHTML = `
        <td>${escapeHtml(change.effectiveDate)}</td>
        <td class="money">${change.previousSalary == null ? "-" : formatPeso(change.previousSalary)}</td>
        <td class="money">${formatPeso(change.newSalary)}</td>
        <td>${escapeHtml(change.reason || "-")}</td>
        <td>${escapeHtml(change.changedBy ? change.changedBy.username : "-")}</td>`;
      body.appendChild(row);
    });

    section.classList.remove("hidden");
  } catch (error) {
    console.error("Salary history loading error:", error);
  }
}

function formatPeso(value) {
  return "₱" + Number(value).toLocaleString("en-PH", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function todayIso() {
  const today = new Date();
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
}

function closeModal() {
  employeeModal.classList.remove("show");

  employeeForm.reset();

  closeAddRows();

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

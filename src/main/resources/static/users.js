// Users page: login accounts, roles, passwords. Administrators only; the
// server enforces the rules (unique usernames, password strength, last
// active administrator, no deleting your own account) and audits changes.

const ROLE_LABELS = {
  ADMINISTRATOR: "Administrator",
  HR_MANAGER: "HR manager",
  GENERAL_MANAGER: "General manager",
  EMPLOYEE: "Employee",
};

const MIN_PASSWORD_LENGTH = 12;

let users = [];
let employees = [];
let currentUserId = null;

const $ = (id) => document.getElementById(id);

document.addEventListener("DOMContentLoaded", () => {
  $("addUserButton").addEventListener("click", () => openUserModal(null));
  $("userForm").addEventListener("submit", saveUser);

  document.querySelectorAll("[data-close]").forEach((button) => {
    button.addEventListener("click", () => closeModal(button.dataset.close));
  });

  $("logoutButton").addEventListener("click", (event) => {
    event.preventDefault();
    logout();
  });

  Promise.all([loadCurrentUser(), loadEmployees()]).then(loadUsers);
});

// ---------- requests ----------

// Fetches JSON; throws an Error with the server's message on failure.
async function api(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: options.body ? { "Content-Type": "application/json" } : undefined,
  });

  if (response.status === 401) {
    window.location.href = "/";
    throw new Error("Please log in.");
  }

  if (response.status === 403) {
    throw new Error("Only administrators can manage users.");
  }

  if (!response.ok) {
    const text = (await response.text()).trim();
    throw new Error(text || `Request failed (${response.status}).`);
  }

  return response.status === 204 ? null : response.json();
}

async function loadCurrentUser() {
  try {
    currentUserId = (await api("/api/auth/me")).userId;
  } catch (error) {
    currentUserId = null;
  }
}

async function loadEmployees() {
  try {
    employees = await api("/api/employees");
  } catch (error) {
    employees = [];
  }

  employees.sort((a, b) => `${a.lastName} ${a.firstName}`.localeCompare(`${b.lastName} ${b.firstName}`));
}

async function loadUsers() {
  const body = $("userTableBody");

  try {
    users = await api("/api/users");
  } catch (error) {
    setEmptyRow(body, 5, error.message);
    $("userSummary").textContent = "";
    $("addUserButton").classList.add("hidden");
    return;
  }

  users.sort((a, b) => a.username.localeCompare(b.username));

  const activeAdmins = users.filter((u) => u.role === "ADMINISTRATOR" && u.status === "ACTIVE").length;
  $("userSummary").textContent =
    `${users.length} account(s), ${activeAdmins} active administrator(s).`;

  if (users.length === 0) {
    setEmptyRow(body, 5, "No users.");
    return;
  }

  body.innerHTML = "";

  users.forEach((user) => {
    const row = document.createElement("tr");
    const isMe = user.userId === currentUserId;
    const statusClass = user.status === "ACTIVE" ? "badge" : "badge badge-locked";

    row.innerHTML = `
      <td>${escapeHtml(user.username)}${isMe ? ` <span class="badge">You</span>` : ""}</td>
      <td>${escapeHtml(ROLE_LABELS[user.role] || user.role)}</td>
      <td><span class="${statusClass}">${user.status === "ACTIVE" ? "Active" : "Inactive"}</span></td>
      <td>${escapeHtml(employeeLabel(user.employeeId))}</td>
      <td>
        <button type="button" class="action-button edit-button">Edit</button>
        ${isMe ? "" : `<button type="button" class="action-button delete-button">Delete</button>`}
      </td>`;

    row.querySelector(".edit-button").addEventListener("click", () => openUserModal(user));

    if (!isMe) {
      row.querySelector(".delete-button").addEventListener("click", () => deleteUser(user));
    }

    body.appendChild(row);
  });
}

// ---------- add / edit ----------

function openUserModal(user) {
  const editing = Boolean(user);

  $("userModalTitle").textContent = editing ? `Edit ${user.username}` : "Add User";
  $("userModalHint").textContent = editing
    ? "Leave the password empty to keep the current one."
    : "Give the password to the person privately.";

  $("userId").value = editing ? user.userId : "";
  $("userUsername").value = editing ? user.username : "";
  $("userRole").value = editing ? user.role : "HR_MANAGER";
  $("userStatus").value = editing ? user.status : "ACTIVE";
  $("userPassword").value = "";
  $("userPasswordConfirm").value = "";
  $("userPassword").required = !editing;
  $("userPasswordConfirm").required = !editing;
  $("userPasswordLabel").textContent = editing ? "New password (optional)" : "Password";

  fillEmployeeOptions(editing ? user.employeeId : null, editing ? user.userId : null);
  clearMessage("userMessage");
  openModal("userModal");
  $("userUsername").focus();
}

// Employees that already have a login are listed but cannot be chosen.
function fillEmployeeOptions(selectedId, editingUserId) {
  const select = $("userEmployee");
  select.innerHTML = `<option value="">None</option>`;

  employees.forEach((employee) => {
    const owner = users.find((u) => u.employeeId === employee.employeeId && u.userId !== editingUserId);
    const option = document.createElement("option");

    option.value = employee.employeeId;
    option.textContent = `${employee.lastName}, ${employee.firstName} (#${employee.employeeId})`
      + (owner ? ` - login: ${owner.username}` : "");
    option.disabled = Boolean(owner);
    select.appendChild(option);
  });

  select.value = selectedId ?? "";
}

async function saveUser(event) {
  event.preventDefault();

  const id = $("userId").value;
  const password = $("userPassword").value;
  const confirmation = $("userPasswordConfirm").value;
  const role = $("userRole").value;
  const employeeId = $("userEmployee").value ? Number($("userEmployee").value) : null;

  if (password !== confirmation) {
    showError("userMessage", "The two passwords do not match.");
    return;
  }

  if (password && password.length < MIN_PASSWORD_LENGTH) {
    showError("userMessage", `Password must be at least ${MIN_PASSWORD_LENGTH} characters.`);
    return;
  }

  if (role === "EMPLOYEE" && employeeId === null) {
    showError("userMessage", "An employee login must be linked to an employee, or they will see no payslips.");
    return;
  }

  const body = {
    username: $("userUsername").value.trim(),
    role,
    status: $("userStatus").value,
    employeeId,
  };

  if (password) {
    body.password = password;
  }

  const editingSelf = id && Number(id) === currentUserId;
  const before = users.find((u) => String(u.userId) === id);

  try {
    await api(id ? `/api/users/${id}` : "/api/users", {
      method: id ? "PUT" : "POST",
      body: JSON.stringify(body),
    });
  } catch (error) {
    showError("userMessage", error.message);
    return;
  }

  closeModal("userModal");

  // The session still carries the old username and role, so log in again.
  if (editingSelf && (body.username !== before.username || body.role !== before.role || body.status !== before.status)) {
    alert("Your own account changed. Please log in again.");
    logout();
    return;
  }

  showSuccess("pageMessage", id
    ? `Saved ${body.username}${password ? " (password changed)" : ""}.`
    : `Created ${body.username}.`);
  await loadUsers();
}

async function deleteUser(user) {
  if (!confirm(`Delete the login "${user.username}"? Their past actions stay in the audit log. To block a login but keep it, set it to Inactive instead.`)) {
    return;
  }

  try {
    await api(`/api/users/${user.userId}`, { method: "DELETE" });
  } catch (error) {
    showError("pageMessage", error.message);
    return;
  }

  showSuccess("pageMessage", `Deleted ${user.username}.`);
  await loadUsers();
}

// ---------- helpers ----------

function employeeLabel(employeeId) {
  if (employeeId === null || employeeId === undefined) {
    return "-";
  }

  const employee = employees.find((e) => e.employeeId === employeeId);
  return employee ? `${employee.lastName}, ${employee.firstName} (#${employeeId})` : `#${employeeId}`;
}

function openModal(id) {
  $(id).classList.add("show");
  $(id).setAttribute("aria-hidden", "false");
}

function closeModal(id) {
  $(id).classList.remove("show");
  $(id).setAttribute("aria-hidden", "true");
}

function showError(id, text) {
  $(id).textContent = text;
  $(id).className = $(id).className.replace(/\s*(error|success)\b/g, "") + " error";
}

function showSuccess(id, text) {
  $(id).textContent = text;
  $(id).className = $(id).className.replace(/\s*(error|success)\b/g, "") + " success";
}

function clearMessage(id) {
  $(id).textContent = "";
  $(id).className = $(id).className.replace(/\s*(error|success)\b/g, "");
}

function setEmptyRow(body, columns, text) {
  body.innerHTML = `<tr><td colspan="${columns}" class="empty-row"></td></tr>`;
  body.querySelector("td").textContent = text;
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

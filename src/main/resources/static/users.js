// Users page: login accounts, roles, passwords. Administrators only; the
// server enforces the rules (unique usernames, password strength, last
// active administrator, no deleting your own account) and audits changes.

const ROLE_LABELS = {
  ADMINISTRATOR: "Administrator",
  HR_MANAGER: "HR manager",
  GENERAL_MANAGER: "General manager",
  EMPLOYEE: "Employee",
  KIOSK: "Kiosk",
};

// The kiosk login is the attendance computer, not a person: no employee.
const DEVICE_ROLES = ["KIOSK"];

const MIN_PASSWORD_LENGTH = 12;

let users = [];
let employees = [];
let currentUserId = null;

const $ = (id) => document.getElementById(id);

document.addEventListener("DOMContentLoaded", () => {
  $("addUserButton").addEventListener("click", () => openUserModal(null));
  $("userForm").addEventListener("submit", saveUser);
  $("userRole").addEventListener("change", applyRoleToEmployeeField);
  $("copyTemporaryButton").addEventListener("click", copyTemporaryPassword);

  document.querySelectorAll("[data-close]").forEach((button) => {
    button.addEventListener("click", () => closeModal(button.dataset.close));
  });

  $("logoutButton").addEventListener("click", (event) => {
    event.preventDefault();
    logout();
  });

  Promise.all([loadCurrentUser(), loadEmployees()]).then(() => Promise.all([loadUsers(), loadResetRequests()]));
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
      <td><span class="${statusClass}">${user.status === "ACTIVE" ? "Active" : "Inactive"}</span>${
        user.mustChangePassword ? ` <span class="badge badge-warning" title="Has not yet replaced the temporary password">Temporary password</span>` : ""}</td>
      <td>${escapeHtml(employeeLabel(user.employeeId))}</td>
      <td>
        <button type="button" class="action-button edit-button">Edit</button>
        ${isMe || user.status !== "ACTIVE" ? "" : `<button type="button" class="action-button reset-button">Reset password</button>`}
        ${isMe ? "" : `<button type="button" class="action-button delete-button">Delete</button>`}
      </td>`;

    row.querySelector(".edit-button").addEventListener("click", () => openUserModal(user));
    row.querySelector(".reset-button")?.addEventListener("click", () => setTemporaryPassword(user, "pageMessage"));

    if (!isMe) {
      row.querySelector(".delete-button").addEventListener("click", () => deleteUser(user));
    }

    body.appendChild(row);
  });
}

// ---------- forgot password ----------

async function loadResetRequests() {
  let requests;

  try {
    requests = await api("/api/users/password-resets");
  } catch (error) {
    requests = [];
  }

  $("resetSection").hidden = requests.length === 0;

  const body = $("resetTableBody");
  body.innerHTML = "";

  requests.forEach((request) => {
    const row = document.createElement("tr");

    row.innerHTML = `
      <td>${escapeHtml(formatDateTime(request.requestedAt))}</td>
      <td>${escapeHtml(request.username)}</td>
      <td>${escapeHtml(ROLE_LABELS[request.role] || request.role)}</td>
      <td>${escapeHtml(request.employeeName || "-")}</td>
      <td>${escapeHtml(request.requestedFrom || "-")}</td>
      <td>
        <button type="button" class="action-button edit-button">Set temporary password</button>
        <button type="button" class="action-button delete-button">Dismiss</button>
      </td>`;

    row.querySelector(".edit-button").addEventListener("click", () =>
      setTemporaryPassword({ userId: request.userId, username: request.username }, "resetMessage"));
    row.querySelector(".delete-button").addEventListener("click", () => dismissRequest(request));

    body.appendChild(row);
  });

  if (window.refreshPasswordResetBadge) {
    window.refreshPasswordResetBadge();
  }
}

async function setTemporaryPassword(user, messageId) {
  if (!confirm(`Set a temporary password for "${user.username}"? Their current password stops working at once. `
    + "Do this only after checking that the request really comes from them.")) {
    return;
  }

  let result;

  try {
    result = await api(`/api/users/${user.userId}/temporary-password`, { method: "POST" });
  } catch (error) {
    showError(messageId, error.message);
    return;
  }

  clearMessage(messageId);
  $("temporaryHint").textContent = `For ${result.username}. Give it to them in person.`;
  $("temporaryValue").textContent = result.temporaryPassword;
  clearMessage("temporaryMessage");
  openModal("temporaryModal");

  await Promise.all([loadUsers(), loadResetRequests()]);
}

async function dismissRequest(request) {
  if (!confirm(`Dismiss the password reset request from "${request.username}"? Their password stays as it is.`)) {
    return;
  }

  try {
    await api(`/api/users/password-resets/${request.requestId}/dismiss`, { method: "POST" });
  } catch (error) {
    showError("resetMessage", error.message);
    return;
  }

  showSuccess("pageMessage", `Dismissed the request from ${request.username}.`);
  await loadResetRequests();
}

async function copyTemporaryPassword() {
  try {
    await navigator.clipboard.writeText($("temporaryValue").textContent);
    showSuccess("temporaryMessage", "Copied.");
  } catch (error) {
    showError("temporaryMessage", "Could not copy. Select the password and copy it by hand.");
  }
}

function formatDateTime(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? String(value)
    : date.toLocaleString("en-PH", { dateStyle: "medium", timeStyle: "short" });
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
  if (window.hidePasswords) {
    window.hidePasswords($("userForm"));
  }
  $("userPassword").required = !editing;
  $("userPasswordConfirm").required = !editing;
  $("userPasswordLabel").textContent = editing ? "New password (optional)" : "Password";

  fillEmployeeOptions(editing ? user.employeeId : null, editing ? user.userId : null);
  applyRoleToEmployeeField();
  clearMessage("userMessage");
  openModal("userModal");
  $("userUsername").focus();
}

// Employees that already have a login are listed but cannot be chosen.
function fillEmployeeOptions(selectedId, editingUserId) {
  const select = $("userEmployee");
  select.innerHTML = `<option value="">Choose an employee</option>`;

  employees.forEach((employee) => {
    const owner = users.find((u) => u.employeeId === employee.employeeId && u.userId !== editingUserId);
    const option = document.createElement("option");

    option.value = employee.employeeId;
    option.textContent = `${employee.lastName}, ${employee.firstName} (#${employee.employeeId})`
      + (employee.employmentStatus === "INACTIVE" ? " - former employee" : "")
      + (owner ? ` - login: ${owner.username}` : "");
    option.disabled = Boolean(owner);
    select.appendChild(option);
  });

  select.value = selectedId ?? "";
}

function applyRoleToEmployeeField() {
  const select = $("userEmployee");
  const device = DEVICE_ROLES.includes($("userRole").value);

  if (device) {
    select.value = "";
  }
  select.disabled = device;
  select.required = !device;
  if (select.options[0]) {
    select.options[0].textContent = device ? "None: the kiosk is a computer, not a person" : "Choose an employee";
  }
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

  // Logins are only for people who work for the company (the kiosk computer
  // excepted); the server checks this too.
  if (employeeId === null && !DEVICE_ROLES.includes(role)) {
    showError("userMessage", "Choose the employee this login belongs to. Logins are only for employees of the company.");
    return;
  }

  const linked = employees.find((e) => e.employeeId === employeeId);
  if (linked && linked.employmentStatus === "INACTIVE" && $("userStatus").value === "ACTIVE") {
    showError("userMessage", "This employee no longer works for the company. Their login can only be inactive.");
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

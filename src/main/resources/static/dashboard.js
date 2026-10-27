const $ = (id) => document.getElementById(id);

const currentUsername = $("currentUsername");
const currentUserRole = $("currentUserRole");
const userAvatar = $("userAvatar");
const logoutButton = $("logoutButton");
const dashboardMessage = $("dashboardMessage");

async function fetchJson(url) {
  const response = await fetch(url, {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
  });

  if (response.status === 401) {
    window.location.href = "/";
    throw new Error("Your session has expired. Please sign in again.");
  }
  if (!response.ok) {
    throw new Error(`${url} returned HTTP ${response.status}`);
  }
  return response.json();
}

function asArray(value) {
  if (Array.isArray(value)) return value;
  if (value && Array.isArray(value.content)) return value.content;
  if (value && Array.isArray(value.data)) return value.data;
  return [];
}

function money(value) {
  const number = Number(value);
  return new Intl.NumberFormat("en-PH", {
    style: "currency",
    currency: "PHP",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number.isFinite(number) ? number : 0);
}

function setMessage(message, isError = false) {
  if (!dashboardMessage) return;
  dashboardMessage.textContent = message || "";
  dashboardMessage.classList.toggle("error", isError);
}

async function loadCurrentUser() {
  const user = await fetchJson("/api/auth/me");
  const username = user.username || user.name || "User";
  const role = String(user.role || "USER")
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());

  currentUsername.textContent = username;
  currentUserRole.textContent = role;
  userAvatar.textContent = username.trim().charAt(0).toUpperCase() || "U";

  return user;
}

async function loadDashboardSummary() {
  const results = await Promise.allSettled([
    fetchJson("/api/employees"),
    fetchJson("/api/attendance"),
    fetchJson("/api/payroll"),
    fetchJson("/api/overtime?status=APPROVED"),
  ]);

  const [employeesResult, attendanceResult, payrollResult, overtimeResult] =
    results;
  let failedModules = 0;

  if (employeesResult.status === "fulfilled") {
    const employees = asArray(employeesResult.value);
    $("employeeCount").textContent = employees.length;
  } else {
    $("employeeCount").textContent = "—";
    failedModules++;
    console.error("Unable to load employees:", employeesResult.reason);
  }

  if (attendanceResult.status === "fulfilled") {
    const attendance = asArray(attendanceResult.value);
    const today = new Date();
    const localToday = [
      today.getFullYear(),
      String(today.getMonth() + 1).padStart(2, "0"),
      String(today.getDate()).padStart(2, "0"),
    ].join("-");
    const todayCount = attendance.filter((record) => {
      const date = String(record.attendanceDate || record.date || "").slice(
        0,
        10,
      );
      return date === localToday;
    }).length;
    $("attendanceCount").textContent = todayCount;
  } else {
    $("attendanceCount").textContent = "—";
    failedModules++;
    console.error("Unable to load attendance:", attendanceResult.reason);
  }

  if (payrollResult.status === "fulfilled") {
    const payroll = asArray(payrollResult.value);
    const totalNetPay = payroll.reduce((sum, record) => {
      const amount = Number(record.netPay ?? 0);
      return sum + (Number.isFinite(amount) ? amount : 0);
    }, 0);
    $("payrollAmount").textContent = money(totalNetPay);
  } else {
    $("payrollAmount").textContent = "—";
    failedModules++;
    console.error("Unable to load payroll:", payrollResult.reason);
  }

  if (overtimeResult.status === "fulfilled") {
    const overtime = asArray(overtimeResult.value);
    const approvedHours = overtime.reduce((sum, record) => {
      if (String(record.status || "").toUpperCase() !== "APPROVED") return sum;
      const hours = Number(record.hours ?? 0);
      return sum + (Number.isFinite(hours) ? hours : 0);
    }, 0);
    $("overtimeHours").textContent = approvedHours.toLocaleString("en-PH", {
      minimumFractionDigits: 0,
      maximumFractionDigits: 2,
    });
  } else {
    $("overtimeHours").textContent = "—";
    failedModules++;
    console.error("Unable to load overtime:", overtimeResult.reason);
  }

  if (failedModules > 0) {
    setMessage(
      `Some dashboard totals could not be loaded (${failedModules} module${failedModules === 1 ? "" : "s"}). Check the browser console and API routes.`,
      true,
    );
  } else {
    setMessage("");
  }
}

// ---------- pending approvals (administrators and HR) ----------

function tableCell(content, className) {
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

function emptyRow(body, columns, message) {
  const row = document.createElement("tr");
  const td = tableCell(message, "empty");
  td.colSpan = columns;
  row.appendChild(td);
  body.replaceChildren(row);
}

function feedback(id, message, kind) {
  const element = $(id);
  element.textContent = message || "";
  element.className = "overview-feedback" + (kind ? " " + kind : "");
}

async function readError(response, fallback) {
  const message = (await response.text()).trim();
  return message || fallback;
}

async function loadApprovals() {
  const body = $("approvalsTableBody");

  try {
    // Filtered and sorted by date on the server.
    const pending = asArray(await fetchJson("/api/overtime?status=PENDING"));

    if (pending.length === 0) {
      emptyRow(body, 5, "Nothing is waiting for approval.");
      return;
    }

    body.replaceChildren(
      ...pending.map((record) => {
        const row = document.createElement("tr");
        const actions = document.createElement("div");
        const who = record.employeeName || `Employee #${record.employeeId}`;

        actions.className = "action-buttons";
        actions.append(
          decisionButton(record, "APPROVED", "Approve", "action-approve", who),
          decisionButton(record, "REJECTED", "Reject", "action-reject", who),
        );

        row.append(
          tableCell(who),
          tableCell(record.overtimeDate || "—"),
          tableCell(String(record.hours ?? "—"), "amount"),
          tableCell(money(record.overtimeAmount), "amount"),
          tableCell(actions),
        );

        return row;
      }),
    );
  } catch (error) {
    emptyRow(body, 5, "Unavailable.");
    feedback("approvalsMessage", error.message, "error");
  }
}

// For the General Manager and administrators: processed payroll that
// needs their sign-off before HR can pay it.
async function loadSignoffNotice() {
  try {
    const waiting = asArray(await fetchJson("/api/payroll"))
      .filter((payroll) => payroll.status === "PROCESSED").length;

    $("signoffText").textContent =
      `${waiting} processed payroll ${waiting === 1 ? "is" : "are"} waiting for your approval.`;
    $("signoffNotice").hidden = waiting === 0;
  } catch (error) {
    $("signoffNotice").hidden = true;
  }
}

// Pending leave, oldest first, with the same Approve and Reject buttons.
async function loadLeaveApprovals() {
  const body = $("leaveApprovalsTableBody");

  try {
    const pending = asArray(await fetchJson("/api/leave?status=PENDING"));

    if (pending.length === 0) {
      emptyRow(body, 5, "No leave is waiting for approval.");
      return;
    }

    body.replaceChildren(
      ...pending.map((leave) => {
        const row = document.createElement("tr");
        const actions = document.createElement("div");
        const dates = leave.startDate === leave.endDate ? leave.startDate : `${leave.startDate} to ${leave.endDate}`;
        const type = leave.reason ? `${leave.leaveTypeLabel}: ${leave.reason}` : leave.leaveTypeLabel;

        actions.className = "action-buttons";
        actions.append(
          leaveDecisionButton(leave, dates, "APPROVED", "Approve", "action-approve"),
          leaveDecisionButton(leave, dates, "REJECTED", "Reject", "action-reject"),
        );

        row.append(
          tableCell(leave.employeeName),
          tableCell(dates),
          tableCell(type),
          tableCell(String(leave.workingDays), "amount"),
          tableCell(actions),
        );

        return row;
      }),
    );
  } catch (error) {
    emptyRow(body, 5, "Unavailable.");
    feedback("approvalsMessage", error.message, "error");
  }
}

function leaveDecisionButton(leave, dates, status, label, className) {
  const button = document.createElement("button");

  button.type = "button";
  button.className = `action-button ${className}`;
  button.textContent = label;
  button.setAttribute("aria-label", `${label} leave of ${leave.employeeName}, ${dates}`);
  button.addEventListener("click", () => decideLeave(leave, dates, status));

  return button;
}

async function decideLeave(leave, dates, status) {
  const verb = status === "APPROVED" ? "Approve" : "Reject";
  const days = `${leave.workingDays} working day${leave.workingDays === 1 ? "" : "s"}`;
  const what = `${leave.leaveTypeLabel.toLowerCase()} for ${leave.employeeName}, ${dates} (${days})`;
  const reason = await confirmOrAskReason(status, `${verb} ${what}?`, "Reject leave", what);

  if (reason === null) {
    return;
  }

  try {
    const response = await fetch(
      `/api/leave/${encodeURIComponent(leave.leaveId)}/status?status=${status}`,
      decisionRequest(reason),
    );

    if (!response.ok) {
      throw new Error(await readError(response, `Unable to ${verb.toLowerCase()} this leave.`));
    }

    await loadLeaveApprovals();
    feedback("approvalsMessage", `Leave for ${leave.employeeName}, ${dates}, ${status.toLowerCase()}.`, "success");
  } catch (error) {
    feedback("approvalsMessage", error.message, "error");
  }
}

// Approving asks for a confirmation; rejecting asks for the reason, which
// the employee will see. Resolves to "" (approve), the reason, or null.
async function confirmOrAskReason(status, question, rejectTitle, what) {
  if (status === "APPROVED") {
    return confirm(question) ? "" : null;
  }

  return askForReason({
    title: rejectTitle,
    message: `${what}. The employee will see this reason on My Payroll.`,
    confirmLabel: "Reject",
  });
}

function decisionRequest(reason) {
  return {
    method: "PUT",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(reason ? { reason } : {}),
  };
}

function decisionButton(record, status, label, className, who) {
  const button = document.createElement("button");

  button.type = "button";
  button.className = `action-button ${className}`;
  button.textContent = label;
  button.setAttribute("aria-label", `${label} overtime of ${who} on ${record.overtimeDate}`);
  button.addEventListener("click", () => decide(record, status, who));

  return button;
}

async function decide(record, status, who) {
  const verb = status === "APPROVED" ? "Approve" : "Reject";
  const what = `${record.hours} h of overtime for ${who} on ${record.overtimeDate}`;
  const reason = await confirmOrAskReason(status, `${verb} ${what}?`, "Reject overtime", what);

  if (reason === null) {
    return;
  }

  try {
    const response = await fetch(
      `/api/overtime/${encodeURIComponent(record.overtimeId)}/status?status=${status}`,
      decisionRequest(reason),
    );

    if (!response.ok) {
      throw new Error(await readError(response, `Unable to ${verb.toLowerCase()} this overtime.`));
    }

    await Promise.all([loadApprovals(), window.CompanyOverview.reload(), loadDashboardSummary()]);
    feedback("approvalsMessage", `Overtime for ${who} on ${record.overtimeDate} ${status.toLowerCase()}.`, "success");
  } catch (error) {
    feedback("approvalsMessage", error.message, "error");
  }
}

// The Company Overview is drawn by company-overview.js.

logoutButton.addEventListener("click", async () => {
  logoutButton.disabled = true;
  try {
    const response = await fetch("/api/auth/logout", {
      method: "POST",
      credentials: "same-origin",
    });
    if (response.ok) {
      window.location.href = "/";
      return;
    }
    setMessage("Unable to log out. Please try again.", true);
  } catch (error) {
    console.error("Logout error:", error);
    setMessage("Unable to connect to the server while logging out.", true);
  } finally {
    logoutButton.disabled = false;
  }
});

async function initializeDashboard() {
  try {
    const user = await loadCurrentUser();

    const tasks = [loadDashboardSummary()];

    if (user.role === "GENERAL_MANAGER" || user.role === "ADMINISTRATOR") {
      tasks.push(loadSignoffNotice());
    }

    // Approving overtime and leave is for administrators and HR.
    if (user.role === "ADMINISTRATOR" || user.role === "HR_MANAGER") {
      $("approvalsSection").hidden = false;
      tasks.push(loadApprovals(), loadLeaveApprovals());
    }

    await Promise.all(tasks);
  } catch (error) {
    console.error("Dashboard initialization failed:", error);
    if (!window.location.pathname.endsWith("/")) {
      // Authentication errors are redirected by fetchJson. This message
      // covers other failures, such as a temporary API problem.
      setMessage(
        "The dashboard could not load all information. Refresh the page and check the browser console if the issue continues.",
        true,
      );
    }
  }
}

initializeDashboard();

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

  if (!confirm(`${verb} ${record.hours} h of overtime for ${who} on ${record.overtimeDate}?`)) {
    return;
  }

  try {
    const response = await fetch(
      `/api/overtime/${encodeURIComponent(record.overtimeId)}/status?status=${status}`,
      { method: "PUT", credentials: "same-origin" },
    );

    if (!response.ok) {
      throw new Error(await readError(response, `Unable to ${verb.toLowerCase()} this overtime.`));
    }

    await Promise.all([loadApprovals(), loadOverview(), loadDashboardSummary()]);
    feedback("approvalsMessage", `Overtime for ${who} on ${record.overtimeDate} ${status.toLowerCase()}.`, "success");
  } catch (error) {
    feedback("approvalsMessage", error.message, "error");
  }
}

// ---------- company overview (administrators, HR, general managers) ----------

const overviewMonth = $("overviewMonth");
const monthName = new Intl.DateTimeFormat("en-PH", { month: "short", year: "numeric" });

function monthLabel(yearMonth) {
  const [year, month] = String(yearMonth).split("-").map(Number);
  return monthName.format(new Date(year, month - 1, 1));
}

function compactMoney(value) {
  const number = Number(value) || 0;
  return number >= 1000 ? `₱${(number / 1000).toLocaleString("en-PH", { maximumFractionDigits: 1 })}k` : money(number);
}

async function loadOverview() {
  const [year, month] = overviewMonth.value.split("-");

  feedback("overviewMessage", "Loading...", null);

  let summary;

  try {
    summary = await fetchJson(`/api/dashboard/executive?year=${Number(year)}&month=${Number(month)}`);
  } catch (error) {
    feedback("overviewMessage", "The company overview could not be loaded.", "error");
    console.error(error);
    return;
  }

  feedback("overviewMessage", "", null);
  renderTotals(summary);
  renderDepartments(summary);
  renderTrend(summary.trend);

  $("overviewNotes").replaceChildren(
    ...(summary.notes || []).map((note) => {
      const item = document.createElement("li");
      item.textContent = note;
      return item;
    }),
  );
}

function renderTotals(summary) {
  const tiles = summary.totals.map((total) => [total.label, money(total.amount), total.key === "laborCost"]);

  tiles.push(["Active employees", String(summary.activeEmployees), false]);
  tiles.push(["Pending overtime requests", String(summary.pendingOvertimeRequests), false]);

  $("overviewTotals").replaceChildren(
    ...tiles.map(([label, value, primary]) => {
      const tile = document.createElement("div");
      const name = document.createElement("span");
      const amount = document.createElement("strong");

      tile.className = "overview-tile" + (primary ? " primary" : "");
      name.textContent = label;
      amount.textContent = value;
      tile.append(name, amount);

      return tile;
    }),
  );
}

function renderDepartments(summary) {
  const body = $("departmentTableBody");

  if (summary.departments.length === 0) {
    emptyRow(body, 5, "No employees or payroll for this month.");
    return;
  }

  body.replaceChildren(
    ...summary.departments.map((department) => {
      const row = document.createElement("tr");

      row.append(
        tableCell(department.department),
        tableCell(String(department.activeEmployees), "amount"),
        tableCell(money(department.amounts.grossPay), "amount"),
        tableCell(money(department.amounts.employerContributions), "amount"),
        tableCell(money(department.amounts.laborCost), "amount"),
      );

      return row;
    }),
  );
}

function renderTrend(trend) {
  const chart = $("trendChart");
  const tooltip = $("trendTooltip");
  const highest = Math.max(...trend.map((point) => Number(point.amounts.laborCost) || 0), 0);

  chart.replaceChildren(
    ...trend.map((point, index) => {
      const value = Number(point.amounts.laborCost) || 0;
      const column = document.createElement("button");
      const bar = document.createElement("span");
      const label = `${monthLabel(point.month)}: ${money(value)}`;

      column.type = "button";
      column.className = "trend-column";
      column.setAttribute("aria-label", `Labor cost ${label}`);

      bar.className = "trend-bar";
      bar.style.height = highest > 0 ? `${(value / highest) * 100}%` : "0";
      column.appendChild(bar);

      // Direct label on the latest month only; the rest are in the tooltip and table.
      if (index === trend.length - 1 && value > 0) {
        const tip = document.createElement("span");
        tip.className = "trend-value";
        tip.textContent = compactMoney(value);
        tip.style.bottom = bar.style.height;
        column.appendChild(tip);
      }

      const show = () => {
        tooltip.textContent = label;
        tooltip.hidden = false;
        tooltip.style.left = `${column.offsetLeft + column.offsetWidth / 2}px`;
        tooltip.style.top = `${column.offsetTop + column.offsetHeight - bar.offsetHeight - 6}px`;
      };
      const hide = () => {
        tooltip.hidden = true;
      };

      column.addEventListener("mouseenter", show);
      column.addEventListener("focus", show);
      column.addEventListener("mouseleave", hide);
      column.addEventListener("blur", hide);

      return column;
    }),
  );

  let labels = chart.nextElementSibling;
  if (!labels || !labels.classList.contains("trend-labels")) {
    labels = document.createElement("div");
    labels.className = "trend-labels";
    labels.setAttribute("aria-hidden", "true");
    chart.after(labels);
  }
  labels.replaceChildren(
    ...trend.map((point) => {
      const name = document.createElement("span");
      name.textContent = monthLabel(point.month).replace(/ \d{4}$/, "");
      return name;
    }),
  );

  $("trendTableBody").replaceChildren(
    ...trend.slice().reverse().map((point) => {
      const row = document.createElement("tr");

      row.append(
        tableCell(monthLabel(point.month)),
        tableCell(money(point.amounts.grossPay), "amount"),
        tableCell(money(point.amounts.overtimePay), "amount"),
        tableCell(money(point.amounts.laborCost), "amount"),
      );

      return row;
    }),
  );
}

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

    const today = new Date();
    overviewMonth.value = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`;
    overviewMonth.addEventListener("change", () => {
      if (overviewMonth.value) {
        loadOverview();
      }
    });

    const tasks = [loadDashboardSummary(), loadOverview()];

    // Approving is for administrators and HR; general managers only read.
    if (user.role === "ADMINISTRATOR" || user.role === "HR_MANAGER") {
      $("approvalsSection").hidden = false;
      tasks.push(loadApprovals());
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

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

  if (response.status === 401 || response.status === 403) {
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
}

async function loadDashboardSummary() {
  const results = await Promise.allSettled([
    fetchJson("/api/employees"),
    fetchJson("/api/attendance"),
    fetchJson("/api/payroll"),
    fetchJson("/api/overtime"),
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
    await loadCurrentUser();
    await loadDashboardSummary();
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

// Audit Log page: every recorded change, newest first, with filters,
// paging and a CSV export of the same filters. Administrators only; the
// server refuses anyone else. Nothing here can change an entry.

const PAGE_SIZE = 50;

const $ = (id) => document.getElementById(id);

let currentPage = 0;

const dateTime = new Intl.DateTimeFormat("en-PH", {
  year: "numeric",
  month: "short",
  day: "numeric",
  hour: "numeric",
  minute: "2-digit",
  second: "2-digit",
});

document.addEventListener("DOMContentLoaded", () => {
  $("auditFilters").addEventListener("submit", (event) => {
    event.preventDefault();
    loadPage(0);
  });

  $("auditClear").addEventListener("click", () => {
    $("auditFilters").reset();
    loadPage(0);
  });

  $("auditPrevious").addEventListener("click", () => loadPage(currentPage - 1));
  $("auditNext").addEventListener("click", () => loadPage(currentPage + 1));

  $("logoutButton").addEventListener("click", (event) => {
    event.preventDefault();
    logout();
  });

  loadActions();
  loadPage(0);
});

// The filters as query parameters; the CSV link uses the same ones.
function filterParams() {
  const params = new URLSearchParams();
  const fields = { from: "auditFrom", to: "auditTo", username: "auditUser", action: "auditAction", text: "auditText" };

  Object.entries(fields).forEach(([name, id]) => {
    const value = $(id).value.trim();
    if (value) {
      params.set(name, value);
    }
  });

  return params;
}

async function api(url) {
  const response = await fetch(url, { credentials: "same-origin" });

  if (response.status === 401) {
    window.location.href = "/";
    throw new Error("Please log in.");
  }

  if (response.status === 403) {
    throw new Error("The audit log is available to administrators only.");
  }

  if (!response.ok) {
    throw new Error((await response.text()).trim() || `Request failed (${response.status}).`);
  }

  return response.json();
}

async function loadActions() {
  try {
    const actions = await api("/api/audit-logs/actions");
    const select = $("auditAction");

    actions.forEach((action) => {
      const option = document.createElement("option");
      option.value = action;
      option.textContent = actionLabel(action);
      select.appendChild(option);
    });
  } catch (error) {
    // The list still works without the action filter.
    console.error("Audit actions loading error:", error);
  }
}

async function loadPage(page) {
  const body = $("auditTableBody");
  const params = filterParams();

  $("auditMessage").textContent = "";
  $("auditMessage").className = "form-message report-message";
  $("auditExport").href = `/api/audit-logs/export.csv?${params}`;

  params.set("page", Math.max(page, 0));
  params.set("size", PAGE_SIZE);

  setEmptyRow(body, "Loading...");

  let result;

  try {
    result = await api(`/api/audit-logs/search?${params}`);
  } catch (error) {
    $("auditMessage").textContent = error.message;
    $("auditMessage").className = "form-message report-message error";
    setEmptyRow(body, "-");
    $("auditSummary").textContent = "";
    updatePager(0, 0);
    return;
  }

  currentPage = result.page;

  $("auditSummary").textContent = result.totalEntries === 0
    ? "No entries match."
    : `${result.totalEntries.toLocaleString("en-PH")} entr${result.totalEntries === 1 ? "y" : "ies"}, newest first.`;

  if (result.entries.length === 0) {
    setEmptyRow(body, "No entries match these filters.");
  } else {
    body.replaceChildren(...result.entries.map(renderRow));
  }

  updatePager(result.page, result.totalPages);
}

function renderRow(entry) {
  const row = document.createElement("tr");

  const when = document.createElement("td");
  when.className = "audit-time";
  when.textContent = entry.createdAt ? dateTime.format(new Date(entry.createdAt)) : "-";

  const who = document.createElement("td");
  who.textContent = entry.username || "(system)";

  const action = document.createElement("td");
  const badge = document.createElement("span");
  badge.className = "audit-action";
  badge.textContent = actionLabel(entry.action);
  badge.title = entry.action;
  action.appendChild(badge);

  const description = document.createElement("td");
  description.className = "audit-description";
  description.textContent = entry.description;

  row.append(when, who, action, description);
  return row;
}

function updatePager(page, totalPages) {
  $("auditPageInfo").textContent = totalPages > 0 ? `Page ${page + 1} of ${totalPages}` : "";
  $("auditPrevious").disabled = page <= 0;
  $("auditNext").disabled = page + 1 >= totalPages;
}

// PAYROLL_PAID -> "Payroll paid"
function actionLabel(action) {
  const words = String(action || "").toLowerCase().split("_").join(" ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}

function setEmptyRow(body, text) {
  body.innerHTML = '<tr><td colspan="4" class="empty-row"></td></tr>';
  body.querySelector("td").textContent = text;
}

async function logout() {
  try {
    await fetch("/api/auth/logout", { method: "POST" });
  } finally {
    window.location.href = "/";
  }
}

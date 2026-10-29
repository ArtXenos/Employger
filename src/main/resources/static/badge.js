// Attendance badge for one employee: badge.html?employee=5. Administrators
// and HR managers issue, print and reissue it; the server refuses everyone
// else (a badge code is enough to clock in as that employee).

const $ = (id) => document.getElementById(id);

const employeeId = Number(new URLSearchParams(window.location.search).get("employee"));

document.addEventListener("DOMContentLoaded", () => {
  $("printBadgeButton").addEventListener("click", () => window.print());
  $("issueBadgeButton").addEventListener("click", () => issueBadge(false));
  $("reissueBadgeButton").addEventListener("click", () => issueBadge(true));

  if (!Number.isInteger(employeeId) || employeeId <= 0) {
    showError("No employee chosen. Open this page from the Employees list.");
    return;
  }

  loadBadge();
});

async function loadBadge() {
  try {
    const response = await fetch(`/api/employees/${employeeId}/badge`, { credentials: "same-origin" });

    if (!response.ok) {
      showError(await errorText(response));
      return;
    }

    showBadge(await response.json());
  } catch (error) {
    showError("Can't reach the server. Check your connection and try again.");
  }
}

async function issueBadge(replacing) {
  if (replacing && !confirm(
    "Reissue this badge? The current card will stop working immediately, "
    + "and you will need to print and hand over the new one.")) {
    return;
  }

  try {
    const response = await fetch(`/api/employees/${employeeId}/badge`, {
      method: "POST",
      credentials: "same-origin",
    });

    if (!response.ok) {
      showError(await errorText(response));
      return;
    }

    showBadge(await response.json());
    showMessage(replacing
      ? "New badge issued. The old card no longer works. Print this one and hand it over."
      : "Badge issued. Print it and hand it to the employee.");
  } catch (error) {
    showError("Can't reach the server. Check your connection and try again.");
  }
}

function showBadge(badge) {
  const hasBadge = Boolean(badge.badgeCode);

  $("badgeName").textContent = badge.employeeName;
  $("badgeNumber").textContent = `Employee #${badge.employeeId}`;
  $("badgePosition").textContent = [badge.department, badge.jobTitle].filter(Boolean).join(" · ");

  $("badgeCard").hidden = !hasBadge;
  $("badgeNone").hidden = hasBadge;
  $("printBadgeButton").hidden = !hasBadge;
  $("issueBadgeButton").hidden = hasBadge || !badge.employed;
  $("reissueBadgeButton").hidden = !hasBadge || !badge.employed;

  if (hasBadge) {
    $("badgeCode").textContent = badge.badgeCode;
    // The code in the URL only makes the browser fetch the new image after a reissue.
    $("badgeQr").src = `/api/employees/${employeeId}/badge/qr.svg?v=${encodeURIComponent(badge.badgeCode)}`;
  } else {
    $("badgeNone").textContent = badge.employed
      ? `${badge.employeeName} has no badge yet. Issue one to print it.`
      : `${badge.employeeName} no longer works for the company, so no badge can be issued.`;
  }

  if (hasBadge && !badge.employed) {
    showError("This employee no longer works for the company: the kiosk refuses this card.");
  }
}

async function errorText(response) {
  if (response.status === 401) {
    window.location.href = "/";
    return "Please log in.";
  }
  if (response.status === 403) {
    return "Only administrators and HR managers can view or issue attendance badges.";
  }
  return (await response.text()).trim() || "Something went wrong. Please try again.";
}

function showMessage(text) {
  const message = $("badgeMessage");
  message.textContent = text;
  message.className = "form-message report-message success";
}

function showError(text) {
  const message = $("badgeMessage");
  message.textContent = text;
  message.className = "form-message report-message error";
}

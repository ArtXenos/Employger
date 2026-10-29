// Shared by the staff pages. Shows each role only what it may use; the
// server enforces the same rules, so this is about clarity, not security.
//   EMPLOYEE         -> sent to their own payroll page
//   KIOSK            -> sent to the attendance kiosk page
//   GENERAL_MANAGER  -> read-only: write controls hidden, banner shown
//   not ADMINISTRATOR -> Settings, Users and Audit Log links hidden (admin-only)
//   GENERAL_MANAGER  -> Reports link hidden (reports are admin and HR only)
//   HR_MANAGER       -> payroll sign-off buttons hidden (GM or admin approves)
//   ADMINISTRATOR    -> a badge on the Users link counts waiting password resets

(function () {
  const READ_ONLY_CONTROLS = [
    "#addAttendanceButton",
    "#addEmployeeButton",
    "#manageListsButton",
    "#addOvertimeButton",
    "#addPayCycleButton",
    "#generatePayrollButton",
    "#generateAllButton",
    "#addHolidayButton",
    "#addEarningButton",
    "#addPriorButton",
    "#thirteenthForm",
    "#deletePayrollButton",
    ".edit-button",
    ".delete-button",
    ".action-edit",
    ".action-approve",
    ".action-reject",
    ".action-delete",
    ".action-cancel",
    ".process-button",
    ".pay-button",
  ];

  // Applies to rows rendered later as well, because it is a stylesheet rule.
  const style = document.createElement("style");
  style.textContent =
    `html[data-role="GENERAL_MANAGER"] :is(${READ_ONLY_CONTROLS.join(", ")})` +
    " { display: none !important; }" +
    ' html[data-role="HR_MANAGER"] .signoff-button { display: none !important; }' +
    " .read-only-banner { margin: 0 0 16px; padding: 10px 14px; border-radius: 8px;" +
    " background: #eff6ff; color: #1e3a8a; border: 1px solid #bfdbfe; font-size: 14px; }" +
    " .nav-badge { margin-left: auto; min-width: 20px; padding: 1px 7px; border-radius: 999px;" +
    " background: #dc2626; color: #fff; font-size: 12px; font-weight: 700; text-align: center; }";
  document.head.appendChild(style);

  async function applyRole() {
    let user;

    try {
      const response = await fetch("/api/auth/me", { credentials: "same-origin" });

      if (response.status === 401) {
        window.location.href = "/";
        return;
      }

      if (!response.ok) {
        return;
      }

      user = await response.json();
    } catch (error) {
      // The page's own requests will report the connection problem.
      return;
    }

    document.documentElement.dataset.role = user.role;

    if (user.role === "EMPLOYEE") {
      window.location.replace("/my-payroll.html");
      return;
    }

    if (user.role === "KIOSK") {
      window.location.replace("/kiosk.html");
      return;
    }

    if (user.role !== "ADMINISTRATOR") {
      document
        .querySelectorAll('a[href$="settings.html"], a[href$="users.html"], a[href$="audit-log.html"]')
        .forEach((link) => link.remove());
    }

    if (user.role === "ADMINISTRATOR") {
      window.refreshPasswordResetBadge = refreshPasswordResetBadge;
      refreshPasswordResetBadge();
    }

    // Reports list government ID numbers: administrators and HR only.
    if (user.role !== "ADMINISTRATOR" && user.role !== "HR_MANAGER") {
      document
        .querySelectorAll('a[href$="reports.html"]')
        .forEach((link) => link.remove());
    }

    if (user.role === "GENERAL_MANAGER") {
      const main = document.querySelector("main");

      if (main && !main.querySelector(".read-only-banner")) {
        const banner = document.createElement("p");

        banner.className = "read-only-banner";
        banner.setAttribute("role", "note");
        banner.textContent =
          "Read-only access, except sign-off: you can view payroll information " +
          "and approve or send back processed payroll.";

        main.prepend(banner);
      }
    }
  }

  // Forgot-password requests waiting for an administrator: a red count on
  // the Users link, on every page, so they are noticed.
  async function refreshPasswordResetBadge() {
    let pending = 0;

    try {
      const response = await fetch("/api/users/password-resets/count", { credentials: "same-origin" });
      if (response.ok) {
        pending = (await response.json()).pending;
      }
    } catch (error) {
      return;
    }

    const label = `${pending} password reset request${pending === 1 ? "" : "s"} waiting`;

    document.querySelectorAll('a[href$="users.html"]').forEach((link) => {
      link.querySelector(".nav-badge")?.remove();
      link.removeAttribute("title");

      if (pending > 0) {
        const badge = document.createElement("span");
        badge.className = "nav-badge";
        badge.textContent = String(pending);
        badge.setAttribute("aria-label", label);
        link.appendChild(badge);
        link.title = label;
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", applyRole);
  } else {
    applyRole();
  }
})();

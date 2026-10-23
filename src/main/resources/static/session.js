// Shared by the staff pages. Shows each role only what it may use; the
// server enforces the same rules, so this is about clarity, not security.
//   EMPLOYEE         -> sent to their own payroll page
//   GENERAL_MANAGER  -> read-only: write controls hidden, banner shown
//   not ADMINISTRATOR -> Settings link hidden (settings are admin-only)
//   GENERAL_MANAGER  -> Reports link hidden (reports are admin and HR only)

(function () {
  const READ_ONLY_CONTROLS = [
    "#addAttendanceButton",
    "#addEmployeeButton",
    "#addOvertimeButton",
    "#addPayCycleButton",
    "#generatePayrollButton",
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
    ".process-button",
    ".pay-button",
  ];

  // Applies to rows rendered later as well, because it is a stylesheet rule.
  const style = document.createElement("style");
  style.textContent =
    `html[data-role="GENERAL_MANAGER"] :is(${READ_ONLY_CONTROLS.join(", ")})` +
    " { display: none !important; }" +
    " .read-only-banner { margin: 0 0 16px; padding: 10px 14px; border-radius: 8px;" +
    " background: #eff6ff; color: #1e3a8a; border: 1px solid #bfdbfe; font-size: 14px; }";
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

    if (user.role !== "ADMINISTRATOR") {
      document
        .querySelectorAll('a[href$="settings.html"]')
        .forEach((link) => link.remove());
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
          "Read-only access: you can view payroll information but not change it.";

        main.prepend(banner);
      }
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", applyRole);
  } else {
    applyRole();
  }
})();

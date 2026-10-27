// "Change password" for every logged-in user, on every page with a sidebar.
// Adds a button above Logout that opens a small form. Load it after csrf.js
// and before password-toggle.js, so the new fields get show/hide buttons.
//
// After logging in with a temporary password from an administrator, the
// form opens by itself and can't be closed until a new password is chosen.
// The server enforces the same: every other request is refused until then.

(function () {
  const MIN_PASSWORD_LENGTH = 12;

  const logout = document.getElementById("logoutButton");
  if (!logout) {
    return;
  }

  const style = document.createElement("style");
  style.textContent = `
    .account-button { margin-top: auto; }
    .account-button + .logout-button { margin-top: 0; }
    .account-modal { position: fixed; inset: 0; display: none; align-items: center;
      justify-content: center; padding: 20px; background: rgba(15, 23, 42, 0.45); z-index: 1000; }
    .account-modal.show { display: flex; }
    .account-modal-content { width: min(440px, 100%); max-height: calc(100vh - 40px); overflow-y: auto;
      background: #fff; border-radius: 12px; padding: 24px; box-shadow: 0 20px 50px rgba(15, 23, 42, 0.25); }
    .account-modal-header { display: flex; justify-content: space-between; align-items: flex-start;
      gap: 12px; margin-bottom: 18px; }
    .account-modal-header h3 { margin: 0 0 4px; font-size: 18px; color: #0f172a; }
    .account-modal-header p { margin: 0; font-size: 13px; color: #64748b; }
    .account-field { display: grid; gap: 6px; margin-bottom: 14px; }
    .account-field label { font-size: 13px; font-weight: 600; color: #334155; }
    .account-field input { width: 100%; box-sizing: border-box; padding: 10px 12px; font: inherit;
      font-size: 14px; border: 1px solid #cbd5e1; border-radius: 8px; }
    .account-field input:focus { outline: 2px solid #2563eb; outline-offset: 0; border-color: #2563eb; }
    .account-message { min-height: 20px; margin: 4px 0 14px; font-size: 13px; }
    .account-message.error { color: #b91c1c; }
    .account-message.success { color: #047857; }
    .account-actions { display: flex; justify-content: flex-end; gap: 10px; }
  `;
  document.head.appendChild(style);

  const button = document.createElement("button");
  button.type = "button";
  button.id = "changePasswordButton";
  button.className = "logout-button account-button";
  button.textContent = "Change password";
  logout.parentNode.insertBefore(button, logout);

  const modal = document.createElement("div");
  modal.className = "account-modal";
  modal.id = "passwordModal";
  modal.setAttribute("aria-hidden", "true");
  modal.innerHTML = `
    <div class="account-modal-content" role="dialog" aria-modal="true" aria-labelledby="passwordModalTitle">
      <div class="account-modal-header">
        <div>
          <h3 id="passwordModalTitle">Change password</h3>
          <p>At least ${MIN_PASSWORD_LENGTH} characters; a few unrelated words work well.</p>
        </div>
        <button type="button" class="close-button" data-close-password aria-label="Close">&times;</button>
      </div>
      <form id="passwordForm" autocomplete="on" novalidate>
        <input type="text" name="username" autocomplete="username" hidden />
        <div class="account-field">
          <label for="currentPassword" id="currentPasswordLabel">Current password</label>
          <input type="password" id="currentPassword" maxlength="72" autocomplete="current-password" required />
        </div>
        <div class="account-field">
          <label for="newPassword">New password</label>
          <input type="password" id="newPassword" maxlength="72" autocomplete="new-password" required />
        </div>
        <div class="account-field">
          <label for="newPasswordConfirm">Confirm new password</label>
          <input type="password" id="newPasswordConfirm" maxlength="72" autocomplete="new-password" required />
        </div>
        <p class="account-message" id="passwordMessage" role="alert"></p>
        <div class="account-actions">
          <button type="button" class="cancel-button" data-close-password>Cancel</button>
          <button type="submit" class="save-button" id="passwordSubmit">Change password</button>
        </div>
      </form>
    </div>`;
  document.body.appendChild(modal);

  const form = modal.querySelector("#passwordForm");
  const message = modal.querySelector("#passwordMessage");
  let mustChange = false;

  function show(text, kind) {
    message.textContent = text;
    message.className = "account-message" + (kind ? " " + kind : "");
  }

  function open() {
    form.reset();
    show("", null);
    if (window.hidePasswords) {
      window.hidePasswords(modal);
    }
    modal.classList.add("show");
    modal.setAttribute("aria-hidden", "false");
    modal.querySelector("#currentPassword").focus();
  }

  function close() {
    if (mustChange) {
      return;
    }
    modal.classList.remove("show");
    modal.setAttribute("aria-hidden", "true");
    button.focus();
  }

  button.addEventListener("click", open);
  modal.querySelectorAll("[data-close-password]").forEach((el) => el.addEventListener("click", close));
  modal.addEventListener("click", (event) => {
    if (event.target === modal) {
      close();
    }
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && modal.classList.contains("show")) {
      close();
    }
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const currentPassword = modal.querySelector("#currentPassword").value;
    const newPassword = modal.querySelector("#newPassword").value;
    const confirmation = modal.querySelector("#newPasswordConfirm").value;

    if (!currentPassword || !newPassword) {
      show("Enter your current password and a new one.", "error");
      return;
    }
    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      show(`The new password must be at least ${MIN_PASSWORD_LENGTH} characters.`, "error");
      return;
    }
    if (newPassword !== confirmation) {
      show("The two new passwords do not match.", "error");
      return;
    }

    const submit = modal.querySelector("#passwordSubmit");
    submit.disabled = true;

    try {
      const response = await fetch("/api/me/password", {
        method: "PUT",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      const text = (await response.text()).trim();

      if (response.status === 401) {
        window.location.href = "/";
        return;
      }
      if (!response.ok) {
        show(text || "The password could not be changed.", "error");
        return;
      }

      form.reset();
      show(text || "Your password has been changed.", "success");

      if (mustChange) {
        // The rest of the page could not load until now.
        show("Your password has been changed. Loading the page...", "success");
        setTimeout(() => window.location.reload(), 1200);
      }
    } catch (error) {
      show("The server could not be reached. Please try again.", "error");
    } finally {
      submit.disabled = false;
    }
  });

  // A temporary password: open the form and keep it open.
  function requireNewPassword() {
    mustChange = true;
    modal.querySelector("#passwordModalTitle").textContent = "Choose your own password";
    modal.querySelector(".account-modal-header p").textContent =
      "You logged in with a temporary password from an administrator. Choose your own " +
      `password to continue: at least ${MIN_PASSWORD_LENGTH} characters; a few unrelated words work well.`;
    modal.querySelector("#currentPasswordLabel").textContent = "Temporary password";
    modal.querySelector(".close-button").style.display = "none";

    // The sidebar is behind the form, so offer Log out here instead of Cancel.
    const cancel = modal.querySelector(".cancel-button");
    const logOut = cancel.cloneNode(false);
    logOut.removeAttribute("data-close-password");
    logOut.textContent = "Log out";
    logOut.addEventListener("click", async () => {
      try {
        await fetch("/api/auth/logout", { method: "POST", credentials: "same-origin" });
      } finally {
        window.location.href = "/";
      }
    });
    cancel.replaceWith(logOut);

    open();
  }

  fetch("/api/auth/me", { credentials: "same-origin" })
    .then((response) => (response.ok ? response.json() : null))
    .then((user) => {
      if (user && user.mustChangePassword) {
        requireNewPassword();
      }
    })
    .catch(() => {
      // The page's own requests report connection problems.
    });
})();

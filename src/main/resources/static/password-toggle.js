// Show/hide button for every password field on the page. An open eye means
// "click to show"; a crossed-out eye means the password is visible and
// clicking hides it. Fields go back to hidden when their form is submitted,
// and hidePasswords(root) hides them again (e.g. when a modal reopens).

(function () {
  const EYE =
    '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
    '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/></svg>';

  const EYE_OFF =
    '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
    '<path d="M10.6 5.1A10.4 10.4 0 0 1 12 5c6.4 0 10 7 10 7a17.6 17.6 0 0 1-3.2 4.2"/>' +
    '<path d="M6.6 6.6C3.9 8.4 2 12 2 12s3.6 7 10 7c1.9 0 3.6-.6 5-1.5"/>' +
    '<path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/><path d="M2 2l20 20"/></svg>';

  const style = document.createElement("style");
  style.textContent =
    ".password-field { position: relative; display: block; }" +
    " .password-field input { padding-right: 46px !important; }" +
    " .password-toggle { position: absolute; top: 50%; right: 6px; transform: translateY(-50%);" +
    " width: 34px; height: 34px; min-height: 0; padding: 0; margin: 0; display: inline-flex;" +
    " align-items: center; justify-content: center; border: none; border-radius: 6px;" +
    " background: transparent; color: #64748b; cursor: pointer; box-shadow: none; }" +
    " .password-toggle:hover { color: #0f172a; background: #f1f5f9; }" +
    " .password-toggle:focus-visible { outline: 2px solid #2563eb; outline-offset: 1px; }";
  document.head.appendChild(style);

  function setVisible(input, button, visible) {
    input.type = visible ? "text" : "password";
    button.innerHTML = visible ? EYE_OFF : EYE;
    button.setAttribute("aria-pressed", String(visible));
    button.setAttribute("aria-label", visible ? "Hide password" : "Show password");
    button.title = visible ? "Hide password" : "Show password";
  }

  function addToggle(input) {
    if (input.dataset.revealReady) {
      return;
    }

    input.dataset.revealReady = "true";

    const wrapper = document.createElement("span");
    wrapper.className = "password-field";
    input.parentNode.insertBefore(wrapper, input);
    wrapper.appendChild(input);

    const button = document.createElement("button");
    button.type = "button";
    button.className = "password-toggle";
    wrapper.appendChild(button);
    setVisible(input, button, false);

    button.addEventListener("click", () => {
      setVisible(input, button, input.type === "password");
      input.focus();
    });

    if (input.form) {
      input.form.addEventListener("submit", () => setVisible(input, button, false));
    }
  }

  window.hidePasswords = function (root) {
    (root || document).querySelectorAll(".password-field").forEach((wrapper) => {
      setVisible(wrapper.querySelector("input"), wrapper.querySelector(".password-toggle"), false);
    });
  };

  function init() {
    document.querySelectorAll('input[type="password"]').forEach(addToggle);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();

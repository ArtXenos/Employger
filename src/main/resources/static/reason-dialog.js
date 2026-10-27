// A small dialog that asks for a required reason, used for Reject and
// Cancel on overtime and leave. askForReason() resolves to the trimmed
// reason, or null if the user closes it. The server checks the reason
// again, so this is only for convenience.
//
//   const reason = await askForReason({
//     title: "Reject overtime",
//     message: "Tell the employee why.",
//     confirmLabel: "Reject",
//   });

(function () {
  const STYLE =
    ".reason-dialog-backdrop { position: fixed; inset: 0; background: rgba(15, 23, 42, 0.45);" +
    " display: flex; align-items: center; justify-content: center; padding: 16px; z-index: 2000; }" +
    " .reason-dialog { background: #fff; color: #0f172a; border-radius: 12px; width: 100%; max-width: 440px;" +
    " padding: 22px; box-shadow: 0 20px 40px rgba(15, 23, 42, 0.25); font-family: inherit; }" +
    " .reason-dialog h3 { margin: 0 0 6px; font-size: 18px; }" +
    " .reason-dialog p { margin: 0 0 14px; color: #475569; font-size: 14px; line-height: 1.45; }" +
    " .reason-dialog label { display: block; font-weight: 600; font-size: 13px; margin-bottom: 6px; }" +
    " .reason-dialog textarea { width: 100%; box-sizing: border-box; min-height: 90px; padding: 10px;" +
    " border: 1px solid #cbd5e1; border-radius: 8px; font: inherit; resize: vertical; }" +
    " .reason-dialog .reason-count { text-align: right; font-size: 12px; color: #64748b; margin-top: 4px; }" +
    " .reason-dialog .reason-error { color: #b91c1c; font-size: 13px; min-height: 18px; margin: 6px 0 0; }" +
    " .reason-dialog .reason-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 14px; }" +
    " .reason-dialog button { padding: 9px 16px; border-radius: 8px; font: inherit; font-weight: 600; cursor: pointer; }" +
    " .reason-dialog .reason-cancel { background: #fff; border: 1px solid #cbd5e1; color: #0f172a; }" +
    " .reason-dialog .reason-confirm { background: #dc2626; border: 1px solid #dc2626; color: #fff; }";

  const MAX_LENGTH = 255;

  function ensureStyle() {
    if (!document.getElementById("reasonDialogStyle")) {
      const style = document.createElement("style");
      style.id = "reasonDialogStyle";
      style.textContent = STYLE;
      document.head.appendChild(style);
    }
  }

  window.askForReason = function ({ title, message, confirmLabel = "Confirm", backLabel = "Back" }) {
    ensureStyle();

    return new Promise((resolve) => {
      const backdrop = document.createElement("div");
      backdrop.className = "reason-dialog-backdrop";
      backdrop.innerHTML = `
        <div class="reason-dialog" role="dialog" aria-modal="true" aria-labelledby="reasonDialogTitle">
          <h3 id="reasonDialogTitle"></h3>
          <p class="reason-message"></p>
          <label for="reasonDialogText">Reason <span aria-hidden="true">*</span></label>
          <textarea id="reasonDialogText" maxlength="${MAX_LENGTH}" required></textarea>
          <div class="reason-count" aria-live="polite">0 / ${MAX_LENGTH}</div>
          <p class="reason-error" role="alert"></p>
          <div class="reason-actions">
            <button type="button" class="reason-cancel"></button>
            <button type="button" class="reason-confirm"></button>
          </div>
        </div>`;

      backdrop.querySelector("h3").textContent = title;
      backdrop.querySelector(".reason-message").textContent = message || "";
      backdrop.querySelector(".reason-cancel").textContent = backLabel;
      backdrop.querySelector(".reason-confirm").textContent = confirmLabel;

      const text = backdrop.querySelector("textarea");
      const count = backdrop.querySelector(".reason-count");
      const error = backdrop.querySelector(".reason-error");
      const previousFocus = document.activeElement;

      function close(result) {
        document.removeEventListener("keydown", onKey);
        backdrop.remove();
        if (previousFocus && previousFocus.focus) {
          previousFocus.focus();
        }
        resolve(result);
      }

      function confirmReason() {
        const reason = text.value.trim();
        if (!reason) {
          error.textContent = "Please enter the reason.";
          text.focus();
          return;
        }
        close(reason);
      }

      function onKey(event) {
        if (event.key === "Escape") {
          close(null);
        }
      }

      text.addEventListener("input", () => {
        count.textContent = `${text.value.length} / ${MAX_LENGTH}`;
        error.textContent = "";
      });
      backdrop.querySelector(".reason-cancel").addEventListener("click", () => close(null));
      backdrop.querySelector(".reason-confirm").addEventListener("click", confirmReason);
      backdrop.addEventListener("click", (event) => {
        if (event.target === backdrop) {
          close(null);
        }
      });
      document.addEventListener("keydown", onKey);

      document.body.appendChild(backdrop);
      text.focus();
    });
  };
})();

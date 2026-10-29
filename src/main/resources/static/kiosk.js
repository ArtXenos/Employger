// Attendance kiosk: reads badge QR codes from the webcam (jsQR, in the
// browser) and sends only the code to the server. The server decides
// clock-in or clock-out and takes the time from its own clock.
//
// Browsers allow the camera only on localhost or https, so this page is
// opened at http://localhost:8080/kiosk.html on the server computer. The
// typed-code box works everywhere, but the server accepts scans only from
// the kiosk computer anyway.

const RESULT_SHOWN_MS = 4000;
// The same card held in front of the camera is read many times a second.
const SAME_CODE_IGNORED_MS = 10000;
const FRAMES_PER_SECOND = 5;
const FRAME_WIDTH = 640;

const $ = (id) => document.getElementById(id);

let busy = false;
let lastCode = null;
let lastCodeAt = 0;
let resetTimer = null;

document.addEventListener("DOMContentLoaded", async () => {
  startClock();

  $("manualForm").addEventListener("submit", (event) => {
    event.preventDefault();
    const code = $("manualCode").value.trim();
    if (code) {
      $("manualCode").value = "";
      submitCode(code, true);
    }
  });

  $("kioskLogout").addEventListener("click", async () => {
    if (!confirm("Log out of the kiosk? Nobody can clock in until it is logged in again.")) {
      return;
    }
    try {
      await fetch("/api/auth/logout", { method: "POST", credentials: "same-origin" });
    } finally {
      window.location.href = "/";
    }
  });

  if (await isKioskLogin()) {
    startCamera();
  }
});

// ---------- login ----------

async function isKioskLogin() {
  try {
    const response = await fetch("/api/auth/me", { credentials: "same-origin" });

    if (response.status === 401) {
      window.location.href = "/";
      return false;
    }

    const user = await response.json();

    if (user.role !== "KIOSK") {
      $("manualForm").hidden = true;
      showResult("error", "Kiosk login needed",
        "This page is for the kiosk account",
        "Log out, then log in with the kiosk account (ask the administrator).", false);
      return false;
    }

    return true;
  } catch (error) {
    showResult("error", "No connection", "Can't reach the server",
      "Check that Em-Ployger is running, then reload this page.", false);
    return false;
  }
}

// ---------- clock ----------

function startClock() {
  const tick = () => {
    const now = new Date();
    $("kioskClock").textContent = now.toLocaleTimeString("en-PH", { hour: "numeric", minute: "2-digit" });
    $("kioskDate").textContent = now.toLocaleDateString("en-PH", {
      weekday: "long", year: "numeric", month: "long", day: "numeric",
    });
  };
  tick();
  setInterval(tick, 1000);
}

// ---------- camera ----------

async function startCamera() {
  if (!window.isSecureContext || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    cameraProblem("The camera only works when this page is opened as http://localhost:8080/kiosk.html "
      + "on the kiosk computer. You can still type badge codes below.");
    return;
  }

  let stream;
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { width: { ideal: 1280 }, height: { ideal: 720 } },
      audio: false,
    });
  } catch (error) {
    cameraProblem(error && error.name === "NotAllowedError"
      ? "Camera permission was refused. Click the camera icon in the address bar, allow it, then reload."
      : "No camera found, or another program is using it. Connect a webcam or close the other program, then reload.");
    return;
  }

  const video = $("cameraVideo");
  video.srcObject = stream;
  await video.play();

  const canvas = document.createElement("canvas");
  const context = canvas.getContext("2d", { willReadFrequently: true });

  setInterval(() => {
    if (busy || video.readyState < video.HAVE_ENOUGH_DATA || !video.videoWidth) {
      return;
    }

    const scale = Math.min(1, FRAME_WIDTH / video.videoWidth);
    canvas.width = Math.round(video.videoWidth * scale);
    canvas.height = Math.round(video.videoHeight * scale);
    context.drawImage(video, 0, 0, canvas.width, canvas.height);

    const frame = context.getImageData(0, 0, canvas.width, canvas.height);
    const found = jsQR(frame.data, frame.width, frame.height, { inversionAttempts: "dontInvert" });

    if (found && found.data) {
      submitCode(found.data, false);
    }
  }, 1000 / FRAMES_PER_SECOND);
}

function cameraProblem(message) {
  const note = $("cameraNote");
  note.textContent = message;
  note.hidden = false;
}

// ---------- scanning ----------

async function submitCode(code, typed) {
  const now = Date.now();

  if (busy || (!typed && code === lastCode && now - lastCodeAt < SAME_CODE_IGNORED_MS)) {
    return;
  }

  busy = true;
  lastCode = code;
  lastCodeAt = now;
  showResult("idle", "Checking...", "One moment", "", false);

  try {
    const response = await fetch("/api/kiosk/scan", {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ badgeCode: code }),
    });

    if (response.status === 401) {
      window.location.href = "/";
      return;
    }

    if (response.ok) {
      const result = await response.json();
      const clockedIn = result.direction === "IN";
      showResult(clockedIn ? "in" : "out",
        clockedIn ? "Clocked in" : "Clocked out",
        result.employeeName,
        result.message, true);
      return;
    }

    // 404 unknown card, 409 refused (already clocked in, on leave, ...),
    // 403 wrong computer: the server's text says what to do.
    const message = (await response.text()).trim() || "The scan could not be recorded.";
    showResult(response.status === 409 ? "refused" : "error",
      response.status === 409 ? "Not recorded" : "Not accepted",
      response.status === 404 ? "Unknown card" : "Please read this",
      message, true);
  } catch (error) {
    showResult("error", "No connection", "Can't reach the server",
      "Your scan was not recorded. Try again, or tell HR the time you arrived.", true);
  } finally {
    busy = false;
  }
}

function showResult(state, label, name, message, resetAfter) {
  $("resultPanel").dataset.state = state;
  $("resultLabel").textContent = label;
  $("resultName").textContent = name;
  $("resultMessage").textContent = message;

  clearTimeout(resetTimer);

  if (resetAfter) {
    resetTimer = setTimeout(() => {
      showResult("idle", "Ready", "Hold your ID badge up to the camera",
        "The QR code goes inside the square. The first scan of the day clocks you in; the next one clocks you out.",
        false);
    }, RESULT_SHOWN_MS);
  }
}

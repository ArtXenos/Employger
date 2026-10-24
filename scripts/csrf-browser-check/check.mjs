// Real-browser CSRF check. Drives headless Chrome over the DevTools protocol
// (Node 22+, no packages) against a THROWAWAY copy of the app on port 8099
// with an empty in-memory database, so the real employger_db is never used.
//
// 1. Start the throwaway app from the project folder (PowerShell):
//      .\mvnw.cmd -q test-compile dependency:build-classpath "-Dmdep.outputFile=cp.txt" "-Dmdep.includeScope=test"
//      java -cp ("target\classes;" + (Get-Content cp.txt -Raw).Trim()) com.employger.EmPloygerApplication `
//        --server.port=8099 "--spring.datasource.url=jdbc:h2:mem:browsercheck;MODE=MariaDB;DB_CLOSE_DELAY=-1" `
//        --spring.datasource.username=sa --spring.datasource.password= --spring.datasource.driver-class-name=org.h2.Driver `
//        --spring.jpa.hibernate.ddl-auto=create-drop --spring.jpa.defer-datasource-initialization=true `
//        --spring.sql.init.mode=always "--spring.sql.init.data-locations=file:scripts/csrf-browser-check/seed.sql"
// 2. In another window: node scripts/csrf-browser-check/check.mjs
// 3. Stop the app (Ctrl+C) and delete cp.txt.
//
// seed.sql adds a test admin (browser.admin) and general manager
// (browser.gm), both with password browser-check-pass, plus the settings
// payroll needs. They exist only in that in-memory database.
import { spawn } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const APP = "http://localhost:8099";
const CHROME = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const PORT = 9333;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const profile = mkdtempSync(join(tmpdir(), "csrf-check-"));
const chrome = spawn(CHROME, [
  "--headless=new", `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
  "--no-first-run", "--no-default-browser-check", "about:blank",
], { stdio: "ignore" });

let ws, nextId = 0;
const pending = new Map();

async function connect() {
  for (let i = 0; i < 50; i++) {
    try {
      const pages = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
      const page = pages.find((p) => p.type === "page");
      if (page) {
        ws = new WebSocket(page.webSocketDebuggerUrl);
        await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
        ws.onmessage = (e) => {
          const msg = JSON.parse(e.data);
          if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); }
        };
        return;
      }
    } catch { /* not up yet */ }
    await sleep(200);
  }
  throw new Error("Chrome did not start");
}

function send(method, params = {}) {
  const id = ++nextId;
  ws.send(JSON.stringify({ id, method, params }));
  return new Promise((res) => pending.set(id, res));
}

async function evaluate(expression) {
  const r = await send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
  if (r.result.exceptionDetails) throw new Error(JSON.stringify(r.result.exceptionDetails));
  return r.result.result.value;
}

async function goto(url) {
  await send("Page.navigate", { url });
  await waitFor(`document.readyState === "complete" && location.href.startsWith(${JSON.stringify(url.slice(0, 15))})`);
}

async function waitFor(condition, timeout = 10000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    try { if (await evaluate(condition)) return; } catch { /* page navigating */ }
    await sleep(150);
  }
  throw new Error("Timed out waiting for: " + condition);
}

const results = [];
function check(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}  ->  ${detail}`);
}

// Same-origin write that bypasses csrf.js (XMLHttpRequest isn't wrapped).
const rawXhr = (token) => `new Promise((res) => {
  const x = new XMLHttpRequest();
  x.open("POST", "/api/holidays");
  x.setRequestHeader("Content-Type", "application/json");
  ${token ? `x.setRequestHeader("X-XSRF-TOKEN", ${JSON.stringify(token)});` : ""}
  x.onload = () => res(x.status + " " + x.responseText);
  x.send(JSON.stringify({ holidayDate: "2026-12-26", name: "Forged", holidayType: "SPECIAL" }));
})`;

try {
  await connect();
  await send("Page.enable");
  await send("Runtime.enable");

  // 1. Log in through the real form.
  await goto(`${APP}/`);
  await evaluate(`(() => {
    document.getElementById("username").value = "browser.admin";
    document.getElementById("password").value = "browser-check-pass";
    document.querySelector("#loginForm [type=submit]").click();
    return true;
  })()`);
  await waitFor(`!location.pathname.endsWith("/") && document.readyState === "complete"`);
  check("Login through the form (itself a protected POST)", true, await evaluate("location.pathname"));

  // 2. The app's own write path: fetch, wrapped by csrf.js.
  const legit = await evaluate(`fetch("/api/holidays", { method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ holidayDate: "2026-12-25", name: "Christmas Day", holidayType: "REGULAR" }) })
    .then(async (r) => r.status + " " + (await r.text()).slice(0, 60))`);
  check("App write with csrf.js header is accepted", legit.startsWith("200"), legit);

  // 3. Same-origin write without the header.
  const noToken = await evaluate(rawXhr(null));
  check("Write without X-XSRF-TOKEN is refused", noToken.startsWith("403") && noToken.includes("security token"), noToken);

  // 4. Same-origin write with a made-up token.
  const badToken = await evaluate(rawXhr("forged-token-123"));
  check("Write with a forged token is refused", badToken.startsWith("403"), badToken);

  // 5. A page on another site auto-submits a form to the app (classic CSRF).
  const attack = `<form id=f method=POST action="${APP}/api/holidays" enctype="text/plain">` +
    `<input name='{"holidayDate":"2026-12-27","name":"CSRF","holidayType":"SPECIAL","x":"' value='"}'></form>` +
    `<script>document.getElementById("f").submit()</script>`;
  await send("Page.navigate", { url: "data:text/html," + encodeURIComponent(attack) });
  await waitFor(`location.href.startsWith("${APP}")`);
  await sleep(300);
  const attackResult = await evaluate("document.body.innerText.trim()");
  check("Cross-site form post is refused", !attackResult.includes("holidayId"), attackResult.slice(0, 120));

  // 6. Nothing forged was saved.
  await goto(`${APP}/dashboard.html`);
  const names = await evaluate(`fetch("/api/holidays").then((r) => r.json()).then((list) => list.map((h) => h.name))`);
  check("Only the legitimate holiday exists", names.length === 1 && names[0] === "Christmas Day", JSON.stringify(names));
} catch (e) {
  check("Script ran to the end", false, e.message);
} finally {
  chrome.kill();
  const failed = results.filter((r) => !r.ok).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
}

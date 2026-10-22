const loginForm = document.getElementById("loginForm");
const loginButton = document.getElementById("loginButton");
const loginMessage = document.getElementById("loginMessage");

// Give up on a login request that has not answered within this time, so
// the button can never stay stuck on "Logging in...".
const LOGIN_TIMEOUT_MS = 15000;

loginForm.addEventListener("submit", async function (event) {

    event.preventDefault();

    const username =
        document.getElementById("username").value.trim();

    const password =
        document.getElementById("password").value;

    loginButton.disabled = true;
    loginButton.textContent = "Logging in...";

    loginMessage.textContent = "";

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), LOGIN_TIMEOUT_MS);

    let redirecting = false;

    try {

        const response = await fetch("/api/auth/login", {

            method: "POST",

            headers: {
                "Content-Type": "application/json"
            },

            credentials: "same-origin",

            signal: controller.signal,

            body: JSON.stringify({
                username: username,
                password: password
            })

        });

        if (response.ok) {

            showLoginMessage("Login successful. Redirecting...", "#16a34a");

            redirecting = true;

            window.location.href = await landingPageForCurrentUser();

            return;
        }

        showLoginMessage(await describeLoginFailure(response), "#dc2626");

    } catch (error) {

        console.error("Login error:", error);

        if (error.name === "AbortError") {
            showLoginMessage(
                "The server took too long to respond. Please try again.",
                "#dc2626");
        } else {
            showLoginMessage(
                "Unable to connect to the server. Check that Em-Ployger is running.",
                "#dc2626");
        }

    } finally {

        clearTimeout(timeoutId);

        if (!redirecting) {
            loginButton.disabled = false;
            loginButton.textContent = "Login";
        }
    }

});

// Employees go to their own payroll page; staff roles to the dashboard.
async function landingPageForCurrentUser() {

    try {
        const response = await fetch("/api/auth/me", { credentials: "same-origin" });

        if (response.ok) {
            const user = await response.json();

            if (user.role === "EMPLOYEE") {
                return "my-payroll.html";
            }
        }
    } catch (error) {
        // Fall through to the dashboard, which routes by role again.
    }

    return "dashboard.html";
}

async function describeLoginFailure(response) {

    // 401 and 503 carry a short, user-safe plain-text message from the server.
    if (response.status === 401 || response.status === 503) {
        const message = (await response.text()).trim();

        if (message) {
            return message;
        }
    }

    if (response.status === 401) {
        return "Invalid username or password.";
    }

    if (response.status === 503) {
        return "Login is temporarily unavailable. Please try again later.";
    }

    return "Login failed because of a server error (HTTP "
        + response.status + "). Please try again or contact the administrator.";
}

function showLoginMessage(text, color) {
    loginMessage.textContent = text;
    loginMessage.style.color = color;
}

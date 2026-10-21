const loginForm = document.getElementById("loginForm");
const loginButton = document.getElementById("loginButton");
const loginMessage = document.getElementById("loginMessage");

loginForm.addEventListener("submit", async function (event) {

    event.preventDefault();

    const username =
        document.getElementById("username").value.trim();

    const password =
        document.getElementById("password").value;

    loginButton.disabled = true;
    loginButton.textContent = "Logging in...";

    loginMessage.textContent = "";

    try {

        const response = await fetch("/api/auth/login", {

            method: "POST",

            headers: {
                "Content-Type": "application/json"
            },

            credentials: "same-origin",

            body: JSON.stringify({
                username: username,
                password: password
            })

        });

        const message = await response.text();

        if (response.ok) {

            loginMessage.textContent =
                "Login successful. Redirecting...";

            loginMessage.style.color = "#16a34a";

            window.location.href = "dashboard.html";

        } else {

            loginMessage.textContent =
                message || "Invalid username or password.";

            loginMessage.style.color = "#dc2626";

            loginButton.disabled = false;
            loginButton.textContent = "Login";
        }

    } catch (error) {

        console.error("Login error:", error);

        loginMessage.textContent =
            "Unable to connect to the server.";

        loginMessage.style.color = "#dc2626";

        loginButton.disabled = false;
        loginButton.textContent = "Login";
    }

});
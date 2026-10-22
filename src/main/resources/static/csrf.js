// CSRF protection for every page. Load this before any other script.
//
// The server puts a random token in the XSRF-TOKEN cookie. Another website
// can make the browser send that cookie, but it cannot read it, so it can't
// copy it into the X-XSRF-TOKEN header. The server rejects any POST, PUT,
// PATCH or DELETE without a matching header.
//
// This wraps window.fetch so every same-origin write request carries the
// header. Page scripts call fetch exactly as before.

(function () {

    const SAFE_METHODS = ["GET", "HEAD", "OPTIONS", "TRACE"];
    const originalFetch = window.fetch.bind(window);

    function readTokenCookie() {
        const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
        return match ? decodeURIComponent(match[1]) : null;
    }

    async function currentToken() {
        let token = readTokenCookie();

        if (!token) {
            // First write request on this page: ask the server to issue a token.
            await originalFetch("/api/auth/csrf", { credentials: "same-origin" });
            token = readTokenCookie();
        }

        return token;
    }

    window.fetch = async function (input, init) {
        const request = new Request(input, init);

        const sameOrigin = new URL(request.url).origin === window.location.origin;
        const writes = !SAFE_METHODS.includes(request.method.toUpperCase());

        if (!sameOrigin || !writes) {
            return originalFetch(input, init);
        }

        const token = await currentToken();

        if (token) {
            const headers = new Headers(init && init.headers
                ? init.headers
                : (input instanceof Request ? input.headers : undefined));
            headers.set("X-XSRF-TOKEN", token);
            init = Object.assign({}, init, { headers: headers });
        }

        return originalFetch(input, init);
    };
})();

package com.employger.config;

import com.employger.entity.User;
import com.employger.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * A login with a temporary password (set by an administrator after a
 * forgot-password request) may only choose a new password. Every other
 * API answers 403 until it has, so the temporary password can't be used
 * for anything else even if the page's prompt is bypassed.
 *
 * Like ActiveAccountFilter, it inherits "once per request" from
 * OncePerRequestFilter. It overrides shouldNotFilter() to say which
 * requests are always allowed, and doFilterInternal() for the check.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    public static final String MESSAGE =
            "You are using a temporary password. Choose your own password before continuing.";

    private static final Logger log = LoggerFactory.getLogger(PasswordChangeRequiredFilter.class);

    private final UserRepository userRepository;

    public PasswordChangeRequiredFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Pages, login/logout/current user, and the password change itself.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());

        return !path.startsWith("/api/")
                || path.startsWith("/api/auth/")
                || (path.equals("/api/me/password") && "PUT".equals(request.getMethod()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (mustChangePassword()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write(MESSAGE);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean mustChangePassword() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }

        try {
            return userRepository.findByUsername(authentication.getName())
                    .map(User::isMustChangePassword)
                    .orElse(false);

        } catch (DataAccessException e) {
            // The database is down: the API itself will report that.
            log.warn("Could not check whether '{}' must change their password", authentication.getName(), e);
            return false;
        }
    }
}

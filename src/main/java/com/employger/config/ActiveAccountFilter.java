package com.employger.config;

import com.employger.entity.User;
import com.employger.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ends a session as soon as its login is deactivated, deleted or given a
 * different role. Without this, a session opened before the change kept
 * working until it expired.
 *
 * Inherits the "run once per request" behaviour from OncePerRequestFilter
 * and supplies only the check itself.
 */
public class ActiveAccountFilter extends OncePerRequestFilter {

    // Set at login. The ID, not the username, so renaming your own account
    // doesn't look like a deleted one.
    public static final String USER_ID_ATTRIBUTE = "EMPLOYGER_USER_ID";

    private static final Logger log = LoggerFactory.getLogger(ActiveAccountFilter.class);

    private final UserRepository userRepository;

    public ActiveAccountFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session != null
                && session.getAttribute(USER_ID_ATTRIBUTE) instanceof Integer userId
                && !stillAllowed(userId)) {

            // The request continues as a visitor, so protected pages answer
            // 401 "Please log in." and the browser goes to the login page.
            session.invalidate();
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private boolean stillAllowed(Integer userId) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return true;
        }

        try {
            return userRepository.findById(userId)
                    .filter(user -> user.getStatus() == User.Status.ACTIVE)
                    .filter(user -> hasRole(authentication, user.getRole()))
                    .isPresent();

        } catch (DataAccessException e) {
            // The database is down: the APIs report that themselves, so
            // don't log anyone out over it.
            log.warn("Could not check whether login #{} is still active", userId, e);
            return true;
        }
    }

    private static boolean hasRole(Authentication authentication, User.Role role) {
        String expected = "ROLE_" + role.name();

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> expected.equals(authority.getAuthority()));
    }
}

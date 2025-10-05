package co.grtk.stableips.service.validation;

import co.grtk.stableips.exception.AuthenticationException;
import co.grtk.stableips.exception.ValidationException;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for validating authentication-related operations.
 *
 * <p>This service centralizes all validation logic for authentication, including
 * username validation and session validation.</p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
@Service
public class AuthValidationService {

    private static final Logger log = LoggerFactory.getLogger(AuthValidationService.class);

    private static final int MIN_USERNAME_LENGTH = 1;
    private static final int MAX_USERNAME_LENGTH = 50;

    /**
     * Validates username format.
     *
     * <p>Ensures the username is not null, not empty, and within acceptable length limits.</p>
     *
     * @param username the username to validate
     * @throws ValidationException if the username is invalid
     */
    public void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            log.warn("Auth validation failed: empty username");
            throw new ValidationException("Username is required");
        }

        String trimmedUsername = username.trim();

        if (trimmedUsername.length() < MIN_USERNAME_LENGTH) {
            log.warn("Auth validation failed: username too short: {}", trimmedUsername.length());
            throw new ValidationException("Username must be at least " + MIN_USERNAME_LENGTH + " character(s)");
        }

        if (trimmedUsername.length() > MAX_USERNAME_LENGTH) {
            log.warn("Auth validation failed: username too long: {}", trimmedUsername.length());
            throw new ValidationException("Username must not exceed " + MAX_USERNAME_LENGTH + " characters");
        }
    }

    /**
     * Validates that a session is authenticated.
     *
     * <p>Checks if the session contains a valid user ID attribute.</p>
     *
     * @param session the HTTP session to validate
     * @throws AuthenticationException if the session is not authenticated
     */
    public void validateAuthenticated(HttpSession session) {
        if (session == null || session.getAttribute("userId") == null) {
            log.warn("Authentication validation failed: no authenticated session");
            throw new AuthenticationException("User not authenticated");
        }
    }

    /**
     * Checks if a session is authenticated without throwing an exception.
     *
     * @param session the HTTP session to check
     * @return true if authenticated, false otherwise
     */
    public boolean isAuthenticated(HttpSession session) {
        return session != null && session.getAttribute("userId") != null;
    }
}

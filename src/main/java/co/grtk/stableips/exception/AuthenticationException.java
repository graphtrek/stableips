package co.grtk.stableips.exception;

/**
 * Exception thrown when authentication fails or user is not authenticated.
 *
 * <p>This exception indicates that a user attempted to access a protected
 * resource without proper authentication or with invalid credentials.</p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
public class AuthenticationException extends RuntimeException {

    /**
     * Constructs a new authentication exception with the specified detail message.
     *
     * @param message the detail message explaining the authentication failure
     */
    public AuthenticationException(String message) {
        super(message);
    }

    /**
     * Constructs a new authentication exception with the specified detail message and cause.
     *
     * @param message the detail message explaining the authentication failure
     * @param cause the cause of this exception
     */
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}

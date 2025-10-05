package co.grtk.stableips.exception;

/**
 * Base exception for validation errors.
 *
 * <p>This exception is thrown when input validation fails, such as invalid
 * amounts, addresses, or missing required fields.</p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
public class ValidationException extends RuntimeException {

    /**
     * Constructs a new validation exception with the specified detail message.
     *
     * @param message the detail message explaining the validation failure
     */
    public ValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a new validation exception with the specified detail message and cause.
     *
     * @param message the detail message explaining the validation failure
     * @param cause the cause of this exception
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

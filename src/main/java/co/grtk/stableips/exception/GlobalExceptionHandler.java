package co.grtk.stableips.exception;

import gg.jte.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler for the application.
 *
 * <p>This controller advice handles all exceptions thrown by controllers
 * and provides consistent error responses. It supports both full-page
 * redirects and HTMX fragment responses for dynamic UI updates.</p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
@ControllerAdvice
@Component
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final TemplateEngine templateEngine;

    /**
     * Constructs a GlobalExceptionHandler with template engine for rendering error fragments.
     *
     * @param templateEngine JTE template engine for rendering HTML fragments
     */
    public GlobalExceptionHandler(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * Handles validation exceptions.
     *
     * <p>Returns a redirect to the wallet page with the validation error message.</p>
     *
     * @param ex the validation exception
     * @return redirect to wallet with error parameter
     */
    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ModelAndView handleValidationException(ValidationException ex) {
        log.warn("Validation error: {}", ex.getMessage());

        ModelAndView mav = new ModelAndView("redirect:/wallet");
        mav.addObject("error", ex.getMessage());
        return mav;
    }

    /**
     * Handles authentication exceptions.
     *
     * <p>Returns a redirect to the login page.</p>
     *
     * @param ex the authentication exception
     * @return redirect to login page
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication error: {}", ex.getMessage());
        return "redirect:/login";
    }

    /**
     * Handles insufficient balance exceptions.
     *
     * <p>Returns a redirect to the wallet page with a balance-specific error message.</p>
     *
     * @param ex the insufficient balance exception
     * @return redirect to wallet with error parameter
     */
    @ExceptionHandler(InsufficientBalanceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ModelAndView handleInsufficientBalanceException(InsufficientBalanceException ex) {
        log.warn("Insufficient balance error: {}", ex.getMessage());

        String errorMessage = ex.getMessage();
        if (ex.getAsset() != null && ex.getAvailable() != null && ex.getRequired() != null) {
            errorMessage = String.format("Insufficient %s balance. Available: %s, Required: %s",
                ex.getAsset(), ex.getAvailable(), ex.getRequired());
        }

        ModelAndView mav = new ModelAndView("redirect:/wallet");
        mav.addObject("error", errorMessage);
        return mav;
    }

    /**
     * Handles blockchain exceptions.
     *
     * <p>Converts technical blockchain errors into user-friendly messages
     * based on the error type.</p>
     *
     * @param ex the blockchain exception
     * @return redirect to wallet with user-friendly error message
     */
    @ExceptionHandler(BlockchainException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView handleBlockchainException(BlockchainException ex) {
        log.error("Blockchain error: {}", ex.getMessage(), ex);

        String userMessage = switch (ex.getErrorType()) {
            case GAS_ESTIMATION_FAILED ->
                "Gas estimation failed - please ensure you have enough ETH for gas fees";
            case GAS_PRICE_TOO_HIGH ->
                "Gas price too high - please try again later";
            case TRANSACTION_REVERTED ->
                "Transaction would fail - please check token approval and balance";
            case NONCE_ERROR ->
                "Transaction ordering issue - please try again";
            case NETWORK_TIMEOUT ->
                "Network timeout - please check your connection and try again";
            case INVALID_ADDRESS ->
                "Invalid recipient address";
            case CONTRACT_ERROR ->
                "Smart contract error - please verify the token contract is deployed";
            default ->
                "Blockchain transaction failed: " + ex.getMessage();
        };

        ModelAndView mav = new ModelAndView("redirect:/wallet");
        mav.addObject("error", userMessage);
        return mav;
    }

    /**
     * Handles IllegalArgumentException (typically from service layer).
     *
     * <p>Returns a redirect to the wallet page with the error message.</p>
     *
     * @param ex the illegal argument exception
     * @return redirect to wallet with error parameter
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ModelAndView handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid argument error: {}", ex.getMessage());

        ModelAndView mav = new ModelAndView("redirect:/wallet");
        mav.addObject("error", ex.getMessage());
        return mav;
    }

    /**
     * Handles IllegalStateException (typically configuration errors).
     *
     * <p>Returns a redirect to the wallet page with a configuration error message.</p>
     *
     * @param ex the illegal state exception
     * @return redirect to wallet with error parameter
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView handleIllegalStateException(IllegalStateException ex) {
        log.error("Service configuration error: {}", ex.getMessage());

        ModelAndView mav = new ModelAndView("redirect:/wallet");
        mav.addObject("error", "Service configuration error: " + ex.getMessage());
        return mav;
    }

    /**
     * Handles missing resource exceptions (404 errors for static resources).
     *
     * <p>Silently returns 404 for common resources like health checks, favicons, etc.
     * Prevents log pollution from monitoring tools and browsers requesting non-existent resources.</p>
     *
     * @param ex the resource not found exception
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleResourceNotFoundException(Exception ex) {
        // Log at DEBUG level only to avoid log pollution
        log.debug("Resource not found: {}", ex.getMessage());
        // Return 404 status without any body or redirect
    }

    /**
     * Handles all other unexpected exceptions.
     *
     * <p>Filters out common static resource errors and logs the full stack trace
     * for genuine application errors.</p>
     *
     * @param ex the unexpected exception
     * @return redirect to wallet with generic error message
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView handleGenericException(Exception ex) {
        String message = ex.getMessage();

        // Filter out common static resource errors to avoid log pollution
        if (message != null && (message.contains("No static resource") ||
            message.contains("favicon.ico") ||
            message.contains("/health") ||
            message.contains("/actuator"))) {
            log.debug("Static resource not found: {}", message);
            ModelAndView mav = new ModelAndView();
            mav.setStatus(HttpStatus.NOT_FOUND);
            return mav;
        }

        log.error("Unexpected error: {}", message, ex);

        ModelAndView mav = new ModelAndView("redirect:/wallet");
        mav.addObject("error", "An unexpected error occurred. Please try again.");
        return mav;
    }
}

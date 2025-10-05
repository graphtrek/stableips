package co.grtk.stableips.controller;

import co.grtk.stableips.service.AuthService;
import co.grtk.stableips.service.validation.AuthValidationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for authentication operations.
 *
 * <p>Handles user login and logout. Validation logic is delegated to
 * {@link AuthValidationService}, and exceptions are handled by
 * {@link co.grtk.stableips.exception.GlobalExceptionHandler}.</p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
@Controller
public class AuthController {

    private final AuthService authService;
    private final AuthValidationService authValidationService;

    /**
     * Constructs an AuthController with required service dependencies.
     *
     * @param authService service for authentication operations
     * @param authValidationService service for validating authentication inputs
     */
    public AuthController(AuthService authService, AuthValidationService authValidationService) {
        this.authService = authService;
        this.authValidationService = authValidationService;
    }

    /**
     * Displays the login page.
     *
     * @return the login view name
     */
    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }

    /**
     * Handles user login.
     *
     * <p>Validates the username using {@link AuthValidationService} and delegates
     * authentication to {@link AuthService}. If validation fails, a
     * {@link co.grtk.stableips.exception.ValidationException} is thrown and handled
     * by the global exception handler.</p>
     *
     * @param username the username submitted from the login form
     * @param session the HTTP session to store authentication state
     * @return redirect to wallet dashboard on success
     */
    @PostMapping("/login")
    public String login(@RequestParam String username, HttpSession session) {
        authValidationService.validateUsername(username);
        authService.login(username.trim(), session);
        return "redirect:/wallet";
    }

    /**
     * Handles user logout.
     *
     * <p>Clears the user session and redirects to the login page.</p>
     *
     * @param session the HTTP session to clear
     * @return redirect to login page
     */
    @PostMapping("/logout")
    public String logout(HttpSession session) {
        authService.logout(session);
        return "redirect:/login";
    }
}

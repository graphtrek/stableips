package co.grtk.stableips.controller;

import co.grtk.stableips.model.dto.UserDto;
import co.grtk.stableips.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API controller for user-related endpoints.
 *
 * <p>This controller provides JSON REST APIs for user management operations.
 * It is separate from the WalletController to maintain a clear separation between
 * HTML-rendering endpoints and JSON API endpoints.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>JSON-based responses for frontend JavaScript consumption</li>
 *   <li>User list retrieval for dropdown population</li>
 *   <li>Multi-chain address support (Ethereum, XRP, Solana)</li>
 * </ul>
 *
 * <p>All endpoints require user authentication via session.</p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 * @see AuthService
 * @see UserDto
 */
@RestController
@RequestMapping("/api")
public class UserApiController {

    private final AuthService authService;

    /**
     * Constructs a UserApiController with required service dependencies.
     *
     * @param authService service for session authentication and user management
     */
    public UserApiController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Retrieves all users except the current authenticated user as JSON.
     *
     * <p>This endpoint provides a JSON list of users for populating recipient dropdowns
     * in transfer forms. It supports multi-chain transfers by including wallet addresses
     * for Ethereum (USDC/EURC), XRP Ledger (XRP), and Solana (SOL).</p>
     *
     * <p>The returned user list:</p>
     * <ul>
     *   <li>Excludes the current authenticated user (prevents self-transfers)</li>
     *   <li>Is sorted alphabetically by username</li>
     *   <li>Contains only safe, displayable user information (no private keys)</li>
     *   <li>Includes username and all blockchain addresses</li>
     * </ul>
     *
     * <p>Example JSON response:</p>
     * <pre>
     * [
     *   {
     *     "id": 1,
     *     "username": "alice",
     *     "walletAddress": "0x123...abc",
     *     "xrpAddress": "rN7n7otQDd6FczFgLdlqtyMVrn3...",
     *     "solanaPublicKey": "5FHwkrdxnt..."
     *   },
     *   {
     *     "id": 2,
     *     "username": "bob",
     *     "walletAddress": "0x456...def",
     *     "xrpAddress": "rU6K7V3Po8w9sxfYAJoKq...",
     *     "solanaPublicKey": "9xQeWvG816..."
     *   }
     * ]
     * </pre>
     *
     * <p>Requires authentication. Unauthenticated users receive 401 Unauthorized.</p>
     *
     * @param session the HTTP session containing user authentication state
     * @return ResponseEntity with list of UserDto objects or 401 if not authenticated
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getAllUsers(HttpSession session) {
        if (!authService.isAuthenticated(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            List<UserDto> users = authService.getAllUsersExceptCurrent(session);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

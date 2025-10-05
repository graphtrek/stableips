package co.grtk.stableips.controller;

import co.grtk.stableips.exception.BlockchainException;
import co.grtk.stableips.exception.InsufficientBalanceException;
import co.grtk.stableips.model.Transaction;
import co.grtk.stableips.model.User;
import co.grtk.stableips.service.AuthService;
import co.grtk.stableips.service.TransactionService;
import co.grtk.stableips.service.validation.AuthValidationService;
import co.grtk.stableips.service.validation.TransferValidationService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * Controller for handling cryptocurrency transfers.
 *
 * <p>Handles transfer initiation for multiple tokens across different blockchain networks.
 * Validation logic is delegated to {@link TransferValidationService} and
 * {@link AuthValidationService}, and exceptions are handled by
 * {@link co.grtk.stableips.exception.GlobalExceptionHandler}.</p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
@Controller
@RequestMapping("/transfer")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final AuthService authService;
    private final TransactionService transactionService;
    private final AuthValidationService authValidationService;
    private final TransferValidationService transferValidationService;

    /**
     * Constructs a TransferController with required service dependencies.
     *
     * @param authService service for authentication operations
     * @param transactionService service for transaction management
     * @param authValidationService service for authentication validation
     * @param transferValidationService service for transfer validation
     */
    public TransferController(
        AuthService authService,
        TransactionService transactionService,
        AuthValidationService authValidationService,
        TransferValidationService transferValidationService
    ) {
        this.authService = authService;
        this.transactionService = transactionService;
        this.authValidationService = authValidationService;
        this.transferValidationService = transferValidationService;
    }

    /**
     * Initiates a cryptocurrency transfer.
     *
     * <p>Validates the transfer request using {@link TransferValidationService} and
     * {@link AuthValidationService}, then delegates to {@link TransactionService} for
     * execution. All exceptions are handled by {@link co.grtk.stableips.exception.GlobalExceptionHandler}.</p>
     *
     * @param recipient the recipient wallet address
     * @param amount the amount to transfer
     * @param token the token type (USDC, DAI, ETH, XRP, SOL)
     * @param network the blockchain network (optional, inferred from token)
     * @param session the HTTP session containing user authentication
     * @param model Spring MVC model for passing data to the view
     * @return redirect to wallet dashboard with success or error parameters
     */
    @PostMapping
    public String initiateTransfer(
        @RequestParam String recipient,
        @RequestParam BigDecimal amount,
        @RequestParam String token,
        @RequestParam(required = false) String network,
        HttpSession session,
        Model model
    ) {
        // Validate authentication
        authValidationService.validateAuthenticated(session);

        // Validate transfer request
        transferValidationService.validateTransferRequest(recipient, amount, token);

        // Get authenticated user
        User user = authService.getCurrentUser(session);
        log.info("Processing transfer request: user={}, token={}, amount={}, recipient={}",
            user.getUsername(), token, amount, recipient);

        // Execute transfer (exceptions handled by GlobalExceptionHandler)
        Transaction tx = transactionService.initiateTransfer(user, recipient, amount, token);

        log.info("Transfer initiated successfully: user={}, txHash={}, token={}, amount={}",
            user.getUsername(), tx.getTxHash(), token, amount);

        return "redirect:/wallet?success=true&txHash=" + tx.getTxHash();
    }

}

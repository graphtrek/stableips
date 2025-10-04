package co.grtk.stableips.controller;

import co.grtk.stableips.model.Transaction;
import co.grtk.stableips.model.User;
import co.grtk.stableips.service.AuthService;
import co.grtk.stableips.service.TransactionService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.web3j.crypto.WalletUtils;

import java.math.BigDecimal;

@Controller
@RequestMapping("/transfer")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final AuthService authService;
    private final TransactionService transactionService;

    public TransferController(AuthService authService, TransactionService transactionService) {
        this.authService = authService;
        this.transactionService = transactionService;
    }

    @PostMapping
    public String initiateTransfer(
        @RequestParam String recipient,
        @RequestParam BigDecimal amount,
        @RequestParam String token,
        @RequestParam(required = false) String network,
        HttpSession session,
        Model model
    ) {
        if (!authService.isAuthenticated(session)) {
            log.warn("Unauthorized transfer attempt - user not authenticated");
            return "redirect:/login";
        }

        // Validate inputs
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Invalid transfer amount: {}", amount);
            return "redirect:/wallet?error=" + encodeMessage("Invalid amount - must be greater than 0");
        }

        if (recipient == null || recipient.trim().isEmpty()) {
            log.warn("Invalid transfer - empty recipient address");
            return "redirect:/wallet?error=" + encodeMessage("Recipient address is required");
        }

        // Validate recipient address format (for Ethereum-based tokens)
        if (!token.equalsIgnoreCase("XRP") && !token.equalsIgnoreCase("SOL")) {
            if (!WalletUtils.isValidAddress(recipient)) {
                log.warn("Invalid Ethereum address format: {}", recipient);
                return "redirect:/wallet?error=" + encodeMessage("Invalid recipient address format");
            }
        }

        try {
            User user = authService.getCurrentUser(session);
            log.info("Processing transfer request: user={}, token={}, amount={}, recipient={}",
                user.getUsername(), token, amount, recipient);

            Transaction tx = transactionService.initiateTransfer(user, recipient, amount, token);

            log.info("Transfer initiated successfully: user={}, txHash={}, token={}, amount={}",
                user.getUsername(), tx.getTxHash(), token, amount);

            return "redirect:/wallet?success=true&txHash=" + tx.getTxHash();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid transfer request from user {}: {}",
                session.getAttribute("username"), e.getMessage());
            return "redirect:/wallet?error=" + encodeMessage(e.getMessage());

        } catch (IllegalStateException e) {
            log.error("Transfer configuration error for user {}: {}",
                session.getAttribute("username"), e.getMessage());
            return "redirect:/wallet?error=" + encodeMessage("Service configuration error: " + e.getMessage());

        } catch (RuntimeException e) {
            log.error("Transfer failed for user {}: {}",
                session.getAttribute("username"), e.getMessage(), e);
            String userMessage = extractUserFriendlyError(e);
            return "redirect:/wallet?error=" + encodeMessage(userMessage);

        } catch (Exception e) {
            log.error("Unexpected error during transfer for user {}: {}",
                session.getAttribute("username"), e.getMessage(), e);
            return "redirect:/wallet?error=" + encodeMessage("An unexpected error occurred. Please try again.");
        }
    }

    /**
     * Extract user-friendly error messages from exceptions
     */
    private String extractUserFriendlyError(RuntimeException e) {
        String msg = e.getMessage().toLowerCase();

        if (msg.contains("insufficient")) {
            return "Insufficient balance";
        }
        if (msg.contains("gas") && msg.contains("low")) {
            return "Gas estimation failed - please ensure you have enough ETH for gas fees";
        }
        if (msg.contains("gas") && msg.contains("high")) {
            return "Gas price too high - please try again later";
        }
        if (msg.contains("revert")) {
            return "Transaction would fail - please check token approval and balance";
        }
        if (msg.contains("nonce")) {
            return "Transaction ordering issue - please try again";
        }
        if (msg.contains("replacement")) {
            return "Transaction replacement error - please wait and try again";
        }
        if (msg.contains("timeout") || msg.contains("timed out")) {
            return "Network timeout - please check your connection and try again";
        }
        if (msg.contains("invalid address")) {
            return "Invalid recipient address";
        }
        if (msg.contains("contract")) {
            return "Smart contract error - please verify the token contract is deployed";
        }

        // Generic fallback
        return "Transfer failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
    }

    /**
     * URL encode error messages for safe redirection
     */
    private String encodeMessage(String message) {
        try {
            return java.net.URLEncoder.encode(message, "UTF-8");
        } catch (Exception e) {
            return "error";
        }
    }
}

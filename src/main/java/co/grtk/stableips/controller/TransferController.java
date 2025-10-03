package co.grtk.stableips.controller;

import co.grtk.stableips.model.User;
import co.grtk.stableips.service.AuthService;
import co.grtk.stableips.service.TransactionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@Controller
@RequestMapping("/transfer")
public class TransferController {

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
        HttpSession session
    ) {
        if (!authService.isAuthenticated(session)) {
            return "redirect:/login";
        }

        try {
            User user = authService.getCurrentUser(session);
            transactionService.initiateTransfer(user, recipient, amount, token);
            return "redirect:/wallet?success=true";
        } catch (Exception e) {
            return "redirect:/wallet?error=true";
        }
    }
}

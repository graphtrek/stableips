package co.grtk.stableips.controller;

import co.grtk.stableips.model.User;
import co.grtk.stableips.service.AuthService;
import co.grtk.stableips.service.TransactionService;
import co.grtk.stableips.service.WalletService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;

@Controller
@RequestMapping("/wallet")
public class WalletController {

    private final AuthService authService;
    private final WalletService walletService;
    private final TransactionService transactionService;

    public WalletController(
        AuthService authService,
        WalletService walletService,
        TransactionService transactionService
    ) {
        this.authService = authService;
        this.walletService = walletService;
        this.transactionService = transactionService;
    }

    @GetMapping
    public String dashboard(HttpSession session, Model model) {
        if (!authService.isAuthenticated(session)) {
            return "redirect:/login";
        }

        User user = authService.getCurrentUser(session);
        String walletAddress = user.getWalletAddress();

        BigDecimal ethBalance = walletService.getEthBalance(walletAddress);
        BigDecimal usdcBalance = transactionService.getTokenBalance(walletAddress, "USDC");
        BigDecimal daiBalance = transactionService.getTokenBalance(walletAddress, "DAI");

        model.addAttribute("user", user);
        model.addAttribute("ethBalance", ethBalance);
        model.addAttribute("usdcBalance", usdcBalance);
        model.addAttribute("daiBalance", daiBalance);
        model.addAttribute("transactions", transactionService.getUserTransactions(user.getId()));

        return "wallet/dashboard";
    }
}

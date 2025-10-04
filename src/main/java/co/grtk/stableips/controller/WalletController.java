package co.grtk.stableips.controller;

import co.grtk.stableips.model.User;
import co.grtk.stableips.service.AuthService;
import co.grtk.stableips.service.TransactionService;
import co.grtk.stableips.service.WalletService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.Map;

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
        String xrpAddress = user.getXrpAddress();
        String solanaPublicKey = user.getSolanaPublicKey();

        BigDecimal ethBalance = walletService.getEthBalance(walletAddress);
        BigDecimal usdcBalance = transactionService.getTokenBalance(walletAddress, "USDC");
        BigDecimal daiBalance = transactionService.getTokenBalance(walletAddress, "DAI");
        BigDecimal xrpBalance = walletService.getXrpBalance(xrpAddress);
        BigDecimal solBalance = walletService.getSolanaBalance(solanaPublicKey);

        model.addAttribute("user", user);
        model.addAttribute("ethBalance", ethBalance);
        model.addAttribute("usdcBalance", usdcBalance);
        model.addAttribute("daiBalance", daiBalance);
        model.addAttribute("xrpBalance", xrpBalance);
        model.addAttribute("solBalance", solBalance);
        model.addAttribute("transactions", transactionService.getUserTransactions(user.getId()));

        return "wallet/dashboard";
    }

    @PostMapping("/fund")
    @ResponseBody
    public String fundWallet(HttpSession session) {
        if (!authService.isAuthenticated(session)) {
            return "<div class='alert alert-danger'>Not authenticated</div>";
        }

        try {
            User user = authService.getCurrentUser(session);
            Map<String, String> txHashes = walletService.fundTestTokens(user.getWalletAddress());

            return String.format(
                """
                <div class='alert alert-success'>
                    <strong>Success!</strong> Wallet funded with test tokens.
                    <br>USDC TX: <a href='https://sepolia.etherscan.io/tx/%s' target='_blank'>%s</a>
                    <br>DAI TX: <a href='https://sepolia.etherscan.io/tx/%s' target='_blank'>%s</a>
                    <br><small>Refresh page to see updated balances</small>
                </div>
                """,
                txHashes.get("usdc"), txHashes.get("usdc").substring(0, 10) + "...",
                txHashes.get("dai"), txHashes.get("dai").substring(0, 10) + "..."
            );
        } catch (IllegalStateException e) {
            return String.format(
                "<div class='alert alert-warning'><strong>Configuration Required:</strong> %s</div>",
                e.getMessage()
            );
        } catch (Exception e) {
            return String.format(
                "<div class='alert alert-danger'><strong>Error:</strong> %s</div>",
                e.getMessage()
            );
        }
    }
}

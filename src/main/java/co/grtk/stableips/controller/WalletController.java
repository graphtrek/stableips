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

        // Get both sent and received transactions
        java.util.Map<String, java.util.List<co.grtk.stableips.model.Transaction>> allTransactions =
            transactionService.getAllUserTransactions(user);

        // Get funding transactions (ETH funding, token minting, XRP faucet funding)
        java.util.List<co.grtk.stableips.model.Transaction> fundingTransactions =
            transactionService.getFundingTransactions(user.getId());

        model.addAttribute("user", user);
        model.addAttribute("ethBalance", ethBalance);
        model.addAttribute("usdcBalance", usdcBalance);
        model.addAttribute("daiBalance", daiBalance);
        model.addAttribute("xrpBalance", xrpBalance);
        model.addAttribute("solBalance", solBalance);
        model.addAttribute("sentTransactions", allTransactions.get("sent"));
        model.addAttribute("receivedTransactions", allTransactions.get("received"));
        model.addAttribute("fundingTransactions", fundingTransactions);

        return "wallet/dashboard";
    }

    @PostMapping("/regenerate-xrp")
    @ResponseBody
    public String regenerateXrpWallet(HttpSession session) {
        if (!authService.isAuthenticated(session)) {
            return "<div class='alert alert-error'>Not authenticated</div>";
        }

        try {
            User user = authService.getCurrentUser(session);
            walletService.regenerateXrpWallet(user);

            return """
                <div class='alert alert-success'>
                    <strong>Success!</strong> XRP wallet regenerated. Please refresh the page to see your new wallet address.
                </div>
                """;
        } catch (Exception e) {
            return String.format(
                "<div class='alert alert-error'>Failed to regenerate XRP wallet: %s</div>",
                e.getMessage()
            );
        }
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

package co.grtk.stableips.controller;

import co.grtk.stableips.model.Transaction;
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
import java.util.List;
import java.util.Map;

/**
 * Controller for managing wallet operations and displaying wallet dashboard.
 *
 * <p>This controller handles the main wallet dashboard page, which displays user balances
 * across multiple blockchain networks (Ethereum, XRP Ledger, Solana) and provides access
 * to wallet management features like regenerating wallets and funding with test tokens.</p>
 *
 * <p>Key features:
 * <ul>
 *   <li>Multi-blockchain wallet dashboard with real-time balances</li>
 *   <li>Transaction history display (sent, received, funding)</li>
 *   <li>XRP wallet regeneration for legacy data migration</li>
 *   <li>Test token funding (USDC, DAI) for development/testing</li>
 * </ul>
 * </p>
 *
 * <p>All endpoints require user authentication via session.</p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 * @see WalletService
 * @see TransactionService
 */
@Controller
@RequestMapping("/wallet")
public class WalletController {

    private final AuthService authService;
    private final WalletService walletService;
    private final TransactionService transactionService;

    /**
     * Constructs a WalletController with required service dependencies.
     *
     * @param authService service for session authentication and user management
     * @param walletService service for wallet operations and balance queries
     * @param transactionService service for transaction history and management
     */
    public WalletController(
        AuthService authService,
        WalletService walletService,
        TransactionService transactionService
    ) {
        this.authService = authService;
        this.walletService = walletService;
        this.transactionService = transactionService;
    }

    /**
     * Displays the main wallet dashboard page.
     *
     * <p>This endpoint renders a comprehensive wallet dashboard showing:
     * <ul>
     *   <li>Wallet addresses for Ethereum, XRP Ledger, and Solana</li>
     *   <li>Current balances for ETH, USDC, DAI, XRP, and SOL</li>
     *   <li>Transaction history categorized as sent, received, and funding</li>
     * </ul>
     * </p>
     *
     * <p>Balances are fetched in real-time from blockchain networks. If a balance
     * query fails, it defaults to zero to prevent page errors.</p>
     *
     * <p>Requires authentication. Unauthenticated users are redirected to login.</p>
     *
     * @param session the HTTP session containing user authentication state
     * @param model Spring MVC model for passing data to the view
     * @return the view name "wallet/dashboard" or redirect to "/login" if not authenticated
     */
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
        Map<String, List<Transaction>> allTransactions =
            transactionService.getAllUserTransactions(user);

        // Get funding transactions (ETH funding, token minting, XRP faucet funding)
        List<Transaction> fundingTransactions =
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

    /**
     * Regenerates the user's XRP wallet address.
     *
     * <p>This endpoint creates a new XRP Ledger wallet for the authenticated user,
     * replacing their existing XRP address and secret. It's primarily used for
     * migrating legacy users who were created before XRP wallet support was added.</p>
     *
     * <p>After regeneration, the new wallet is automatically funded via the XRP testnet
     * faucet to provide initial XRP for transaction fees.</p>
     *
     * <p>Returns an HTMX-compatible HTML fragment for dynamic page updates.</p>
     *
     * @param session the HTTP session containing user authentication state
     * @return HTML fragment indicating success or error (HTMX response)
     */
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

    /**
     * Funds the user's wallet with test tokens for development and testing.
     *
     * <p>This endpoint mints test USDC and DAI tokens to the authenticated user's
     * Ethereum wallet. It requires a configured funding wallet with owner privileges
     * on the deployed test token contracts.</p>
     *
     * <p>Funding amounts are configured in application.properties:
     * <ul>
     *   <li>token.funding.initial-usdc (default: 1000 TEST-USDC)</li>
     *   <li>token.funding.initial-dai (default: 1000 TEST-DAI)</li>
     * </ul>
     * </p>
     *
     * <p>The minting transactions are logged in the database with type "MINTING"
     * for transaction history tracking.</p>
     *
     * <p>Returns an HTMX-compatible HTML fragment showing transaction links to Etherscan.</p>
     *
     * @param session the HTTP session containing user authentication state
     * @return HTML fragment with transaction links or error message (HTMX response)
     */
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

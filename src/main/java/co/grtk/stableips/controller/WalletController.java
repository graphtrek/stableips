package co.grtk.stableips.controller;

import co.grtk.stableips.model.Transaction;
import co.grtk.stableips.model.User;
import co.grtk.stableips.service.AuthService;
import co.grtk.stableips.service.TransactionService;
import co.grtk.stableips.service.WalletService;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
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
    private final TemplateEngine templateEngine;

    /**
     * Constructs a WalletController with required service dependencies.
     *
     * @param authService service for session authentication and user management
     * @param walletService service for wallet operations and balance queries
     * @param transactionService service for transaction history and management
     * @param templateEngine JTE template engine for rendering HTML fragments
     */
    public WalletController(
        AuthService authService,
        WalletService walletService,
        TransactionService transactionService,
        TemplateEngine templateEngine
    ) {
        this.authService = authService;
        this.walletService = walletService;
        this.transactionService = transactionService;
        this.templateEngine = templateEngine;
    }

    /**
     * Displays the main wallet dashboard page with complete transaction history.
     *
     * <p>This endpoint renders a comprehensive wallet dashboard showing:</p>
     *
     * <ul>
     *   <li>Wallet addresses for Ethereum, XRP Ledger, and Solana</li>
     *   <li>Current balances for ETH, USDC, DAI, XRP, and SOL (real-time blockchain queries)</li>
     *   <li>Complete transaction history including sent, received, and funding transactions</li>
     * </ul>
     *
     * <p><strong>Transaction History Structure:</strong></p>
     * <ul>
     *   <li><code>allTransactions</code>: Unified timeline of ALL transactions (sent + received + funding) sorted by timestamp descending</li>
     *   <li><code>sentTransactions</code>: User-initiated transfers only</li>
     *   <li><code>receivedTransactions</code>: Incoming transfers from external sources</li>
     *   <li><code>fundingTransactions</code>: System-initiated funding (ETH funding, test token minting, faucet requests)</li>
     * </ul>
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

        // Fetch categorized transactions (sent, received, and merged timeline)
        Map<String, List<Transaction>> transactionsByCategory =
            transactionService.getAllUserTransactions(user);

        // Fetch system-initiated funding transactions separately
        List<Transaction> fundingTransactions =
            transactionService.getFundingTransactions(user.getId());

        // Create complete unified timeline: sent + received + funding
        List<Transaction> completeTransactionHistory = mergeAllTransactions(
            transactionsByCategory.get("all"),
            fundingTransactions
        );

        model.addAttribute("user", user);
        model.addAttribute("ethBalance", ethBalance);
        model.addAttribute("usdcBalance", usdcBalance);
        model.addAttribute("daiBalance", daiBalance);
        model.addAttribute("xrpBalance", xrpBalance);
        model.addAttribute("solBalance", solBalance);
        model.addAttribute("allTransactions", completeTransactionHistory);
        model.addAttribute("sentTransactions", transactionsByCategory.get("sent"));
        model.addAttribute("receivedTransactions", transactionsByCategory.get("received"));
        model.addAttribute("fundingTransactions", fundingTransactions);

        return "wallet/dashboard";
    }

    /**
     * Merges user-initiated transactions with funding transactions into a unified timeline.
     *
     * <p>This method combines sent/received transactions with system-initiated funding
     * transactions and sorts them by timestamp descending (newest first) to provide
     * a complete chronological transaction history.</p>
     *
     * @param userTransactions sent and received transactions already merged
     * @param fundingTransactions system-initiated funding operations
     * @return complete transaction list sorted by timestamp descending
     */
    private List<Transaction> mergeAllTransactions(
        List<Transaction> userTransactions,
        List<Transaction> fundingTransactions
    ) {
        List<Transaction> mergedList = new java.util.ArrayList<>();
        mergedList.addAll(userTransactions);
        mergedList.addAll(fundingTransactions);
        mergedList.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
        return mergedList;
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
            return renderTemplate("wallet/fragments/auth-error.jte");
        }

        try {
            User user = authService.getCurrentUser(session);
            walletService.regenerateXrpWallet(user);

            return renderTemplate("wallet/fragments/xrp-regenerate-success.jte");
        } catch (Exception e) {
            return renderTemplate("wallet/fragments/xrp-regenerate-error.jte",
                Map.of("errorMessage", e.getMessage()));
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
            return renderTemplate("wallet/fragments/auth-error.jte");
        }

        try {
            User user = authService.getCurrentUser(session);
            Map<String, String> txHashes = walletService.fundTestTokens(user.getWalletAddress());

            return renderTemplate("wallet/fragments/fund-success.jte",
                Map.of(
                    "usdcTxHash", txHashes.get("usdc"),
                    "daiTxHash", txHashes.get("dai")
                ));
        } catch (IllegalStateException e) {
            return renderTemplate("wallet/fragments/fund-config-error.jte",
                Map.of("errorMessage", e.getMessage()));
        } catch (Exception e) {
            return renderTemplate("wallet/fragments/fund-error.jte",
                Map.of("errorMessage", e.getMessage()));
        }
    }

    /**
     * Renders a JTE template with optional parameters.
     *
     * @param templatePath the path to the JTE template
     * @param params optional template parameters
     * @return rendered HTML string
     */
    private String renderTemplate(String templatePath, Map<String, Object> params) {
        StringOutput output = new StringOutput();
        templateEngine.render(templatePath, params, output);
        return output.toString();
    }

    /**
     * Renders a JTE template without parameters.
     *
     * @param templatePath the path to the JTE template
     * @return rendered HTML string
     */
    private String renderTemplate(String templatePath) {
        return renderTemplate(templatePath, Map.of());
    }
}

package co.grtk.stableips.controller;

import co.grtk.stableips.model.Transaction;
import co.grtk.stableips.model.User;
import co.grtk.stableips.service.AuthService;
import co.grtk.stableips.service.TransactionService;
import co.grtk.stableips.service.WalletService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for WalletController.
 *
 * <p>This test class validates the wallet dashboard endpoint behavior, including
 * authentication checks, balance display, and transaction history rendering.
 * Uses MockMvc for HTTP simulation and MockBean for service layer isolation.</p>
 *
 * <p>Test coverage:
 * <ul>
 *   <li>Authenticated wallet dashboard display</li>
 *   <li>Multi-blockchain balance rendering (ETH, USDC, DAI, XRP, SOL)</li>
 *   <li>Transaction history display (sent, received, funding)</li>
 *   <li>Unauthenticated access handling</li>
 * </ul>
 * </p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private WalletService walletService;

    @MockBean
    private TransactionService transactionService;

    /**
     * Tests successful wallet dashboard rendering for authenticated user.
     *
     * <p>Verifies that:
     * <ul>
     *   <li>Authenticated users can access the wallet dashboard</li>
     *   <li>All wallet balances are displayed (ETH, USDC, DAI, XRP, SOL)</li>
     *   <li>Transaction history is properly categorized (sent, received, funding)</li>
     *   <li>User information is passed to the view</li>
     *   <li>Correct view template is rendered</li>
     * </ul>
     * </p>
     */
    @Test
    void shouldShowWalletDashboard() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);
        session.setAttribute("username", "alice");

        User user = new User("alice", "0xWallet123");
        user.setId(1L);
        user.setXrpAddress("rN7n7otQDd6FczFgLdllrq4OhiX1zp7n8");
        user.setSolanaPublicKey("DYw8jCTfwHNRJhhmFcbXvVDTqWMEVFBX6Z");

        BigDecimal ethBalance = new BigDecimal("1.5");
        BigDecimal usdcBalance = new BigDecimal("500.00");
        BigDecimal daiBalance = new BigDecimal("250.75");
        BigDecimal xrpBalance = new BigDecimal("10.0");
        BigDecimal solBalance = new BigDecimal("2.0");

        Transaction sentTx = new Transaction(1L, "0xRecipient1", BigDecimal.TEN, "USDC", "ETHEREUM", "0xAbcdef123456", "CONFIRMED");
        Transaction receivedTx = new Transaction(2L, "0xWallet123", BigDecimal.ONE, "DAI", "ETHEREUM", "0x9876543210ab", "PENDING");
        Transaction fundingTx = new Transaction(1L, "0xWallet123", new BigDecimal("10"), "ETH", "ETHEREUM", "0xFedcba098765", "CONFIRMED");
        fundingTx.setType("FUNDING");

        List<Transaction> sentTransactions = Arrays.asList(sentTx);
        List<Transaction> receivedTransactions = Arrays.asList(receivedTx);
        List<Transaction> fundingTransactions = Arrays.asList(fundingTx);

        // Combined transactions sorted by timestamp (sent + received)
        List<Transaction> combinedTransactions = Arrays.asList(sentTx, receivedTx);

        Map<String, List<Transaction>> allTransactions = Map.of(
            "sent", sentTransactions,
            "received", receivedTransactions,
            "all", combinedTransactions  // Added "all" key with combined sorted list
        );

        when(authService.isAuthenticated(session)).thenReturn(true);
        when(authService.getCurrentUser(session)).thenReturn(user);
        when(walletService.getEthBalance(anyString())).thenReturn(ethBalance);
        when(transactionService.getTokenBalance(anyString(), ArgumentMatchers.eq("USDC"))).thenReturn(usdcBalance);
        when(transactionService.getTokenBalance(anyString(), ArgumentMatchers.eq("DAI"))).thenReturn(daiBalance);
        when(walletService.getXrpBalance(anyString())).thenReturn(xrpBalance);
        when(walletService.getSolanaBalance(anyString())).thenReturn(solBalance);
        when(transactionService.getAllUserTransactions(user)).thenReturn(allTransactions);
        when(transactionService.getFundingTransactions(1L)).thenReturn(fundingTransactions);

        // When & Then
        mockMvc.perform(get("/wallet").session(session))
            .andExpect(status().isOk())
            .andExpect(view().name("wallet/dashboard"))
            .andExpect(model().attribute("user", user))
            .andExpect(model().attribute("ethBalance", ethBalance))
            .andExpect(model().attribute("usdcBalance", usdcBalance))
            .andExpect(model().attribute("daiBalance", daiBalance))
            .andExpect(model().attribute("xrpBalance", xrpBalance))
            .andExpect(model().attribute("solBalance", solBalance))
            .andExpect(model().attribute("sentTransactions", sentTransactions))
            .andExpect(model().attribute("receivedTransactions", receivedTransactions))
            .andExpect(model().attribute("fundingTransactions", fundingTransactions))
            .andExpect(model().attributeExists("allTransactions"));  // Verify allTransactions exists
    }

    /**
     * Tests that unauthenticated users are redirected to login page.
     *
     * <p>Verifies that attempting to access the wallet dashboard without
     * authentication results in a redirect to the login page, protecting
     * sensitive financial information.</p>
     */
    @Test
    void shouldRedirectToLoginWhenNotAuthenticated() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        when(authService.isAuthenticated(session)).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/wallet").session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    /**
     * Tests successful XRP wallet regeneration for authenticated user.
     */
    @Test
    void shouldRegenerateXrpWalletSuccessfully() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        User user = new User("alice", "0xWallet123");
        user.setId(1L);

        when(authService.isAuthenticated(session)).thenReturn(true);
        when(authService.getCurrentUser(session)).thenReturn(user);

        // When & Then
        mockMvc.perform(post("/wallet/regenerate-xrp").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Success")))
            .andExpect(content().string(containsString("XRP wallet regenerated")));
    }

    /**
     * Tests XRP wallet regeneration fails when not authenticated.
     */
    @Test
    void shouldFailXrpRegenerationWhenNotAuthenticated() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        when(authService.isAuthenticated(session)).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/wallet/regenerate-xrp").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Not authenticated")));
    }

    /**
     * Tests XRP wallet regeneration handles errors gracefully.
     */
    @Test
    void shouldHandleXrpRegenerationError() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        User user = new User("alice", "0xWallet123");
        user.setId(1L);

        when(authService.isAuthenticated(session)).thenReturn(true);
        when(authService.getCurrentUser(session)).thenReturn(user);
        doThrow(new RuntimeException("Network error"))
            .when(walletService).regenerateXrpWallet(user);

        // When & Then
        mockMvc.perform(post("/wallet/regenerate-xrp").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Failed to regenerate XRP wallet")))
            .andExpect(content().string(containsString("Network error")));
    }

    /**
     * Tests successful wallet funding for authenticated user.
     */
    @Test
    void shouldFundWalletSuccessfully() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        User user = new User("alice", "0xWallet123");
        user.setId(1L);

        Map<String, String> txHashes = Map.of(
            "usdc", "0xabc123456789",
            "dai", "0xdef987654321"
        );

        when(authService.isAuthenticated(session)).thenReturn(true);
        when(authService.getCurrentUser(session)).thenReturn(user);
        when(walletService.fundTestTokens(anyString())).thenReturn(txHashes);

        // When & Then
        mockMvc.perform(post("/wallet/fund").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Success")))
            .andExpect(content().string(containsString("Wallet funded")))
            .andExpect(content().string(containsString("0xabc123456")))
            .andExpect(content().string(containsString("0xdef987654")));
    }

    /**
     * Tests wallet funding fails when not authenticated.
     */
    @Test
    void shouldFailFundingWhenNotAuthenticated() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        when(authService.isAuthenticated(session)).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/wallet/fund").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Not authenticated")));
    }

    /**
     * Tests wallet funding handles configuration errors.
     */
    @Test
    void shouldHandleFundingConfigurationError() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        User user = new User("alice", "0xWallet123");
        user.setId(1L);

        when(authService.isAuthenticated(session)).thenReturn(true);
        when(authService.getCurrentUser(session)).thenReturn(user);
        when(walletService.fundTestTokens(anyString()))
            .thenThrow(new IllegalStateException("Funding wallet not configured"));

        // When & Then
        mockMvc.perform(post("/wallet/fund").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Configuration Required")))
            .andExpect(content().string(containsString("Funding wallet not configured")));
    }

    /**
     * Tests wallet funding handles general errors.
     */
    @Test
    void shouldHandleFundingGeneralError() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        User user = new User("alice", "0xWallet123");
        user.setId(1L);

        when(authService.isAuthenticated(session)).thenReturn(true);
        when(authService.getCurrentUser(session)).thenReturn(user);
        when(walletService.fundTestTokens(anyString()))
            .thenThrow(new RuntimeException("Transaction failed"));

        // When & Then
        mockMvc.perform(post("/wallet/fund").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Error")))
            .andExpect(content().string(containsString("Transaction failed")));
    }
}

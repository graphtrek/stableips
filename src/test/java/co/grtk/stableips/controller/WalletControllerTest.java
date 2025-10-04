package co.grtk.stableips.controller;

import co.grtk.stableips.model.Transaction;
import co.grtk.stableips.model.User;
import co.grtk.stableips.service.AuthService;
import co.grtk.stableips.service.TransactionService;
import co.grtk.stableips.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private WalletService walletService;

    @MockBean
    private TransactionService transactionService;

    @Test
    void shouldShowWalletDashboard() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);
        session.setAttribute("username", "alice");

        User user = new User("alice", "0xWallet123");
        user.setId(1L);

        BigDecimal ethBalance = new BigDecimal("1.5");
        BigDecimal usdcBalance = new BigDecimal("500.00");
        BigDecimal daiBalance = new BigDecimal("250.75");

        Transaction tx1 = new Transaction(1L, "0xRecipient1", BigDecimal.TEN, "USDC", "ETHEREUM", "0xHash1", "CONFIRMED");
        Transaction tx2 = new Transaction(1L, "0xRecipient2", BigDecimal.ONE, "DAI", "ETHEREUM", "0xHash2", "PENDING");
        List<Transaction> transactions = Arrays.asList(tx1, tx2);

        when(authService.isAuthenticated(session)).thenReturn(true);
        when(authService.getCurrentUser(session)).thenReturn(user);
        when(walletService.getEthBalance(anyString())).thenReturn(ethBalance);
        when(transactionService.getTokenBalance(anyString(), org.mockito.ArgumentMatchers.eq("USDC"))).thenReturn(usdcBalance);
        when(transactionService.getTokenBalance(anyString(), org.mockito.ArgumentMatchers.eq("DAI"))).thenReturn(daiBalance);
        when(transactionService.getUserTransactions(1L)).thenReturn(transactions);

        // When & Then
        mockMvc.perform(get("/wallet").session(session))
            .andExpect(status().isOk())
            .andExpect(view().name("wallet/dashboard"))
            .andExpect(model().attribute("user", user))
            .andExpect(model().attribute("ethBalance", ethBalance))
            .andExpect(model().attribute("usdcBalance", usdcBalance))
            .andExpect(model().attribute("daiBalance", daiBalance))
            .andExpect(model().attribute("transactions", transactions));
    }

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
}

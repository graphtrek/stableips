package co.grtk.stableips.controller;

import co.grtk.stableips.model.Transaction;
import co.grtk.stableips.model.User;
import co.grtk.stableips.service.AuthService;
import co.grtk.stableips.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private TransactionService transactionService;

    @Test
    void shouldInitiateTransfer() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);

        User user = new User("alice", "0xWallet123");
        user.setId(1L);

        String recipient = "0xRecipient456";
        BigDecimal amount = new BigDecimal("100.50");
        String token = "USDC";

        Transaction transaction = new Transaction(
            user.getId(),
            recipient,
            amount,
            token,
            "0xTransactionHash123",
            "PENDING"
        );
        transaction.setId(10L);

        when(authService.isAuthenticated(session)).thenReturn(true);
        when(authService.getCurrentUser(session)).thenReturn(user);
        when(transactionService.initiateTransfer(eq(user), eq(recipient), eq(amount), eq(token)))
            .thenReturn(transaction);

        // When & Then
        mockMvc.perform(post("/transfer")
                .session(session)
                .param("recipient", recipient)
                .param("amount", "100.50")
                .param("token", token))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/wallet?success=true"));

        verify(transactionService).initiateTransfer(eq(user), eq(recipient), eq(amount), eq(token));
    }

    @Test
    void shouldRedirectToLoginWhenNotAuthenticated() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        when(authService.isAuthenticated(session)).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/transfer")
                .session(session)
                .param("recipient", "0xRecipient")
                .param("amount", "100")
                .param("token", "USDC"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void shouldHandleTransferError() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);

        User user = new User("bob", "0xWallet789");
        user.setId(1L);

        when(authService.isAuthenticated(session)).thenReturn(true);
        when(authService.getCurrentUser(session)).thenReturn(user);
        when(transactionService.initiateTransfer(any(), any(), any(), any()))
            .thenThrow(new RuntimeException("Insufficient funds"));

        // When & Then
        mockMvc.perform(post("/transfer")
                .session(session)
                .param("recipient", "0xRecipient")
                .param("amount", "1000")
                .param("token", "USDC"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/wallet?error=true"));
    }
}

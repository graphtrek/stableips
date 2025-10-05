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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for TransferController.
 *
 * <p>This test class validates the transfer initiation endpoint behavior,
 * including authentication checks, successful transfer execution, and error
 * handling. Uses MockMvc for HTTP request simulation and MockBean for service
 * layer mocking.</p>
 *
 * <p>Test coverage:
 * <ul>
 *   <li>Authenticated user transfer initiation</li>
 *   <li>Unauthenticated access handling</li>
 *   <li>Transfer error scenarios (insufficient funds, etc.)</li>
 * </ul>
 * </p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private TransactionService transactionService;

    /**
     * Tests successful transfer initiation for an authenticated user.
     *
     * <p>Verifies that:
     * <ul>
     *   <li>Authenticated users can initiate transfers</li>
     *   <li>Transfer service is called with correct parameters</li>
     *   <li>User is redirected to wallet page with success flag</li>
     *   <li>Transaction hash is included in redirect URL</li>
     * </ul>
     * </p>
     */
    @Test
    void shouldInitiateTransfer() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);

        User user = new User("alice", "0x71C7656EC7ab88b098defB751B7401B5f6d8976F");
        user.setId(1L);

        String recipient = "0x0000000000000000000000000000000000000001";
        BigDecimal amount = new BigDecimal("100.50");
        String token = "USDC";

        Transaction transaction = new Transaction(
            user.getId(),
            recipient,
            amount,
            token,
            "ETHEREUM",
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
            .andExpect(redirectedUrlPattern("/wallet?success=true&txHash=*"));

        verify(transactionService).initiateTransfer(eq(user), eq(recipient), eq(amount), eq(token));
    }

    /**
     * Tests that unauthenticated users are redirected to login page.
     *
     * <p>Verifies that attempting to initiate a transfer without authentication
     * results in a redirect to the login page, preventing unauthorized access.</p>
     */
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

    /**
     * Tests error handling when transfer execution fails.
     *
     * <p>Verifies that:
     * <ul>
     *   <li>Transfer service errors are caught gracefully</li>
     *   <li>User is redirected to wallet page with error parameter</li>
     *   <li>Error message is included in the redirect</li>
     * </ul>
     * </p>
     *
     * <p>Common error scenarios: insufficient funds, invalid recipient address,
     * network connectivity issues.</p>
     */
    @Test
    void shouldHandleTransferError() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);

        User user = new User("bob", "0x71C7656EC7ab88b098defB751B7401B5f6d8976F");
        user.setId(1L);

        when(authService.isAuthenticated(session)).thenReturn(true);
        when(authService.getCurrentUser(session)).thenReturn(user);
        when(transactionService.initiateTransfer(any(), any(), any(), any()))
            .thenThrow(new RuntimeException("Insufficient funds"));

        // When & Then
        mockMvc.perform(post("/transfer")
                .session(session)
                .param("recipient", "0x0000000000000000000000000000000000000002")
                .param("amount", "1000")
                .param("token", "USDC"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/wallet?error=*"));
    }
}

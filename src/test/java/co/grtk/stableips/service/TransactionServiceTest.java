package co.grtk.stableips.service;

import co.grtk.stableips.model.Transaction;
import co.grtk.stableips.model.User;
import co.grtk.stableips.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.Credentials;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private ContractService contractService;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void shouldInitiateTransfer() {
        // Given
        Long userId = 1L;
        String recipient = "0xRecipient123";
        BigDecimal amount = new BigDecimal("100.50");
        String token = "USDC";
        String txHash = "0xTransactionHash123";

        User user = new User("alice", "0xSender123");
        user.setId(userId);

        Credentials credentials = mock(Credentials.class);
        when(walletService.getUserCredentials(anyString())).thenReturn(credentials);
        when(contractService.transfer(credentials, recipient, amount, token)).thenReturn(txHash);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(1L);
            return tx;
        });

        // When
        Transaction transaction = transactionService.initiateTransfer(user, recipient, amount, token);

        // Then
        assertThat(transaction).isNotNull();
        assertThat(transaction.getUserId()).isEqualTo(userId);
        assertThat(transaction.getRecipient()).isEqualTo(recipient);
        assertThat(transaction.getAmount()).isEqualByComparingTo(amount);
        assertThat(transaction.getToken()).isEqualTo(token);
        assertThat(transaction.getTxHash()).isEqualTo(txHash);
        assertThat(transaction.getStatus()).isEqualTo("PENDING");
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void shouldGetTransactionsByUserId() {
        // Given
        Long userId = 1L;
        Transaction tx1 = new Transaction(userId, "0xAddr1", BigDecimal.TEN, "USDC", "0xHash1", "CONFIRMED");
        Transaction tx2 = new Transaction(userId, "0xAddr2", BigDecimal.ONE, "DAI", "0xHash2", "PENDING");

        when(transactionRepository.findByUserIdOrderByTimestampDesc(userId))
            .thenReturn(Arrays.asList(tx2, tx1));

        // When
        List<Transaction> transactions = transactionService.getUserTransactions(userId);

        // Then
        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).getTxHash()).isEqualTo("0xHash2"); // Most recent first
        assertThat(transactions.get(1).getTxHash()).isEqualTo("0xHash1");
    }

    @Test
    void shouldGetTransactionByHash() {
        // Given
        String txHash = "0xHash123";
        Transaction transaction = new Transaction(1L, "0xRecipient", BigDecimal.TEN, "USDC", txHash, "CONFIRMED");

        when(transactionRepository.findByTxHash(txHash)).thenReturn(Optional.of(transaction));

        // When
        Transaction found = transactionService.getTransactionByHash(txHash);

        // Then
        assertThat(found).isNotNull();
        assertThat(found.getTxHash()).isEqualTo(txHash);
    }

    @Test
    void shouldThrowExceptionWhenTransactionNotFound() {
        // Given
        String txHash = "0xNonExistent";
        when(transactionRepository.findByTxHash(txHash)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> transactionService.getTransactionByHash(txHash))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void shouldUpdateTransactionStatus() {
        // Given
        Long txId = 1L;
        String newStatus = "CONFIRMED";
        Transaction transaction = new Transaction(1L, "0xRecipient", BigDecimal.TEN, "USDC", "0xHash", "PENDING");
        transaction.setId(txId);

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Transaction updated = transactionService.updateTransactionStatus(txId, newStatus);

        // Then
        assertThat(updated.getStatus()).isEqualTo(newStatus);
        verify(transactionRepository).save(transaction);
    }

    @Test
    void shouldGetTokenBalance() {
        // Given
        String walletAddress = "0xWallet123";
        String token = "USDC";
        BigDecimal balance = new BigDecimal("500.75");

        when(contractService.getBalance(walletAddress, token)).thenReturn(balance);

        // When
        BigDecimal result = transactionService.getTokenBalance(walletAddress, token);

        // Then
        assertThat(result).isEqualByComparingTo(balance);
        verify(contractService).getBalance(walletAddress, token);
    }

    @Test
    void shouldHandleTransferFailure() {
        // Given
        User user = new User("bob", "0xSender");
        user.setId(2L);
        Credentials credentials = mock(Credentials.class);

        when(walletService.getUserCredentials(anyString())).thenReturn(credentials);
        when(contractService.transfer(any(), anyString(), any(), anyString()))
            .thenThrow(new RuntimeException("Insufficient funds"));

        // When & Then
        assertThatThrownBy(() ->
            transactionService.initiateTransfer(user, "0xRecipient", BigDecimal.TEN, "USDC"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Insufficient funds");
    }
}

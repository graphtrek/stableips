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

    @Mock
    private XrpWalletService xrpWalletService;

    @Mock
    private SolanaWalletService solanaWalletService;

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
        Transaction tx1 = new Transaction(userId, "0xAddr1", BigDecimal.TEN, "USDC", "ETHEREUM", "0xHash1", "CONFIRMED");
        Transaction tx2 = new Transaction(userId, "0xAddr2", BigDecimal.ONE, "DAI", "ETHEREUM", "0xHash2", "PENDING");

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
        Transaction transaction = new Transaction(1L, "0xRecipient", BigDecimal.TEN, "USDC", "ETHEREUM", txHash, "CONFIRMED");

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
        Transaction transaction = new Transaction(1L, "0xRecipient", BigDecimal.TEN, "USDC", "ETHEREUM", "0xHash", "PENDING");
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

    // ==================== Funding Transaction Tests ====================

    @Test
    void shouldRecordEthFundingTransaction() {
        // Given
        Long userId = 1L;
        String walletAddress = "0xUserWallet123";
        BigDecimal fundingAmount = new BigDecimal("10");
        String txHash = "0xFundingTxHash123";

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(1L);
            return tx;
        });

        // When
        Transaction fundingTx = transactionService.recordFundingTransaction(
            userId,
            walletAddress,
            fundingAmount,
            "ETH",
            "ETHEREUM",
            txHash,
            "FUNDING"
        );

        // Then
        assertThat(fundingTx).isNotNull();
        assertThat(fundingTx.getUserId()).isEqualTo(userId);
        assertThat(fundingTx.getRecipient()).isEqualTo(walletAddress);
        assertThat(fundingTx.getAmount()).isEqualByComparingTo(fundingAmount);
        assertThat(fundingTx.getToken()).isEqualTo("ETH");
        assertThat(fundingTx.getNetwork()).isEqualTo("ETHEREUM");
        assertThat(fundingTx.getTxHash()).isEqualTo(txHash);
        assertThat(fundingTx.getStatus()).isEqualTo("CONFIRMED");
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void shouldRecordUsdcMintingTransaction() {
        // Given
        Long userId = 2L;
        String walletAddress = "0xUserWallet456";
        BigDecimal mintAmount = new BigDecimal("1000");
        String txHash = "0xMintTxHash456";

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(2L);
            return tx;
        });

        // When
        Transaction mintingTx = transactionService.recordFundingTransaction(
            userId,
            walletAddress,
            mintAmount,
            "TEST-USDC",
            "ETHEREUM",
            txHash,
            "MINTING"
        );

        // Then
        assertThat(mintingTx).isNotNull();
        assertThat(mintingTx.getUserId()).isEqualTo(userId);
        assertThat(mintingTx.getRecipient()).isEqualTo(walletAddress);
        assertThat(mintingTx.getAmount()).isEqualByComparingTo(mintAmount);
        assertThat(mintingTx.getToken()).isEqualTo("TEST-USDC");
        assertThat(mintingTx.getStatus()).isEqualTo("CONFIRMED");
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void shouldRecordXrpFaucetFundingTransaction() {
        // Given
        Long userId = 3L;
        String xrpAddress = "rN7n7otQDd6FczFgLdllrq4OhiX1zp7n8";
        BigDecimal fundAmount = new BigDecimal("1000");
        String txHash = "XRP_FAUCET_0x123abc";

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(3L);
            return tx;
        });

        // When
        Transaction xrpFundingTx = transactionService.recordFundingTransaction(
            userId,
            xrpAddress,
            fundAmount,
            "XRP",
            "XRP",
            txHash,
            "FAUCET_FUNDING"
        );

        // Then
        assertThat(xrpFundingTx).isNotNull();
        assertThat(xrpFundingTx.getUserId()).isEqualTo(userId);
        assertThat(xrpFundingTx.getRecipient()).isEqualTo(xrpAddress);
        assertThat(xrpFundingTx.getAmount()).isEqualByComparingTo(fundAmount);
        assertThat(xrpFundingTx.getToken()).isEqualTo("XRP");
        assertThat(xrpFundingTx.getNetwork()).isEqualTo("XRP");
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void shouldGetAllTransactionsIncludingFunding() {
        // Given
        Long userId = 1L;

        // User initiated transfer
        Transaction userTransfer = new Transaction(
            userId, "0xRecipient", new BigDecimal("50"), "USDC", "ETHEREUM", "0xTransfer1", "CONFIRMED"
        );

        // ETH funding transaction
        Transaction ethFunding = new Transaction(
            userId, "0xUserWallet", new BigDecimal("10"), "ETH", "ETHEREUM", "0xFunding1", "CONFIRMED"
        );

        // USDC minting transaction
        Transaction usdcMinting = new Transaction(
            userId, "0xUserWallet", new BigDecimal("1000"), "TEST-USDC", "ETHEREUM", "0xMinting1", "CONFIRMED"
        );

        when(transactionRepository.findByUserIdOrderByTimestampDesc(userId))
            .thenReturn(Arrays.asList(usdcMinting, ethFunding, userTransfer));

        // When
        List<Transaction> allTransactions = transactionService.getUserTransactions(userId);

        // Then
        assertThat(allTransactions).hasSize(3);
        assertThat(allTransactions).extracting(Transaction::getTxHash)
            .containsExactly("0xMinting1", "0xFunding1", "0xTransfer1");
    }

    @Test
    void shouldGetFundingTransactionsOnly() {
        // Given
        Long userId = 1L;

        Transaction ethFunding = new Transaction(
            userId, "0xUserWallet", new BigDecimal("10"), "ETH", "ETHEREUM", "0xFunding1", "CONFIRMED"
        );
        ethFunding.setType("FUNDING");

        Transaction usdcMinting = new Transaction(
            userId, "0xUserWallet", new BigDecimal("1000"), "TEST-USDC", "ETHEREUM", "0xMinting1", "CONFIRMED"
        );
        usdcMinting.setType("MINTING");

        when(transactionRepository.findByUserIdAndTypeInOrderByTimestampDesc(
            userId,
            List.of("FUNDING", "MINTING", "FAUCET_FUNDING")
        )).thenReturn(Arrays.asList(usdcMinting, ethFunding));

        // When
        List<Transaction> fundingTransactions = transactionService.getFundingTransactions(userId);

        // Then
        assertThat(fundingTransactions).hasSize(2);
        assertThat(fundingTransactions).extracting(Transaction::getToken)
            .containsExactly("TEST-USDC", "ETH");
    }

    @Test
    void shouldHandleNullTxHashForFundingTransaction() {
        // Given - When funding fails, txHash might be null
        Long userId = 4L;
        String walletAddress = "0xUserWallet789";

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(4L);
            return tx;
        });

        // When
        Transaction failedFunding = transactionService.recordFundingTransaction(
            userId,
            walletAddress,
            new BigDecimal("10"),
            "ETH",
            "ETHEREUM",
            null,  // Funding failed, no txHash
            "FUNDING"
        );

        // Then
        assertThat(failedFunding).isNotNull();
        assertThat(failedFunding.getTxHash()).isNull();
        assertThat(failedFunding.getStatus()).isEqualTo("FAILED");
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void shouldDistinguishBetweenTransferAndFundingTypes() {
        // Given
        Long userId = 1L;

        Transaction userTransfer = new Transaction(
            userId, "0xOtherUser", new BigDecimal("50"), "USDC", "ETHEREUM", "0xTransfer1", "CONFIRMED"
        );

        Transaction systemFunding = new Transaction(
            userId, "0xUserWallet", new BigDecimal("10"), "ETH", "ETHEREUM", "0xFunding1", "CONFIRMED"
        );

        when(transactionRepository.findByUserIdAndTypeOrderByTimestampDesc(userId, "TRANSFER"))
            .thenReturn(List.of(userTransfer));

        when(transactionRepository.findByUserIdAndTypeOrderByTimestampDesc(userId, "FUNDING"))
            .thenReturn(List.of(systemFunding));

        // When
        List<Transaction> transfers = transactionService.getTransactionsByType(userId, "TRANSFER");
        List<Transaction> funding = transactionService.getTransactionsByType(userId, "FUNDING");

        // Then
        assertThat(transfers).hasSize(1);
        assertThat(transfers.get(0).getTxHash()).isEqualTo("0xTransfer1");

        assertThat(funding).hasSize(1);
        assertThat(funding.get(0).getTxHash()).isEqualTo("0xFunding1");
    }
}

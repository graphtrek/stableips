package co.grtk.stableips.integration;

import co.grtk.stableips.model.Transaction;
import co.grtk.stableips.model.User;
import co.grtk.stableips.repository.TransactionRepository;
import co.grtk.stableips.repository.UserRepository;
import co.grtk.stableips.service.TransactionService;
import co.grtk.stableips.service.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for funding transaction flow
 * Tests the complete workflow of wallet creation, funding, and transaction recording
 *
 * NOTE: These tests will fail until the following are implemented:
 * 1. TransactionService.recordFundingTransaction() method
 * 2. TransactionService.getFundingTransactions() method
 * 3. TransactionService.getTransactionsByType() method
 * 4. TransactionRepository.findByUserIdAndStatusOrderByTimestampDesc() method
 * 5. TransactionRepository.findByUserIdAndTypeOrderByTimestampDesc() method
 * 6. Transaction model enhancement with 'type' field
 */
@SpringBootTest
@ActiveProfiles("test")
class FundingTransactionIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @AfterEach
    void cleanup() {
        transactionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldRecordEthFundingTransactionWhenWalletIsFunded() {
        // Given
        User user = walletService.createUserWithWallet("alice");
        String txHash = "0xEthFundingTx123";

        // When - Record ETH funding transaction
        Transaction fundingTx = transactionService.recordFundingTransaction(
            user.getId(),
            user.getWalletAddress(),
            new BigDecimal("10"),
            "ETH",
            "ETHEREUM",
            txHash,
            "FUNDING"
        );

        // Then
        assertThat(fundingTx).isNotNull();
        assertThat(fundingTx.getId()).isNotNull();

        // Verify transaction is persisted
        Transaction saved = transactionRepository.findByTxHash(txHash).orElse(null);
        assertThat(saved).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(user.getId());
        assertThat(saved.getRecipient()).isEqualTo(user.getWalletAddress());
        assertThat(saved.getToken()).isEqualTo("ETH");
    }

    @Test
    void shouldRecordAllFundingTransactionsForNewUser() {
        // Given
        User user = walletService.createUserWithWallet("bob");

        // When - Record all funding transactions (ETH, USDC, EURC)
        Transaction ethFunding = transactionService.recordFundingTransaction(
            user.getId(),
            user.getWalletAddress(),
            new BigDecimal("10"),
            "ETH",
            "ETHEREUM",
            "0xEthFund",
            "FUNDING"
        );

        Transaction usdcMinting = transactionService.recordFundingTransaction(
            user.getId(),
            user.getWalletAddress(),
            new BigDecimal("1000"),
            "TEST-USDC",
            "ETHEREUM",
            "0xUsdcMint",
            "MINTING"
        );

        Transaction eurcMinting = transactionService.recordFundingTransaction(
            user.getId(),
            user.getWalletAddress(),
            new BigDecimal("1000"),
            "TEST-EURC",
            "ETHEREUM",
            "0xEurcMint",
            "MINTING"
        );

        // Then
        List<Transaction> allTransactions = transactionService.getUserTransactions(user.getId());
        assertThat(allTransactions).hasSize(3);
        assertThat(allTransactions).extracting(Transaction::getToken)
            .containsExactlyInAnyOrder("ETH", "TEST-USDC", "TEST-EURC");
    }

    @Test
    void shouldRecordXrpFaucetFundingTransaction() {
        // Given
        User user = walletService.createUserWithWallet("charlie");
        String xrpAddress = user.getXrpAddress();

        // When - Record XRP faucet funding
        Transaction xrpFunding = transactionService.recordFundingTransaction(
            user.getId(),
            xrpAddress,
            new BigDecimal("1000"),
            "XRP",
            "XRP",
            "XRP_FAUCET_0x456",
            "FAUCET_FUNDING"
        );

        // Then
        assertThat(xrpFunding).isNotNull();

        // Verify we can find it by recipient
        List<Transaction> receivedTxs = transactionRepository.findByRecipientOrderByTimestampDesc(xrpAddress);
        assertThat(receivedTxs).hasSize(1);
        assertThat(receivedTxs.get(0).getNetwork()).isEqualTo("XRP");
    }

    @Test
    void shouldDistinguishBetweenFundingAndUserTransfers() {
        // Given
        User sender = walletService.createUserWithWallet("sender");
        User receiver = walletService.createUserWithWallet("receiver");

        // When - Record funding for sender
        Transaction ethFunding = transactionService.recordFundingTransaction(
            sender.getId(),
            sender.getWalletAddress(),
            new BigDecimal("10"),
            "ETH",
            "ETHEREUM",
            "0xFunding123",
            "FUNDING"
        );

        // Record a user-initiated transfer from sender to receiver
        // Note: This would normally go through initiateTransfer, but we're simulating
        Transaction userTransfer = new Transaction(
            sender.getId(),
            receiver.getWalletAddress(),
            new BigDecimal("5"),
            "USDC",
            "ETHEREUM",
            "0xTransfer456",
            "CONFIRMED"
        );
        transactionRepository.save(userTransfer);

        // Then - Sender should have 2 transactions (1 funding, 1 sent)
        List<Transaction> senderTxs = transactionService.getUserTransactions(sender.getId());
        assertThat(senderTxs).hasSize(2);

        // Receiver should have 1 received transaction
        List<Transaction> receivedTxs = transactionRepository
            .findByRecipientOrderByTimestampDesc(receiver.getWalletAddress());
        assertThat(receivedTxs).hasSize(1);
        assertThat(receivedTxs.get(0).getTxHash()).isEqualTo("0xTransfer456");
    }

    @Test
    void shouldGetAllReceivedTransactionsIncludingFunding() {
        // Given
        User user = walletService.createUserWithWallet("david");

        // When - Record multiple funding transactions
        transactionService.recordFundingTransaction(
            user.getId(),
            user.getWalletAddress(),
            new BigDecimal("10"),
            "ETH",
            "ETHEREUM",
            "0xEth",
            "FUNDING"
        );

        transactionService.recordFundingTransaction(
            user.getId(),
            user.getWalletAddress(),
            new BigDecimal("1000"),
            "TEST-USDC",
            "ETHEREUM",
            "0xUsdc",
            "MINTING"
        );

        transactionService.recordFundingTransaction(
            user.getId(),
            user.getXrpAddress(),
            new BigDecimal("1000"),
            "XRP",
            "XRP",
            "0xXrp",
            "FAUCET_FUNDING"
        );

        // Then - Get received transactions should include all funding
        List<Transaction> received = transactionService.getReceivedTransactions(user);
        assertThat(received).hasSize(3);
        assertThat(received).extracting(Transaction::getNetwork)
            .containsExactlyInAnyOrder("ETHEREUM", "ETHEREUM", "XRP");
    }

    @Test
    void shouldHandleFailedFundingTransaction() {
        // Given
        User user = walletService.createUserWithWallet("eve");

        // When - Record failed funding (null txHash)
        Transaction failedFunding = transactionService.recordFundingTransaction(
            user.getId(),
            user.getWalletAddress(),
            new BigDecimal("10"),
            "ETH",
            "ETHEREUM",
            null,  // Funding failed
            "FUNDING"
        );

        // Then
        assertThat(failedFunding).isNotNull();
        assertThat(failedFunding.getTxHash()).isNull();
        assertThat(failedFunding.getStatus()).isEqualTo("FAILED");

        // Verify it's persisted
        List<Transaction> userTxs = transactionService.getUserTransactions(user.getId());
        assertThat(userTxs).hasSize(1);
        assertThat(userTxs.get(0).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void shouldGetAllTransactionTypesCombined() {
        // Given
        User user = walletService.createUserWithWallet("frank");

        // When - Record funding
        transactionService.recordFundingTransaction(
            user.getId(),
            user.getWalletAddress(),
            new BigDecimal("10"),
            "ETH",
            "ETHEREUM",
            "0xFunding",
            "FUNDING"
        );

        // Simulate receiving a transfer from another user
        Transaction receivedTransfer = new Transaction(
            999L,  // Different user ID
            user.getWalletAddress(),
            new BigDecimal("50"),
            "USDC",
            "ETHEREUM",
            "0xReceived",
            "CONFIRMED"
        );
        transactionRepository.save(receivedTransfer);

        // Then - getAllUserTransactions should include both sent and received
        var allTxs = transactionService.getAllUserTransactions(user);
        assertThat(allTxs.get("sent")).hasSize(1);  // Funding transaction
        assertThat(allTxs.get("received")).hasSize(2);  // Funding + received transfer
    }

    @Test
    void shouldRecordMultipleNetworkFundingForSingleUser() {
        // Given
        User user = walletService.createUserWithWallet("grace");

        // When - Fund across all networks
        transactionService.recordFundingTransaction(
            user.getId(),
            user.getWalletAddress(),
            new BigDecimal("10"),
            "ETH",
            "ETHEREUM",
            "0xEthFund",
            "FUNDING"
        );

        transactionService.recordFundingTransaction(
            user.getId(),
            user.getXrpAddress(),
            new BigDecimal("1000"),
            "XRP",
            "XRP",
            "0xXrpFund",
            "FAUCET_FUNDING"
        );

        transactionService.recordFundingTransaction(
            user.getId(),
            user.getSolanaPublicKey(),
            new BigDecimal("2"),
            "SOL",
            "SOLANA",
            "0xSolFund",
            "FAUCET_FUNDING"
        );

        // Then
        List<Transaction> allTxs = transactionService.getUserTransactions(user.getId());
        assertThat(allTxs).hasSize(3);
        assertThat(allTxs).extracting(Transaction::getNetwork)
            .containsExactlyInAnyOrder("ETHEREUM", "XRP", "SOLANA");

        // Verify received transactions across all wallets
        List<Transaction> received = transactionService.getReceivedTransactions(user);
        assertThat(received).hasSize(3);
    }

    @Test
    void shouldQueryFundingTransactionsByNetwork() {
        // Given
        User user = walletService.createUserWithWallet("henry");

        // When - Record funding on different networks
        transactionService.recordFundingTransaction(
            user.getId(),
            user.getWalletAddress(),
            new BigDecimal("10"),
            "ETH",
            "ETHEREUM",
            "0xEth",
            "FUNDING"
        );

        transactionService.recordFundingTransaction(
            user.getId(),
            user.getXrpAddress(),
            new BigDecimal("1000"),
            "XRP",
            "XRP",
            "0xXrp",
            "FAUCET_FUNDING"
        );

        // Then - Should be able to query by network
        List<Transaction> allTxs = transactionRepository.findByUserId(user.getId());
        assertThat(allTxs).hasSize(2);

        // Filter Ethereum transactions
        List<Transaction> ethTxs = allTxs.stream()
            .filter(tx -> "ETHEREUM".equals(tx.getNetwork()))
            .toList();
        assertThat(ethTxs).hasSize(1);
        assertThat(ethTxs.get(0).getToken()).isEqualTo("ETH");

        // Filter XRP transactions
        List<Transaction> xrpTxs = allTxs.stream()
            .filter(tx -> "XRP".equals(tx.getNetwork()))
            .toList();
        assertThat(xrpTxs).hasSize(1);
        assertThat(xrpTxs.get(0).getToken()).isEqualTo("XRP");
    }
}

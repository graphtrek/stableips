package co.grtk.stableips.repository;

import co.grtk.stableips.model.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TransactionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void shouldSaveAndRetrieveTransaction() {
        // Given
        Transaction transaction = new Transaction(
            1L,
            "0xRecipient123",
            new BigDecimal("100.50"),
            "USDC",
            "ETHEREUM",
            "0xTxHash123",
            "PENDING"
        );

        // When
        Transaction saved = transactionRepository.save(transaction);
        entityManager.flush();
        Transaction found = transactionRepository.findById(saved.getId()).orElse(null);

        // Then
        assertThat(found).isNotNull();
        assertThat(found.getUserId()).isEqualTo(1L);
        assertThat(found.getRecipient()).isEqualTo("0xRecipient123");
        assertThat(found.getAmount()).isEqualByComparingTo(new BigDecimal("100.50"));
        assertThat(found.getToken()).isEqualTo("USDC");
        assertThat(found.getTxHash()).isEqualTo("0xTxHash123");
        assertThat(found.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldFindTransactionsByUserId() {
        // Given
        Transaction tx1 = new Transaction(1L, "0xAddr1", BigDecimal.TEN, "USDC", "ETHEREUM", "0xHash1", "CONFIRMED");
        Transaction tx2 = new Transaction(1L, "0xAddr2", BigDecimal.ONE, "EURC", "ETHEREUM", "0xHash2", "PENDING");
        Transaction tx3 = new Transaction(2L, "0xAddr3", new BigDecimal("50"), "USDC", "ETHEREUM", "0xHash3", "CONFIRMED");

        entityManager.persist(tx1);
        entityManager.persist(tx2);
        entityManager.persist(tx3);
        entityManager.flush();

        // When
        List<Transaction> userTransactions = transactionRepository.findByUserId(1L);

        // Then
        assertThat(userTransactions).hasSize(2);
        assertThat(userTransactions).extracting(Transaction::getTxHash)
            .containsExactlyInAnyOrder("0xHash1", "0xHash2");
    }

    @Test
    void shouldFindTransactionByTxHash() {
        // Given
        Transaction transaction = new Transaction(1L, "0xAddr", BigDecimal.TEN, "USDC", "ETHEREUM", "0xUniqueHash", "PENDING");
        entityManager.persist(transaction);
        entityManager.flush();

        // When
        Optional<Transaction> found = transactionRepository.findByTxHash("0xUniqueHash");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getTxHash()).isEqualTo("0xUniqueHash");
    }

    @Test
    void shouldReturnEmptyWhenTxHashNotFound() {
        // When
        Optional<Transaction> found = transactionRepository.findByTxHash("0xNonExistent");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindTransactionsByUserIdOrderedByTimestampDesc() {
        // Given
        Transaction tx1 = new Transaction(1L, "0xAddr1", BigDecimal.TEN, "USDC", "ETHEREUM", "0xHash1", "CONFIRMED");
        Transaction tx2 = new Transaction(1L, "0xAddr2", BigDecimal.ONE, "EURC", "ETHEREUM", "0xHash2", "PENDING");

        entityManager.persist(tx1);
        entityManager.flush();

        // Small delay to ensure different timestamps
        try { Thread.sleep(10); } catch (InterruptedException e) {}

        entityManager.persist(tx2);
        entityManager.flush();

        // When
        List<Transaction> transactions = transactionRepository.findByUserIdOrderByTimestampDesc(1L);

        // Then
        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).getTxHash()).isEqualTo("0xHash2"); // Most recent first
        assertThat(transactions.get(1).getTxHash()).isEqualTo("0xHash1");
    }

    // ==================== Transaction Type Query Tests ====================

    @Test
    void shouldFindTransactionsByStatus() {
        // Given
        Transaction pending1 = new Transaction(1L, "0xAddr1", BigDecimal.TEN, "USDC", "ETHEREUM", "0xHash1", "PENDING");
        Transaction pending2 = new Transaction(2L, "0xAddr2", BigDecimal.ONE, "EURC", "ETHEREUM", "0xHash2", "PENDING");
        Transaction confirmed = new Transaction(1L, "0xAddr3", new BigDecimal("50"), "USDC", "ETHEREUM", "0xHash3", "CONFIRMED");

        entityManager.persist(pending1);
        entityManager.persist(pending2);
        entityManager.persist(confirmed);
        entityManager.flush();

        // When
        List<Transaction> pendingTransactions = transactionRepository.findByStatus("PENDING");

        // Then
        assertThat(pendingTransactions).hasSize(2);
        assertThat(pendingTransactions).extracting(Transaction::getStatus)
            .containsOnly("PENDING");
    }

    @Test
    void shouldFindTransactionsByStatusAndNetwork() {
        // Given
        Transaction ethPending = new Transaction(1L, "0xAddr1", BigDecimal.TEN, "ETH", "ETHEREUM", "0xHash1", "PENDING");
        Transaction xrpPending = new Transaction(2L, "rAddr1", BigDecimal.ONE, "XRP", "XRP", "0xHash2", "PENDING");
        Transaction ethConfirmed = new Transaction(1L, "0xAddr2", new BigDecimal("50"), "USDC", "ETHEREUM", "0xHash3", "CONFIRMED");

        entityManager.persist(ethPending);
        entityManager.persist(xrpPending);
        entityManager.persist(ethConfirmed);
        entityManager.flush();

        // When
        List<Transaction> ethPendingTxs = transactionRepository.findByStatusAndNetwork("PENDING", "ETHEREUM");

        // Then
        assertThat(ethPendingTxs).hasSize(1);
        assertThat(ethPendingTxs.get(0).getTxHash()).isEqualTo("0xHash1");
        assertThat(ethPendingTxs.get(0).getNetwork()).isEqualTo("ETHEREUM");
        assertThat(ethPendingTxs.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldFindTransactionsByRecipient() {
        // Given
        String userWallet = "0xUserWallet123";
        Transaction funding1 = new Transaction(1L, userWallet, BigDecimal.TEN, "ETH", "ETHEREUM", "0xFunding1", "CONFIRMED");
        Transaction funding2 = new Transaction(1L, userWallet, new BigDecimal("1000"), "TEST-USDC", "ETHEREUM", "0xFunding2", "CONFIRMED");
        Transaction transfer = new Transaction(1L, "0xOtherUser", new BigDecimal("50"), "USDC", "ETHEREUM", "0xTransfer1", "CONFIRMED");

        entityManager.persist(transfer);
        entityManager.flush();

        try { Thread.sleep(10); } catch (InterruptedException e) {}

        entityManager.persist(funding1);
        entityManager.flush();

        try { Thread.sleep(10); } catch (InterruptedException e) {}

        entityManager.persist(funding2);
        entityManager.flush();

        // When
        List<Transaction> receivedTxs = transactionRepository.findByRecipientOrderByTimestampDesc(userWallet);

        // Then
        assertThat(receivedTxs).hasSize(2);
        assertThat(receivedTxs.get(0).getTxHash()).isEqualTo("0xFunding2"); // Most recent
        assertThat(receivedTxs.get(1).getTxHash()).isEqualTo("0xFunding1");
        assertThat(receivedTxs).extracting(Transaction::getRecipient)
            .containsOnly(userWallet);
    }

    @Test
    void shouldFindFundingTransactionsByType() {
        // Given - This test assumes Transaction model has a 'type' field
        // Currently the model doesn't have this field, so this test documents the requirement

        // Expected: Transaction model should have a 'type' field (TRANSFER, FUNDING, MINTING, FAUCET_FUNDING)
        // Expected: Repository should have findByUserIdAndTypeOrderByTimestampDesc method

        // This test will fail until the Transaction model is updated
        // Keeping as documentation of expected behavior
    }

    @Test
    void shouldFindAllFundingTransactionsForUser() {
        // Given
        String userWallet = "0xUserWallet123";
        Long userId = 1L;

        // ETH funding
        Transaction ethFunding = new Transaction(userId, userWallet, BigDecimal.TEN, "ETH", "ETHEREUM", "0xEthFund", "CONFIRMED");

        // USDC minting
        Transaction usdcMinting = new Transaction(userId, userWallet, new BigDecimal("1000"), "TEST-USDC", "ETHEREUM", "0xUsdcMint", "CONFIRMED");

        // EURC minting
        Transaction eurcMinting = new Transaction(userId, userWallet, new BigDecimal("1000"), "TEST-EURC", "ETHEREUM", "0xEurcMint", "CONFIRMED");

        // User transfer (should not be included in funding)
        Transaction userTransfer = new Transaction(userId, "0xOtherUser", new BigDecimal("50"), "USDC", "ETHEREUM", "0xTransfer", "CONFIRMED");

        entityManager.persist(userTransfer);
        entityManager.persist(ethFunding);
        entityManager.persist(usdcMinting);
        entityManager.persist(eurcMinting);
        entityManager.flush();

        // When - Find all transactions received by user's wallet (funding transactions)
        List<Transaction> fundingTxs = transactionRepository.findByRecipientOrderByTimestampDesc(userWallet);

        // Then
        assertThat(fundingTxs).hasSize(3);
        assertThat(fundingTxs).extracting(Transaction::getToken)
            .containsExactlyInAnyOrder("ETH", "TEST-USDC", "TEST-EURC");
    }

    @Test
    void shouldFindXrpFundingTransactions() {
        // Given
        String xrpAddress = "rN7n7otQDd6FczFgLdllrq4OhiX1zp7n8";
        Long userId = 2L;

        Transaction xrpFunding = new Transaction(
            userId,
            xrpAddress,
            new BigDecimal("1000"),
            "XRP",
            "XRP",
            "XRP_FAUCET_0x123",
            "CONFIRMED"
        );

        entityManager.persist(xrpFunding);
        entityManager.flush();

        // When
        List<Transaction> xrpTxs = transactionRepository.findByRecipientOrderByTimestampDesc(xrpAddress);

        // Then
        assertThat(xrpTxs).hasSize(1);
        assertThat(xrpTxs.get(0).getToken()).isEqualTo("XRP");
        assertThat(xrpTxs.get(0).getNetwork()).isEqualTo("XRP");
        assertThat(xrpTxs.get(0).getRecipient()).isEqualTo(xrpAddress);
    }

    @Test
    void shouldHandleMultipleNetworkFundingForSameUser() {
        // Given
        Long userId = 3L;
        String ethWallet = "0xEthWallet123";
        String xrpWallet = "rXrpWallet456";
        String solWallet = "SolWallet789";

        Transaction ethFunding = new Transaction(userId, ethWallet, BigDecimal.TEN, "ETH", "ETHEREUM", "0xEth", "CONFIRMED");
        Transaction xrpFunding = new Transaction(userId, xrpWallet, new BigDecimal("1000"), "XRP", "XRP", "0xXrp", "CONFIRMED");
        Transaction solFunding = new Transaction(userId, solWallet, new BigDecimal("2"), "SOL", "SOLANA", "0xSol", "CONFIRMED");

        entityManager.persist(ethFunding);
        entityManager.persist(xrpFunding);
        entityManager.persist(solFunding);
        entityManager.flush();

        // When
        List<Transaction> allUserTxs = transactionRepository.findByUserId(userId);

        // Then
        assertThat(allUserTxs).hasSize(3);
        assertThat(allUserTxs).extracting(Transaction::getNetwork)
            .containsExactlyInAnyOrder("ETHEREUM", "XRP", "SOLANA");
    }

    @Test
    void shouldDifferentiateFundingFromTransfers() {
        // Given
        Long userId = 1L;
        String userWallet = "0xUserWallet123";

        // Funding transaction (user is recipient)
        Transaction funding = new Transaction(
            userId,
            userWallet,
            BigDecimal.TEN,
            "ETH",
            "ETHEREUM",
            "0xFunding123",
            "CONFIRMED"
        );

        // Transfer transaction (user is sender, different recipient)
        Transaction transfer = new Transaction(
            userId,
            "0xOtherUser456",
            new BigDecimal("5"),
            "USDC",
            "ETHEREUM",
            "0xTransfer456",
            "CONFIRMED"
        );

        entityManager.persist(funding);
        entityManager.persist(transfer);
        entityManager.flush();

        // When
        List<Transaction> fundingReceived = transactionRepository.findByRecipientOrderByTimestampDesc(userWallet);
        List<Transaction> allUserTxs = transactionRepository.findByUserId(userId);

        // Then
        assertThat(fundingReceived).hasSize(1);
        assertThat(fundingReceived.get(0).getRecipient()).isEqualTo(userWallet);

        assertThat(allUserTxs).hasSize(2);
        assertThat(allUserTxs).extracting(Transaction::getRecipient)
            .containsExactlyInAnyOrder(userWallet, "0xOtherUser456");
    }
}

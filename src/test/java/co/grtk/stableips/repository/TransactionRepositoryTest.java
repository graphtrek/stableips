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
        Transaction tx1 = new Transaction(1L, "0xAddr1", BigDecimal.TEN, "USDC", "0xHash1", "CONFIRMED");
        Transaction tx2 = new Transaction(1L, "0xAddr2", BigDecimal.ONE, "DAI", "0xHash2", "PENDING");
        Transaction tx3 = new Transaction(2L, "0xAddr3", new BigDecimal("50"), "USDC", "0xHash3", "CONFIRMED");

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
        Transaction transaction = new Transaction(1L, "0xAddr", BigDecimal.TEN, "USDC", "0xUniqueHash", "PENDING");
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
        Transaction tx1 = new Transaction(1L, "0xAddr1", BigDecimal.TEN, "USDC", "0xHash1", "CONFIRMED");
        Transaction tx2 = new Transaction(1L, "0xAddr2", BigDecimal.ONE, "DAI", "0xHash2", "PENDING");

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
}

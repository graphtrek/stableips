package co.grtk.stableips.repository;

import co.grtk.stableips.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserId(Long userId);

    Optional<Transaction> findByTxHash(String txHash);

    List<Transaction> findByUserIdOrderByTimestampDesc(Long userId);

    /**
     * Find all transactions with a specific status
     * Used by TransactionMonitoringService to find PENDING transactions
     */
    List<Transaction> findByStatus(String status);

    /**
     * Find transactions by status and network
     * Useful for monitoring specific blockchain networks
     */
    List<Transaction> findByStatusAndNetwork(String status, String network);
}

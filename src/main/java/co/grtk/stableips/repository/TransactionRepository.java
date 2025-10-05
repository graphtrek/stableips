package co.grtk.stableips.repository;

import co.grtk.stableips.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for accessing and querying Transaction entities.
 *
 * <p>This repository provides query methods for retrieving transaction records
 * across multiple dimensions: user, status, network, type, and recipient address.
 * It supports transaction monitoring, history display, and blockchain-specific queries.</p>
 *
 * <p>All query methods use Spring Data JPA naming conventions for automatic
 * query generation, eliminating the need for manual JPQL queries.</p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 * @see Transaction
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Finds all transactions initiated by a specific user.
     *
     * @param userId the ID of the user
     * @return list of transactions where the user is the sender
     */
    List<Transaction> findByUserId(Long userId);

    /**
     * Finds a transaction by its blockchain transaction hash.
     *
     * @param txHash the blockchain transaction hash (unique identifier)
     * @return optional containing the transaction if found, empty otherwise
     */
    Optional<Transaction> findByTxHash(String txHash);

    /**
     * Finds all transactions initiated by a user, ordered by timestamp descending.
     *
     * @param userId the ID of the user
     * @return list of transactions ordered by most recent first
     */
    List<Transaction> findByUserIdOrderByTimestampDesc(Long userId);

    /**
     * Finds all transactions with a specific status.
     *
     * <p>Primarily used by TransactionMonitoringService to find PENDING transactions
     * that need confirmation status updates from the blockchain.</p>
     *
     * @param status the transaction status (PENDING, CONFIRMED, FAILED)
     * @return list of transactions with the specified status
     */
    List<Transaction> findByStatus(String status);

    /**
     * Finds transactions by status and blockchain network.
     *
     * <p>Useful for monitoring specific blockchain networks or filtering
     * pending transactions by network type for targeted status updates.</p>
     *
     * @param status the transaction status (PENDING, CONFIRMED, FAILED)
     * @param network the blockchain network (ETHEREUM, XRP, SOLANA)
     * @return list of matching transactions
     */
    List<Transaction> findByStatusAndNetwork(String status, String network);

    /**
     * Finds all transactions received by a specific wallet address.
     *
     * <p>Queries for transactions where the specified address is the recipient,
     * regardless of the sender. Results are ordered by timestamp descending
     * (newest first). This is used to build "received transactions" history.</p>
     *
     * @param recipient the wallet address that received the transactions
     * @return list of received transactions ordered by most recent first
     */
    List<Transaction> findByRecipientOrderByTimestampDesc(String recipient);

    /**
     * Finds transactions by user and status, ordered by timestamp descending.
     *
     * <p>Allows filtering a user's transactions by their confirmation status,
     * useful for displaying only confirmed or pending transactions.</p>
     *
     * @param userId the ID of the user
     * @param status the transaction status filter
     * @return list of matching transactions ordered by most recent first
     */
    List<Transaction> findByUserIdAndStatusOrderByTimestampDesc(Long userId, String status);

    /**
     * Finds transactions by user and transaction type, ordered by timestamp descending.
     *
     * <p>Filters transactions by their type field (TRANSFER, FUNDING, MINTING, etc.),
     * useful for separating user-initiated transfers from system-initiated funding.</p>
     *
     * @param userId the ID of the user
     * @param type the transaction type (TRANSFER, FUNDING, MINTING, FAUCET_FUNDING)
     * @return list of matching transactions ordered by most recent first
     */
    List<Transaction> findByUserIdAndTypeOrderByTimestampDesc(Long userId, String type);

    /**
     * Finds transactions by user and multiple transaction types.
     *
     * <p>Allows querying for multiple transaction types in a single query.
     * For example, retrieving all funding-related transactions (FUNDING, MINTING,
     * FAUCET_FUNDING) for a user.</p>
     *
     * @param userId the ID of the user
     * @param types list of transaction types to include
     * @return list of matching transactions ordered by most recent first
     */
    List<Transaction> findByUserIdAndTypeInOrderByTimestampDesc(Long userId, List<String> types);

    /**
     * Finds transactions by status and multiple transaction types.
     *
     * <p>Used by transaction monitoring services to find pending transactions
     * of specific types that need status updates. For example, finding all
     * pending funding transactions.</p>
     *
     * @param status the transaction status filter
     * @param types list of transaction types to include
     * @return list of matching transactions ordered by most recent first
     */
    List<Transaction> findByStatusAndTypeInOrderByTimestampDesc(String status, List<String> types);
}

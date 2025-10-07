package co.grtk.stableips.service;

import co.grtk.stableips.model.Transaction;
import co.grtk.stableips.model.User;
import co.grtk.stableips.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Service for managing cryptocurrency transactions across multiple blockchain networks.
 *
 * <p>This service handles transaction initiation, status tracking, and transaction history
 * for users across Ethereum, XRP Ledger, and Solana networks. It coordinates with
 * blockchain-specific services (WalletService, XrpWalletService, SolanaWalletService)
 * to execute transfers and maintains a unified transaction log in the database.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Multi-blockchain transaction initiation (Ethereum, XRP, Solana)</li>
 *   <li>Transaction status management (PENDING, CONFIRMED, FAILED)</li>
 *   <li>Transaction history tracking (sent, received, funding)</li>
 *   <li>Token balance queries for ERC-20 tokens</li>
 *   <li>System-initiated funding transaction logging</li>
 * </ul>
 *
 * <p>Supported networks:
 * <ul>
 *   <li>ETHEREUM: ETH, USDC, EURC, TEST-USDC, TEST-EURC</li>
 *   <li>XRP: Native XRP transfers</li>
 *   <li>SOLANA: SOL transfers</li>
 * </ul>
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
@Service
@Transactional
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final ContractService contractService;
    private final XrpWalletService xrpWalletService;
    private final SolanaWalletService solanaWalletService;

    /**
     * Constructs a TransactionService with required blockchain service dependencies.
     *
     * @param transactionRepository repository for transaction persistence and queries
     * @param walletService service for Ethereum wallet operations and credentials
     * @param contractService service for ERC-20 token contract interactions
     * @param xrpWalletService service for XRP Ledger wallet operations
     * @param solanaWalletService service for Solana blockchain operations
     */
    public TransactionService(
        TransactionRepository transactionRepository,
        WalletService walletService,
        ContractService contractService,
        XrpWalletService xrpWalletService,
        SolanaWalletService solanaWalletService
    ) {
        this.transactionRepository = transactionRepository;
        this.walletService = walletService;
        this.contractService = contractService;
        this.xrpWalletService = xrpWalletService;
        this.solanaWalletService = solanaWalletService;
    }

    /**
     * Initiates a cryptocurrency transfer across any supported blockchain network.
     *
     * <p>This method routes the transfer to the appropriate blockchain service based on
     * the token type. It supports Solana (SOL), XRP Ledger (XRP), and Ethereum-based
     * tokens (ETH, USDC, EURC). The transaction is logged with PENDING status and saved
     * to the database for tracking.</p>
     *
     * <p>Transfer routing:
     * <ul>
     *   <li>SOL: Routed to {@link SolanaWalletService}</li>
     *   <li>XRP: Routed to {@link XrpWalletService}</li>
     *   <li>ETH, USDC, EURC: Routed to {@link ContractService}</li>
     * </ul>
     *
     * @param user the authenticated user initiating the transfer
     * @param recipient the destination wallet address (format varies by blockchain)
     * @param amount the transfer amount in token units (e.g., 100.50 USDC)
     * @param token the token symbol (SOL, XRP, ETH, USDC, EURC)
     * @return the persisted Transaction entity with PENDING status and transaction hash
     * @throws RuntimeException if the blockchain transfer fails or credentials are invalid
     * @throws IllegalArgumentException if the token type is unsupported
     */
    public Transaction initiateTransfer(User user, String recipient, BigDecimal amount, String token) {
        String txHash;
        String network;

        if (isSolanaTransfer(token)) {
            txHash = executeSolanaTransfer(user, recipient, amount);
            network = "SOLANA";
        } else if (isXrpTransfer(token)) {
            txHash = executeXrpTransfer(user, recipient, amount);
            network = "XRP";
        } else {
            txHash = executeEthereumTransfer(user, recipient, amount, token);
            network = "ETHEREUM";
        }

        Transaction transaction = new Transaction(
            user.getId(),
            recipient,
            amount,
            token,
            network,
            txHash,
            "PENDING"
        );

        return transactionRepository.save(transaction);
    }

    private boolean isSolanaTransfer(String token) {
        return "SOL".equalsIgnoreCase(token);
    }

    private boolean isXrpTransfer(String token) {
        return "XRP".equalsIgnoreCase(token);
    }

    private String executeSolanaTransfer(User user, String recipient, BigDecimal amount) {
        return solanaWalletService.sendSol(user.getSolanaPrivateKey(), recipient, amount);
    }

    /**
     * Executes an XRP transfer using the XrpWalletService with enhanced error handling.
     *
     * <p>This method wraps {@link XrpWalletService#sendXrp(String, String, BigDecimal)}
     * to provide user-friendly error messages when seed parsing failures occur. It catches
     * {@link IllegalArgumentException} from corrupted seed formats and re-throws with
     * additional context directing users to the wallet dashboard.</p>
     *
     * <p>Common error scenarios handled:
     * <ul>
     *   <li>Corrupted seed format (Seed.toString() output)</li>
     *   <li>Legacy address-only storage</li>
     *   <li>Unsupported seed encoding</li>
     * </ul>
     * </p>
     *
     * @param user the user initiating the transfer (must have valid XRP secret)
     * @param recipient the destination XRP address
     * @param amount the transfer amount in XRP units
     * @return the blockchain transaction hash
     * @throws IllegalArgumentException if the user's XRP seed is corrupted or invalid,
     *         with actionable error message directing to wallet regeneration
     * @throws RuntimeException if the XRP transfer fails for other reasons (network error,
     *         insufficient balance, invalid recipient address)
     */
    private String executeXrpTransfer(User user, String recipient, BigDecimal amount) {
        try {
            return xrpWalletService.sendXrp(user.getXrpSecret(), recipient, amount);
        } catch (IllegalArgumentException e) {
            // Re-throw with user-friendly context for seed parsing errors
            log.error("XRP transfer failed for user {}: {}", user.getId(), e.getMessage());
            throw new IllegalArgumentException(
                "XRP transfer failed: " + e.getMessage() +
                " Visit your wallet dashboard to regenerate your XRP wallet.",
                e
            );
        }
    }

    private String executeEthereumTransfer(User user, String recipient, BigDecimal amount, String token) {
        Credentials credentials = walletService.getUserCredentials(user.getWalletAddress());
        return contractService.transfer(credentials, recipient, amount, token);
    }

    /**
     * Retrieves all transactions sent by a user.
     *
     * <p>Returns transactions where the user is the sender, ordered by timestamp
     * descending (most recent first). This includes all blockchain networks.</p>
     *
     * @param userId the ID of the user whose sent transactions to retrieve
     * @return list of sent transactions ordered by timestamp descending
     */
    public List<Transaction> getUserTransactions(Long userId) {
        return transactionRepository.findByUserIdOrderByTimestampDesc(userId);
    }

    /**
     * Retrieves all transactions received by a user across all their wallet addresses.
     *
     * <p>This method queries for transactions where the user's wallet addresses
     * (Ethereum, XRP, Solana) are the recipient. It aggregates received transactions
     * from all blockchain networks and sorts them by timestamp descending.</p>
     *
     * <p>Wallets checked:
     * <ul>
     *   <li>Ethereum wallet address (if present)</li>
     *   <li>XRP wallet address (if present)</li>
     *   <li>Solana public key (if present)</li>
     * </ul>
     *
     * @param user the user whose received transactions to fetch
     * @return list of received transactions ordered by timestamp descending
     */
    public List<Transaction> getReceivedTransactions(User user) {
        List<Transaction> received = new java.util.ArrayList<>();

        // Get transactions received by Ethereum wallet
        if (user.getWalletAddress() != null) {
            received.addAll(transactionRepository.findByRecipientOrderByTimestampDesc(user.getWalletAddress()));
        }

        // Get transactions received by XRP wallet
        if (user.getXrpAddress() != null) {
            received.addAll(transactionRepository.findByRecipientOrderByTimestampDesc(user.getXrpAddress()));
        }

        // Get transactions received by Solana wallet
        if (user.getSolanaPublicKey() != null) {
            received.addAll(transactionRepository.findByRecipientOrderByTimestampDesc(user.getSolanaPublicKey()));
        }

        // Sort all received transactions by timestamp descending
        received.sort(createTimestampComparator());

        return received;
    }

    /**
     * Retrieves all transactions for a user, categorized by direction and combined.
     *
     * <p>This method provides a complete transaction history for a user by fetching
     * transactions they initiated (sent) and transactions they received across all
     * their wallet addresses (Ethereum, XRP, Solana). The results are organized in
     * a map with three keys:</p>
     *
     * <ul>
     *   <li><strong>sent</strong>: User-initiated transfers ordered by timestamp descending</li>
     *   <li><strong>received</strong>: Incoming transfers across all wallets ordered by timestamp descending</li>
     *   <li><strong>all</strong>: Combined sent + received transactions merged and sorted by timestamp descending</li>
     * </ul>
     *
     * <p>The "all" key provides a unified transaction timeline useful for dashboard displays.</p>
     *
     * <p><strong>Note</strong>: This method does NOT include funding transactions (FUNDING, MINTING, FAUCET_FUNDING).
     * To include those, use {@link #getFundingTransactions(Long)} separately.</p>
     *
     * <p>Use case: Dashboard displays showing complete user-initiated and received transaction activity.</p>
     *
     * @param user the user whose complete transaction history to fetch
     * @return map with three keys ("sent", "received", "all"), each containing a list of
     *         transactions ordered by timestamp descending (newest first)
     */
    public Map<String, List<Transaction>> getAllUserTransactions(User user) {
        List<Transaction> sent = getUserTransactions(user.getId());
        List<Transaction> received = getReceivedTransactions(user);

        // Merge sent and received transactions into a unified timeline
        List<Transaction> mergedTransactions = new java.util.ArrayList<>();
        mergedTransactions.addAll(sent);
        mergedTransactions.addAll(received);
        mergedTransactions.sort(createTimestampComparator());

        return Map.of(
            "sent", sent,
            "received", received,
            "all", mergedTransactions
        );
    }

    /**
     * Creates a comparator for sorting transactions by timestamp in descending order.
     *
     * <p>This comparator ensures transactions are sorted from newest to oldest,
     * which is the standard display order for transaction histories.</p>
     *
     * @return comparator that sorts transactions by timestamp descending
     */
    private java.util.Comparator<Transaction> createTimestampComparator() {
        return (a, b) -> b.getTimestamp().compareTo(a.getTimestamp());
    }

    /**
     * Retrieves a transaction by its blockchain transaction hash.
     *
     * <p>This method looks up a transaction using its unique blockchain transaction hash
     * (e.g., Ethereum tx hash, XRP tx hash, or Solana signature). The hash must exactly
     * match a stored transaction.</p>
     *
     * @param txHash the blockchain transaction hash to search for
     * @return the transaction with the specified hash
     * @throws RuntimeException if no transaction is found with the given hash
     */
    public Transaction getTransactionByHash(String txHash) {
        return transactionRepository.findByTxHash(txHash)
            .orElseThrow(() -> new RuntimeException("Transaction not found for hash: " + txHash));
    }

    /**
     * Updates the status of an existing transaction.
     *
     * <p>This method is used to update transaction status as it progresses through
     * blockchain confirmation. Common status values: PENDING, CONFIRMED, FAILED.</p>
     *
     * <p>Note: This method is typically called by the TransactionMonitoringService
     * to update transaction states based on blockchain confirmation status.</p>
     *
     * @param transactionId the database ID of the transaction to update
     * @param status the new status value (e.g., "CONFIRMED", "FAILED")
     * @return the updated transaction entity
     * @throws RuntimeException if no transaction is found with the given ID
     */
    public Transaction updateTransactionStatus(Long transactionId, String status) {
        Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new RuntimeException("Transaction not found for ID: " + transactionId));

        transaction.setStatus(status);
        return transactionRepository.save(transaction);
    }

    /**
     * Retrieves the token balance for a specific ERC-20 token at a wallet address.
     *
     * <p>This method queries the blockchain to get the current balance of an ERC-20
     * token (USDC, EURC) at the specified Ethereum wallet address. For ETH balance,
     * use {@link WalletService#getEthBalance(String)} instead.</p>
     *
     * @param walletAddress the Ethereum wallet address to check
     * @param token the token symbol (USDC, EURC)
     * @return the token balance in token units (e.g., 100.50 USDC)
     */
    public BigDecimal getTokenBalance(String walletAddress, String token) {
        return contractService.getBalance(walletAddress, token);
    }

    /**
     * Records a system-initiated funding transaction in the database.
     *
     * <p>This method creates a transaction record for system-initiated funding operations
     * such as initial wallet funding, test token minting, or faucet requests. The transaction
     * status is automatically determined based on whether a transaction hash is present.</p>
     *
     * <p>Funding types:
     * <ul>
     *   <li>FUNDING: ETH sent from system funding wallet</li>
     *   <li>MINTING: Test tokens (TEST-USDC, TEST-EURC) minted to user wallet</li>
     *   <li>FAUCET_FUNDING: XRP received from testnet faucet</li>
     * </ul>
     *
     * <p>Status determination:
     * <ul>
     *   <li>CONFIRMED: If txHash is present and non-empty</li>
     *   <li>FAILED: If txHash is null or empty</li>
     * </ul>
     *
     * @param userId the ID of the user receiving the funding
     * @param recipientAddress the wallet address receiving the funds
     * @param amount the funding amount in token units
     * @param token the token type (ETH, XRP, SOL, TEST-USDC, TEST-EURC)
     * @param network the blockchain network (ETHEREUM, XRP, SOLANA)
     * @param txHash the blockchain transaction hash (null if funding failed)
     * @param fundingType the type of funding operation (FUNDING, MINTING, FAUCET_FUNDING)
     * @return the saved transaction record with appropriate status
     */
    public Transaction recordFundingTransaction(
        Long userId,
        String recipientAddress,
        BigDecimal amount,
        String token,
        String network,
        String txHash,
        String fundingType
    ) {
        // Determine status based on txHash presence
        String status = (txHash != null && !txHash.isEmpty()) ? "CONFIRMED" : "FAILED";

        Transaction transaction = new Transaction(
            userId,
            recipientAddress,
            amount,
            token,
            network,
            txHash,
            status
        );

        transaction.setType(fundingType);

        log.info("Recording {} transaction: user={}, token={}, amount={}, network={}, txHash={}, status={}",
            fundingType, userId, token, amount, network, txHash, status);

        return transactionRepository.save(transaction);
    }

    /**
     * Retrieves all funding transactions for a user.
     *
     * <p>Returns all system-initiated funding operations for the specified user,
     * including ETH funding, test token minting, and faucet funding. This excludes
     * user-initiated transfers.</p>
     *
     * <p>Included transaction types:
     * <ul>
     *   <li>FUNDING: System wallet funding (ETH)</li>
     *   <li>MINTING: Test token minting (TEST-USDC, TEST-EURC)</li>
     *   <li>FAUCET_FUNDING: Faucet funding (XRP, SOL)</li>
     *   <li>EXTERNAL_FUNDING: External funding from faucets or other sources</li>
     * </ul>
     *
     * @param userId the ID of the user whose funding transactions to retrieve
     * @return list of funding transactions ordered by timestamp descending
     */
    public List<Transaction> getFundingTransactions(Long userId) {
        return transactionRepository.findByUserIdAndTypeInOrderByTimestampDesc(
            userId,
            List.of("FUNDING", "MINTING", "FAUCET_FUNDING", "EXTERNAL_FUNDING")
        );
    }

    /**
     * Retrieves transactions filtered by a specific transaction type.
     *
     * <p>Allows filtering transactions by their type field. Common types include
     * TRANSFER (user-initiated), FUNDING, MINTING, and FAUCET_FUNDING.</p>
     *
     * @param userId the ID of the user whose transactions to filter
     * @param type the transaction type to filter by (TRANSFER, FUNDING, MINTING, FAUCET_FUNDING)
     * @return list of matching transactions ordered by timestamp descending
     */
    public List<Transaction> getTransactionsByType(Long userId, String type) {
        return transactionRepository.findByUserIdAndTypeOrderByTimestampDesc(userId, type);
    }
}

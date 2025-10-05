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

@Service
@Transactional
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final ContractService contractService;
    private final XrpWalletService xrpWalletService;
    private final SolanaWalletService solanaWalletService;

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

    public Transaction initiateTransfer(User user, String recipient, BigDecimal amount, String token) {
        String txHash;
        String network;

        // Handle SOL transfers
        if ("SOL".equalsIgnoreCase(token)) {
            txHash = solanaWalletService.sendSol(user.getSolanaPrivateKey(), recipient, amount);
            network = "SOLANA";
        }
        // Handle XRP transfers
        else if ("XRP".equalsIgnoreCase(token)) {
            txHash = xrpWalletService.sendXrp(user.getXrpSecret(), recipient, amount);
            network = "XRP";
        }
        // Handle Ethereum-based transfers (ETH, USDC, DAI)
        else {
            Credentials credentials = walletService.getUserCredentials(user.getWalletAddress());
            txHash = contractService.transfer(credentials, recipient, amount, token);
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

    public List<Transaction> getUserTransactions(Long userId) {
        return transactionRepository.findByUserIdOrderByTimestampDesc(userId);
    }

    /**
     * Get all transactions received by a user's wallets (Ethereum, XRP, Solana)
     * @param user The user whose received transactions to fetch
     * @return List of received transactions ordered by timestamp descending
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
        received.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

        return received;
    }

    /**
     * Get all transactions for a user (both sent and received)
     * Each transaction is marked with a "type" field for display purposes
     * @param user The user whose transactions to fetch
     * @return Combined list of sent and received transactions
     */
    public java.util.Map<String, List<Transaction>> getAllUserTransactions(User user) {
        List<Transaction> sent = getUserTransactions(user.getId());
        List<Transaction> received = getReceivedTransactions(user);

        return java.util.Map.of(
            "sent", sent,
            "received", received
        );
    }

    public Transaction getTransactionByHash(String txHash) {
        return transactionRepository.findByTxHash(txHash)
            .orElseThrow(() -> new RuntimeException("Transaction not found for hash: " + txHash));
    }

    public Transaction updateTransactionStatus(Long transactionId, String status) {
        Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new RuntimeException("Transaction not found for ID: " + transactionId));

        transaction.setStatus(status);
        return transactionRepository.save(transaction);
    }

    public BigDecimal getTokenBalance(String walletAddress, String token) {
        return contractService.getBalance(walletAddress, token);
    }

    /**
     * Record a system-initiated funding transaction
     * @param userId User receiving the funding
     * @param recipientAddress Wallet address receiving the funds
     * @param amount Amount of funding
     * @param token Token type (ETH, XRP, SOL, TEST-USDC, TEST-DAI)
     * @param network Network name (ETHEREUM, XRP, SOLANA)
     * @param txHash Transaction hash (null if failed)
     * @param fundingType Type of funding (FUNDING, MINTING, FAUCET_FUNDING)
     * @return Saved transaction record
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
     * Get all funding transactions for a user
     * Includes FUNDING, MINTING, and FAUCET_FUNDING types
     * @param userId User ID
     * @return List of funding transactions ordered by timestamp descending
     */
    public List<Transaction> getFundingTransactions(Long userId) {
        return transactionRepository.findByUserIdAndTypeInOrderByTimestampDesc(
            userId,
            List.of("FUNDING", "MINTING", "FAUCET_FUNDING")
        );
    }

    /**
     * Get transactions filtered by type
     * @param userId User ID
     * @param type Transaction type (TRANSFER, FUNDING, MINTING, FAUCET_FUNDING)
     * @return List of transactions ordered by timestamp descending
     */
    public List<Transaction> getTransactionsByType(Long userId, String type) {
        return transactionRepository.findByUserIdAndTypeOrderByTimestampDesc(userId, type);
    }
}

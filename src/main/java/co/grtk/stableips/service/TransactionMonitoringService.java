package co.grtk.stableips.service;

import co.grtk.stableips.model.Transaction;
import co.grtk.stableips.model.User;
import co.grtk.stableips.repository.TransactionRepository;
import co.grtk.stableips.repository.UserRepository;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;
import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.model.client.transactions.TransactionRequestParams;
import org.xrpl.xrpl4j.model.client.transactions.TransactionResult;
import org.xrpl.xrpl4j.model.transactions.Hash256;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * Service to monitor pending transactions and update their status
 * Runs periodically to check blockchain for transaction confirmations
 */
@Service
public class TransactionMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(TransactionMonitoringService.class);

    private static final int REQUIRED_CONFIRMATIONS = 3; // Conservative for testnet
    private static final int MAX_MONITORING_AGE_HOURS = 24; // Stop monitoring after 24 hours

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final Web3j web3j;
    private final XrplClient xrplClient;
    private final RpcClient solanaClient;
    private final TransactionService transactionService;

    public TransactionMonitoringService(
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            Web3j web3j,
            XrplClient xrplClient,
            TransactionService transactionService) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.web3j = web3j;
        this.xrplClient = xrplClient;
        this.solanaClient = new RpcClient(org.p2p.solanaj.rpc.Cluster.DEVNET);
        this.transactionService = transactionService;
    }

    /**
     * Scheduled task to monitor pending transactions
     * Runs every 30 seconds
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    @Transactional
    public void monitorPendingTransactions() {
        log.debug("Starting transaction monitoring cycle");

        List<Transaction> pendingTransactions = transactionRepository.findByStatus("PENDING");

        if (pendingTransactions.isEmpty()) {
            log.debug("No pending transactions to monitor");
        } else {
            log.info("Monitoring {} pending transactions", pendingTransactions.size());

            for (Transaction tx : pendingTransactions) {
                try {
                    // Check if transaction is too old
                    if (isTransactionTooOld(tx)) {
                        log.warn("Transaction {} is older than {} hours, marking as TIMEOUT",
                                tx.getTxHash(), MAX_MONITORING_AGE_HOURS);
                        transactionService.updateTransactionStatus(tx.getId(), "TIMEOUT");
                        continue;
                    }

                    // Route to appropriate blockchain checker
                    switch (tx.getNetwork().toUpperCase()) {
                        case "ETHEREUM", "SEPOLIA" -> checkEthereumTransaction(tx);
                        case "XRP", "XRPL" -> checkXrpTransaction(tx);
                        case "SOLANA", "SOL" -> checkSolanaTransaction(tx);
                        default -> log.warn("Unknown network for transaction {}: {}", tx.getTxHash(), tx.getNetwork());
                    }

                } catch (Exception e) {
                    log.error("Error monitoring transaction {}: {}", tx.getTxHash(), e.getMessage(), e);
                }
            }
        }

        // Scan for external incoming transactions
        scanForIncomingTransactions();

        log.debug("Transaction monitoring cycle completed");
    }

    /**
     * Scans blockchain for incoming transactions that weren't initiated by the application
     * This detects external funding like faucet transactions
     */
    private void scanForIncomingTransactions() {
        try {
            log.debug("Scanning for external incoming transactions");

            // Get latest block number
            BigInteger latestBlockNumber = web3j.ethBlockNumber().send().getBlockNumber();

            // Scan last 10 blocks for incoming transactions to our users
            BigInteger scanFromBlock = latestBlockNumber.subtract(BigInteger.valueOf(10));
            if (scanFromBlock.compareTo(BigInteger.ZERO) < 0) {
                scanFromBlock = BigInteger.ZERO;
            }

            // Get all user wallet addresses
            List<User> users = userRepository.findAll();

            for (User user : users) {
                if (user.getWalletAddress() == null) continue;

                scanEthereumIncomingTransactions(user, scanFromBlock, latestBlockNumber);
            }

        } catch (Exception e) {
            log.error("Error scanning for incoming transactions: {}", e.getMessage());
        }
    }

    /**
     * Scans Ethereum blockchain for incoming transactions to a user's wallet
     */
    private void scanEthereumIncomingTransactions(User user, BigInteger fromBlock, BigInteger toBlock) {
        try {
            String userAddress = user.getWalletAddress().toLowerCase();

            for (BigInteger blockNum = fromBlock; blockNum.compareTo(toBlock) <= 0; blockNum = blockNum.add(BigInteger.ONE)) {
                EthBlock ethBlock = web3j.ethGetBlockByNumber(
                    org.web3j.protocol.core.DefaultBlockParameter.valueOf(blockNum),
                    true
                ).send();

                if (ethBlock.getBlock() == null) continue;

                for (EthBlock.TransactionResult txResult : ethBlock.getBlock().getTransactions()) {
                    org.web3j.protocol.core.methods.response.Transaction tx =
                        (org.web3j.protocol.core.methods.response.Transaction) txResult.get();

                    if (tx.getTo() == null) continue;

                    // Check if this transaction is to our user's wallet
                    if (tx.getTo().toLowerCase().equals(userAddress)) {
                        String txHash = tx.getHash();

                        // Check if we already have this transaction recorded
                        Optional<Transaction> existing = transactionRepository.findByTxHash(txHash);
                        if (existing.isPresent()) {
                            continue; // Already recorded
                        }

                        // Calculate ETH amount
                        BigDecimal ethAmount = Convert.fromWei(
                            new BigDecimal(tx.getValue()),
                            Convert.Unit.ETHER
                        );

                        // Record this as an external funding transaction
                        log.info("Detected external incoming ETH transaction: {} -> {} ({} ETH)",
                            tx.getFrom(), userAddress, ethAmount);

                        transactionService.recordFundingTransaction(
                            user.getId(),
                            userAddress,
                            ethAmount,
                            "ETH",
                            "ETHEREUM",
                            txHash,
                            "EXTERNAL_FUNDING"
                        );
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error scanning Ethereum incoming transactions for user {}: {}",
                user.getWalletAddress(), e.getMessage());
        }
    }

    /**
     * Check if an Ethereum transaction has been confirmed
     */
    private void checkEthereumTransaction(Transaction tx) {
        try {
            log.debug("Checking Ethereum transaction: {}", tx.getTxHash());

            Optional<TransactionReceipt> receiptOpt = web3j
                    .ethGetTransactionReceipt(tx.getTxHash())
                    .send()
                    .getTransactionReceipt();

            if (receiptOpt.isEmpty()) {
                log.debug("Transaction {} not yet mined", tx.getTxHash());
                return;
            }

            TransactionReceipt receipt = receiptOpt.get();

            // Check if transaction succeeded or failed
            if (receipt.isStatusOK()) {
                // Check confirmations
                BigInteger confirmations = getConfirmations(receipt.getBlockNumber());

                if (confirmations.compareTo(BigInteger.valueOf(REQUIRED_CONFIRMATIONS)) >= 0) {
                    log.info("Transaction {} confirmed with {} confirmations",
                            tx.getTxHash(), confirmations);
                    transactionService.updateTransactionStatus(tx.getId(), "CONFIRMED");
                } else {
                    log.debug("Transaction {} has {} confirmations, waiting for {}",
                            tx.getTxHash(), confirmations, REQUIRED_CONFIRMATIONS);
                }
            } else {
                log.warn("Transaction {} failed on-chain", tx.getTxHash());
                transactionService.updateTransactionStatus(tx.getId(), "FAILED");
            }

        } catch (Exception e) {
            log.error("Error checking Ethereum transaction {}: {}", tx.getTxHash(), e.getMessage());
        }
    }

    /**
     * Check if an XRP Ledger transaction has been confirmed
     */
    private void checkXrpTransaction(Transaction tx) {
        try {
            log.debug("Checking XRP transaction: {}", tx.getTxHash());

            TransactionResult<org.xrpl.xrpl4j.model.transactions.Transaction> result = xrplClient.transaction(
                    TransactionRequestParams.of(Hash256.of(tx.getTxHash())),
                    org.xrpl.xrpl4j.model.transactions.Transaction.class
            );

            // If we got a result, the transaction is validated (confirmed)
            if (result.validated()) {
                log.info("XRP transaction {} confirmed (validated)", tx.getTxHash());
                transactionService.updateTransactionStatus(tx.getId(), "CONFIRMED");
            } else {
                log.debug("XRP transaction {} not yet validated", tx.getTxHash());
            }

        } catch (Exception e) {
            // Transaction not found means it's still pending or dropped
            if (e.getMessage() != null && e.getMessage().contains("txnNotFound")) {
                log.debug("XRP transaction {} not yet in ledger", tx.getTxHash());
            } else {
                log.error("Error checking XRP transaction {}: {}", tx.getTxHash(), e.getMessage());
            }
        }
    }

    /**
     * Check if a Solana transaction has been confirmed
     * Note: Simplified implementation - uses getTransaction to verify existence
     */
    private void checkSolanaTransaction(Transaction tx) {
        try {
            log.debug("Checking Solana transaction: {}", tx.getTxHash());

            // Try to get the transaction details - if it exists and returns data, it's confirmed
            var transaction = solanaClient.getApi().getTransaction(tx.getTxHash());

            if (transaction != null) {
                // Transaction exists on-chain
                // Check if there's an error in the transaction metadata
                if (transaction.getMeta() != null && transaction.getMeta().getErr() == null) {
                    log.info("Solana transaction {} confirmed successfully", tx.getTxHash());
                    transactionService.updateTransactionStatus(tx.getId(), "CONFIRMED");
                } else if (transaction.getMeta() != null && transaction.getMeta().getErr() != null) {
                    log.warn("Solana transaction {} failed: {}", tx.getTxHash(), transaction.getMeta().getErr());
                    transactionService.updateTransactionStatus(tx.getId(), "FAILED");
                } else {
                    log.info("Solana transaction {} confirmed", tx.getTxHash());
                    transactionService.updateTransactionStatus(tx.getId(), "CONFIRMED");
                }
            } else {
                log.debug("Solana transaction {} not yet confirmed", tx.getTxHash());
            }

        } catch (org.p2p.solanaj.rpc.RpcException e) {
            // Transaction not found yet - still pending
            log.debug("Solana transaction {} not yet on-chain: {}", tx.getTxHash(), e.getMessage());
        } catch (Exception e) {
            log.error("Error checking Solana transaction {}: {}", tx.getTxHash(), e.getMessage());
        }
    }

    /**
     * Get number of confirmations for an Ethereum transaction
     */
    private BigInteger getConfirmations(BigInteger txBlockNumber) {
        try {
            EthBlock.Block latestBlock = web3j.ethGetBlockByNumber(
                    org.web3j.protocol.core.DefaultBlockParameterName.LATEST,
                    false
            ).send().getBlock();

            if (latestBlock == null) {
                return BigInteger.ZERO;
            }

            BigInteger latestBlockNumber = latestBlock.getNumber();
            return latestBlockNumber.subtract(txBlockNumber).add(BigInteger.ONE);

        } catch (Exception e) {
            log.warn("Failed to get confirmations: {}", e.getMessage());
            return BigInteger.ZERO;
        }
    }

    /**
     * Check if transaction is older than maximum monitoring age
     */
    private boolean isTransactionTooOld(Transaction tx) {
        java.time.Duration age = java.time.Duration.between(tx.getTimestamp(), java.time.LocalDateTime.now());
        return age.toHours() > MAX_MONITORING_AGE_HOURS;
    }

    /**
     * Manually trigger monitoring (useful for testing)
     */
    public void triggerMonitoring() {
        log.info("Manually triggered transaction monitoring");
        monitorPendingTransactions();
    }
}

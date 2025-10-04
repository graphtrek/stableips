package co.grtk.stableips.service;

import co.grtk.stableips.model.Transaction;
import co.grtk.stableips.repository.TransactionRepository;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.model.client.transactions.TransactionRequestParams;
import org.xrpl.xrpl4j.model.client.transactions.TransactionResult;
import org.xrpl.xrpl4j.model.transactions.Hash256;

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
    private final Web3j web3j;
    private final XrplClient xrplClient;
    private final RpcClient solanaClient;
    private final TransactionService transactionService;

    public TransactionMonitoringService(
            TransactionRepository transactionRepository,
            Web3j web3j,
            XrplClient xrplClient,
            TransactionService transactionService) {
        this.transactionRepository = transactionRepository;
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
            return;
        }

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

        log.debug("Transaction monitoring cycle completed");
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

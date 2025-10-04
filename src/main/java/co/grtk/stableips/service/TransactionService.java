package co.grtk.stableips.service;

import co.grtk.stableips.model.Transaction;
import co.grtk.stableips.model.User;
import co.grtk.stableips.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class TransactionService {

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
            txHash = xrpWalletService.sendXrp(user.getXrpAddress(), recipient, amount);
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
}

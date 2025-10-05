# Blockchain Transaction Monitoring Design Document

## Executive Summary

This document provides a comprehensive design for blockchain transaction monitoring and recording across all funding operations in the StableIPS application. The design ensures **100% transaction visibility** for ETH funding, USDC/DAI minting, XRP faucet funding, and Solana devnet funding.

**Design Goals:**
- Capture ALL transaction hashes from blockchain operations
- Support multi-network transaction tracking (Ethereum, XRP, Solana)
- Handle synchronous and asynchronous operations reliably
- Provide robust error handling with partial failure recovery
- Enable transaction type differentiation (TRANSFER vs FUNDING vs MINTING)

---

## 1. Transaction Hash Capture Patterns

### 1.1 Ethereum Operations (Web3j)

#### Current Implementation Analysis

**ETH Funding (`WalletService.fundWallet()`)**
- **Status**: ✅ Transaction hash available
- **Pattern**: Synchronous - `Transfer.sendFunds()` returns `TransactionReceipt` immediately
- **Hash Source**: `receipt.getTransactionHash()`
- **Reliability**: High - transaction is mined before returning

```java
// Current pattern (lines 135-141 in WalletService.java)
TransactionReceipt receipt = Transfer.sendFunds(
    web3j,
    fundingCredentials,
    toAddress,
    amountInEth,
    Convert.Unit.ETHER
).send();

String txHash = receipt.getTransactionHash(); // ✅ Available
```

**USDC/DAI Minting (`ContractService.mintTestTokens()`)**
- **Status**: ✅ Transaction hash available
- **Pattern**: Asynchronous submission - `transactionManager.sendTransaction()` returns response
- **Hash Source**: `transactionResponse.getTransactionHash()`
- **Reliability**: High - hash returned immediately after submission

```java
// Current pattern (lines 441-447 in ContractService.java)
EthSendTransaction transactionResponse = transactionManager.sendTransaction(
    DefaultGasProvider.GAS_PRICE,
    DefaultGasProvider.GAS_LIMIT,
    contractAddress,
    encodedFunction,
    BigInteger.ZERO
);

String txHash = transactionResponse.getTransactionHash(); // ✅ Available
```

**Recommendation**: No changes needed for Ethereum operations. Hash capture is already reliable.

---

### 1.2 XRP Ledger Operations

#### Current Implementation Analysis

**XRP Faucet Funding (`XrpWalletService.fundWalletFromFaucet()`)**
- **Status**: ❌ Transaction hash NOT returned
- **Pattern**: Asynchronous - faucet client returns void
- **Hash Source**: Not captured
- **Issue**: Method returns wallet address instead of transaction hash

```java
// Current pattern (lines 72-82 in XrpWalletService.java)
public String fundWalletFromFaucet(String address) {
    try {
        FundAccountRequest fundRequest = FundAccountRequest.of(Address.of(address));
        faucetClient.fundAccount(fundRequest);
        log.info("Funded XRP wallet from faucet: {}", address);
        return address; // ❌ Returns address, not txHash
    } catch (Exception e) {
        log.error("Failed to fund XRP wallet from faucet: {}", e.getMessage());
        return null;
    }
}
```

**XRP Transfer (`XrpWalletService.sendXrp()`)**
- **Status**: ✅ Transaction hash available
- **Pattern**: Synchronous - submit returns `SubmitResult` with hash
- **Hash Source**: `submitResult.transactionResult().hash().value()`
- **Reliability**: High

```java
// Current pattern (lines 148-152 in XrpWalletService.java)
SubmitResult<Payment> submitResult = xrplClient.submit(signedTransaction);

if (submitResult.engineResult().equals("tesSUCCESS")) {
    String txHash = submitResult.transactionResult().hash().value(); // ✅ Available
    return txHash;
}
```

**Critical Issue**: The `fundWalletFromFaucet()` method uses the XRPL4J `FaucetClient` which does NOT return transaction details.

**Design Decision**:
1. **Primary Strategy**: Modify `fundWalletFromFaucet()` to poll for recent transactions after faucet call
2. **Fallback Strategy**: Generate synthetic transaction ID for tracking purposes
3. **Long-term**: Replace faucet funding with controlled wallet transfers (like ETH funding)

---

### 1.3 Solana Operations

#### Current Implementation Analysis

**Solana Faucet Funding (`SolanaWalletService.fundWalletFromFaucet()`)**
- **Status**: ✅ Transaction hash available
- **Pattern**: Asynchronous airdrop - returns signature
- **Hash Source**: `solanaClient.getApi().requestAirdrop()` returns signature string
- **Reliability**: Medium - devnet faucet can be rate-limited

```java
// Current pattern (lines 109-117 in SolanaWalletService.java)
String signature = solanaClient.getApi().requestAirdrop(publicKey, amountLamports);

if (signature != null && !signature.isEmpty()) {
    log.info("SOL airdrop successful. Signature: {}", signature);
    Thread.sleep(2000);
    return signature; // ✅ Transaction signature available
}
```

**Solana Transfer (`SolanaWalletService.sendSol()`)**
- **Status**: ✅ Transaction hash available
- **Pattern**: Synchronous - returns signature after sending
- **Hash Source**: `solanaClient.getApi().sendTransaction()`
- **Reliability**: High

```java
// Current pattern (lines 186-189 in SolanaWalletService.java)
String signature = solanaClient.getApi().sendTransaction(transaction, fromAccount);
log.info("SOL transfer successful. Signature: {}", signature);
return signature; // ✅ Available
```

**Recommendation**: Solana operations already return transaction signatures. No changes needed.

---

## 2. Transaction Monitoring Architecture

### 2.1 Data Model Enhancement

**Add Transaction Type Field**

```java
// Add to Transaction.java (after line 34)
@Column(nullable = false)
private String type = "TRANSFER"; // Default for backward compatibility

// Add getter/setter
public String getType() { return type; }
public void setType(String type) { this.type = type; }
```

**Transaction Type Enum** (Recommended)

```java
// Create new file: co/grtk/stableips/model/TransactionType.java
package co.grtk.stableips.model;

public enum TransactionType {
    TRANSFER,        // User-initiated transfers
    FUNDING,         // ETH funding from system wallet
    MINTING,         // Test token minting (USDC/DAI)
    FAUCET_FUNDING,  // XRP/SOL faucet funding
    AIRDROP          // Alternative name for faucet funding
}
```

**Migration Script**

```sql
-- V2__add_transaction_type.sql
ALTER TABLE transactions
ADD COLUMN type VARCHAR(50) NOT NULL DEFAULT 'TRANSFER';

-- Create index for type-based queries
CREATE INDEX idx_transactions_user_type_timestamp
ON transactions(user_id, type, timestamp DESC);

-- Add constraint
ALTER TABLE transactions
ADD CONSTRAINT chk_transaction_type
CHECK (type IN ('TRANSFER', 'FUNDING', 'MINTING', 'FAUCET_FUNDING', 'AIRDROP'));
```

---

### 2.2 Service Layer Methods

#### TransactionService Enhancements

**Method 1: `recordFundingTransaction()`**

```java
/**
 * Record a system-initiated funding transaction
 *
 * @param userId User ID receiving the funds
 * @param recipientAddress Wallet address receiving funds
 * @param amount Amount in token units
 * @param token Token symbol (ETH, USDC, DAI, XRP, SOL)
 * @param network Blockchain network (ETHEREUM, XRP, SOLANA)
 * @param txHash Transaction hash (null if funding failed)
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

    // For XRP faucet without txHash, use synthetic ID
    if (txHash == null && "FAUCET_FUNDING".equals(fundingType)) {
        txHash = generateSyntheticTxHash(network, recipientAddress);
        log.warn("Faucet funding without txHash. Generated synthetic ID: {}", txHash);
    }

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
 * Generate synthetic transaction hash for operations without real txHash
 */
private String generateSyntheticTxHash(String network, String address) {
    return String.format("%s_FAUCET_%s_%d",
        network,
        address.substring(0, Math.min(8, address.length())),
        System.currentTimeMillis()
    );
}
```

**Method 2: `getFundingTransactions()`**

```java
/**
 * Get all funding transactions for a user
 *
 * @param userId User ID
 * @return List of funding transactions ordered by timestamp descending
 */
public List<Transaction> getFundingTransactions(Long userId) {
    // Option 1: Using type field (recommended after migration)
    return transactionRepository.findByUserIdAndTypeInOrderByTimestampDesc(
        userId,
        List.of("FUNDING", "MINTING", "FAUCET_FUNDING")
    );

    // Option 2: Using status field (temporary workaround)
    // return transactionRepository.findByUserIdAndStatusOrderByTimestampDesc(userId, "FUNDING");
}
```

**Method 3: `getTransactionsByType()`**

```java
/**
 * Get transactions filtered by type
 *
 * @param userId User ID
 * @param type Transaction type (TRANSFER, FUNDING, MINTING, etc.)
 * @return List of transactions of specified type
 */
public List<Transaction> getTransactionsByType(Long userId, String type) {
    return transactionRepository.findByUserIdAndTypeOrderByTimestampDesc(userId, type);
}
```

**Method 4: `getAllTransactionsGrouped()`**

```java
/**
 * Get all transactions grouped by type
 *
 * @param user The user
 * @return Map with keys: "sent", "received", "funding"
 */
public Map<String, List<Transaction>> getAllTransactionsGrouped(User user) {
    List<Transaction> sent = getUserTransactions(user.getId());
    List<Transaction> received = getReceivedTransactions(user);
    List<Transaction> funding = getFundingTransactions(user.getId());

    return Map.of(
        "sent", sent,
        "received", received,
        "funding", funding
    );
}
```

---

### 2.3 Repository Enhancements

**Add Query Methods to TransactionRepository**

```java
// Add to TransactionRepository.java

/**
 * Find transactions by user and status
 */
List<Transaction> findByUserIdAndStatusOrderByTimestampDesc(Long userId, String status);

/**
 * Find transactions by user and type
 */
List<Transaction> findByUserIdAndTypeOrderByTimestampDesc(Long userId, String type);

/**
 * Find transactions by user and multiple types
 */
List<Transaction> findByUserIdAndTypeInOrderByTimestampDesc(Long userId, List<String> types);

/**
 * Find funding transactions across all users (admin query)
 */
@Query("SELECT t FROM Transaction t WHERE t.type IN :types ORDER BY t.timestamp DESC")
List<Transaction> findAllFundingTransactions(@Param("types") List<String> types);

/**
 * Find failed funding transactions for retry
 */
List<Transaction> findByStatusAndTypeInOrderByTimestampDesc(
    String status,
    List<String> types
);
```

---

## 3. Integration Patterns

### 3.1 WalletService Integration

#### Update `fundWallet()` - Record ETH Funding

```java
public String fundWallet(String toAddress, BigDecimal amountInEth) {
    if (fundingPrivateKey == null || fundingPrivateKey.isEmpty() || fundingPrivateKey.equals("YOUR_PRIVATE_KEY_HERE")) {
        log.info("Funding wallet not configured. Skipping wallet funding for: {}", toAddress);
        return null;
    }

    try {
        BigInteger privateKey = new BigInteger(fundingPrivateKey, 16);
        Credentials fundingCredentials = Credentials.create(ECKeyPair.create(privateKey));

        TransactionReceipt receipt = Transfer.sendFunds(
            web3j,
            fundingCredentials,
            toAddress,
            amountInEth,
            Convert.Unit.ETHER
        ).send();

        String txHash = receipt.getTransactionHash();
        log.info("Funded wallet {} with {} ETH. TX: {}", toAddress, amountInEth, txHash);

        // ✅ NEW: Record funding transaction
        User user = userRepository.findByWalletAddress(toAddress)
            .orElseThrow(() -> new RuntimeException("User not found for wallet: " + toAddress));

        transactionService.recordFundingTransaction(
            user.getId(),
            toAddress,
            amountInEth,
            "ETH",
            "ETHEREUM",
            txHash,
            "FUNDING"
        );

        return txHash;
    } catch (Exception e) {
        log.error("Failed to fund wallet {}: {}", toAddress, e.getMessage());

        // ✅ NEW: Record failed funding
        try {
            User user = userRepository.findByWalletAddress(toAddress).orElse(null);
            if (user != null) {
                transactionService.recordFundingTransaction(
                    user.getId(),
                    toAddress,
                    amountInEth,
                    "ETH",
                    "ETHEREUM",
                    null,  // null txHash indicates failure
                    "FUNDING"
                );
            }
        } catch (Exception recordError) {
            log.error("Failed to record funding failure: {}", recordError.getMessage());
        }

        return null;
    }
}
```

#### Update `fundTestTokens()` - Record USDC/DAI Minting

```java
public java.util.Map<String, String> fundTestTokens(String walletAddress) {
    if (fundingPrivateKey == null || fundingPrivateKey.isEmpty()) {
        throw new IllegalStateException("Funding wallet not configured. Please set FUNDED_SEPOLIA_WALLET_PRIVATE_KEY environment variable.");
    }

    try {
        BigInteger privateKey = new BigInteger(fundingPrivateKey, 16);
        Credentials ownerCredentials = Credentials.create(ECKeyPair.create(privateKey));

        User user = userRepository.findByWalletAddress(walletAddress)
            .orElseThrow(() -> new RuntimeException("User not found for wallet: " + walletAddress));

        // Mint test USDC
        String usdcTxHash = contractService.mintTestTokens(
            ownerCredentials,
            walletAddress,
            initialUsdcAmount,
            "TEST-USDC"
        );

        // ✅ NEW: Record USDC minting
        transactionService.recordFundingTransaction(
            user.getId(),
            walletAddress,
            initialUsdcAmount,
            "TEST-USDC",
            "ETHEREUM",
            usdcTxHash,
            "MINTING"
        );

        // Mint test DAI
        String daiTxHash = contractService.mintTestTokens(
            ownerCredentials,
            walletAddress,
            initialDaiAmount,
            "TEST-DAI"
        );

        // ✅ NEW: Record DAI minting
        transactionService.recordFundingTransaction(
            user.getId(),
            walletAddress,
            initialDaiAmount,
            "TEST-DAI",
            "ETHEREUM",
            daiTxHash,
            "MINTING"
        );

        return java.util.Map.of(
            "usdc", usdcTxHash,
            "dai", daiTxHash
        );
    } catch (Exception e) {
        // Record failed minting
        try {
            User user = userRepository.findByWalletAddress(walletAddress).orElse(null);
            if (user != null) {
                transactionService.recordFundingTransaction(
                    user.getId(), walletAddress, BigDecimal.ZERO,
                    "TEST-USDC", "ETHEREUM", null, "MINTING"
                );
            }
        } catch (Exception ignored) {}

        throw new RuntimeException("Failed to fund test tokens: " + e.getMessage(), e);
    }
}
```

#### Update `createUserWithWalletAndFunding()` - Record All Funding

```java
public User createUserWithWalletAndFunding(String username) {
    User user = createUserWithWallet(username);

    // Fund Ethereum wallet with ETH
    String ethTxHash = fundWallet(user.getWalletAddress(), initialAmount);
    // Note: fundWallet() now records the transaction internally

    // Fund XRP wallet from faucet
    String xrpTxHash = xrpWalletService.fundUserWallet(user.getXrpAddress());

    // ✅ NEW: Record XRP funding (if txHash available)
    if (xrpTxHash != null && !xrpTxHash.isEmpty()) {
        transactionService.recordFundingTransaction(
            user.getId(),
            user.getXrpAddress(),
            new BigDecimal("1000"), // XRP faucet amount
            "XRP",
            "XRP",
            xrpTxHash,
            "FAUCET_FUNDING"
        );
    }

    // Note: Solana funding removed - users can manually fund via https://faucet.solana.com/

    return user;
}
```

---

### 3.2 XrpWalletService Integration

**Critical Fix: Return Transaction Hash from Faucet Funding**

The XRPL4J `FaucetClient.fundAccount()` returns `FaucetAccountResponse` which may contain transaction details, but the current implementation doesn't capture them.

**Strategy 1: Poll for Recent Transactions (Recommended)**

```java
/**
 * Fund a wallet using the testnet faucet
 * Returns the funding transaction hash by polling account transactions
 */
public String fundWalletFromFaucet(String address) {
    try {
        // Record starting balance
        BigDecimal initialBalance = getBalance(address);

        // Request faucet funding
        FundAccountRequest fundRequest = FundAccountRequest.of(Address.of(address));
        FaucetAccountResponse response = faucetClient.fundAccount(fundRequest);

        log.info("Requested faucet funding for XRP wallet: {}", address);

        // Wait for transaction to be processed (faucet is async)
        Thread.sleep(3000);

        // Poll for the funding transaction
        String txHash = findRecentFundingTransaction(address, initialBalance);

        if (txHash != null) {
            log.info("Captured XRP faucet funding txHash: {}", txHash);
            return txHash;
        } else {
            log.warn("Could not find XRP faucet funding transaction for: {}", address);
            // Return synthetic ID for tracking
            return "XRP_FAUCET_" + address.substring(0, 8) + "_" + System.currentTimeMillis();
        }
    } catch (Exception e) {
        log.error("Failed to fund XRP wallet from faucet: {}", e.getMessage());
        return null;
    }
}

/**
 * Find recent funding transaction by comparing balance change
 */
private String findRecentFundingTransaction(String address, BigDecimal previousBalance) {
    try {
        // Get account transactions
        AccountTransactionsRequestParams params = AccountTransactionsRequestParams.builder()
            .account(Address.of(address))
            .limit(UnsignedInteger.valueOf(10))
            .build();

        AccountTransactionsResult result = xrplClient.accountTransactions(params);

        // Find payment transaction that increased balance
        for (var txResult : result.transactions()) {
            if (txResult.transaction() instanceof Payment payment) {
                if (payment.destination().equals(Address.of(address))) {
                    // This is a received payment
                    return payment.hash().map(Hash::value).orElse(null);
                }
            }
        }

        return null;
    } catch (Exception e) {
        log.error("Failed to query XRP transactions: {}", e.getMessage());
        return null;
    }
}
```

**Strategy 2: Enhanced Faucet Response Parsing**

```java
public String fundWalletFromFaucet(String address) {
    try {
        FundAccountRequest fundRequest = FundAccountRequest.of(Address.of(address));
        FaucetAccountResponse response = faucetClient.fundAccount(fundRequest);

        // Try to extract txHash from response
        String txHash = response.hash()
            .map(Hash::value)
            .orElse(null);

        if (txHash != null) {
            log.info("XRP faucet funding successful. TxHash: {}", txHash);
            return txHash;
        } else {
            log.warn("XRP faucet response did not include txHash. Address: {}", address);
            // Fallback to transaction polling or synthetic ID
            return "XRP_FAUCET_" + address.substring(0, 8) + "_" + System.currentTimeMillis();
        }
    } catch (Exception e) {
        log.error("Failed to fund XRP wallet from faucet: {}", e.getMessage());
        return null;
    }
}
```

**Update `fundUserWallet()` to Return TxHash**

```java
/**
 * Fund a user wallet from the configured funding wallet OR faucet
 * @return Transaction hash or null if funding failed
 */
public String fundUserWallet(String toAddress) {
    if (fundingSecret == null || fundingSecret.isEmpty()) {
        log.info("XRP funding wallet not configured. Using faucet instead.");
        return fundWalletFromFaucet(toAddress);
    }

    return sendXrp(fundingSecret, toAddress, initialAmount);
}
```

---

### 3.3 SolanaWalletService Integration

**Update `fundUserWallet()` for Transaction Recording**

Solana already returns transaction signatures. The integration point is in `WalletService.createUserWithWalletAndFunding()`:

```java
// In WalletService.createUserWithWalletAndFunding()

// Fund Solana wallet (if enabled)
String solTxHash = solanaWalletService.fundUserWallet(user.getSolanaPublicKey());

if (solTxHash != null && !solTxHash.isEmpty()) {
    transactionService.recordFundingTransaction(
        user.getId(),
        user.getSolanaPublicKey(),
        new BigDecimal("2"), // Solana airdrop amount
        "SOL",
        "SOLANA",
        solTxHash,
        "FAUCET_FUNDING"
    );
}
```

---

## 4. Async Transaction Handling

### 4.1 Async Operation Patterns

**Challenge**: Some blockchain operations are asynchronous:
- XRP faucet funding (callback/polling required)
- Solana airdrop (signature returned but transaction may fail)
- Ethereum transactions (hash returned but mining can fail)

**Solution**: Two-phase recording pattern

#### Phase 1: Record PENDING Transaction

```java
/**
 * Record a pending funding transaction before operation completes
 */
public Transaction recordPendingFunding(
    Long userId,
    String recipientAddress,
    BigDecimal amount,
    String token,
    String network,
    String fundingType
) {
    Transaction transaction = new Transaction(
        userId,
        recipientAddress,
        amount,
        token,
        network,
        "PENDING_" + System.currentTimeMillis(), // Temporary txHash
        "PENDING"
    );

    transaction.setType(fundingType);

    return transactionRepository.save(transaction);
}
```

#### Phase 2: Update with Actual TxHash

```java
/**
 * Update pending transaction with actual txHash
 */
public Transaction confirmFundingTransaction(Long transactionId, String actualTxHash) {
    Transaction transaction = transactionRepository.findById(transactionId)
        .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));

    transaction.setTxHash(actualTxHash);
    transaction.setStatus("CONFIRMED");

    return transactionRepository.save(transaction);
}
```

#### Usage Example

```java
// For XRP faucet with async callback
public String fundWalletFromFaucetAsync(String address, Long userId) {
    // Phase 1: Record pending
    Transaction pendingTx = transactionService.recordPendingFunding(
        userId, address, new BigDecimal("1000"), "XRP", "XRP", "FAUCET_FUNDING"
    );

    try {
        // Initiate faucet funding
        FundAccountRequest fundRequest = FundAccountRequest.of(Address.of(address));
        faucetClient.fundAccount(fundRequest);

        // Poll for completion in background
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(5000); // Wait for faucet processing
                String actualTxHash = findRecentFundingTransaction(address, BigDecimal.ZERO);

                if (actualTxHash != null) {
                    transactionService.confirmFundingTransaction(pendingTx.getId(), actualTxHash);
                } else {
                    transactionService.updateTransactionStatus(pendingTx.getId(), "FAILED");
                }
            } catch (Exception e) {
                log.error("Failed to confirm faucet transaction: {}", e.getMessage());
                transactionService.updateTransactionStatus(pendingTx.getId(), "FAILED");
            }
        });

        return pendingTx.getTxHash(); // Return temporary hash
    } catch (Exception e) {
        transactionService.updateTransactionStatus(pendingTx.getId(), "FAILED");
        return null;
    }
}
```

---

### 4.2 Transaction Monitoring Service

**Create Background Monitoring for Pending Transactions**

```java
// Create new file: co/grtk/stableips/service/FundingMonitoringService.java
package co.grtk.stableips.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FundingMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(FundingMonitoringService.class);

    private final TransactionRepository transactionRepository;
    private final XrpWalletService xrpWalletService;

    public FundingMonitoringService(
        TransactionRepository transactionRepository,
        XrpWalletService xrpWalletService
    ) {
        this.transactionRepository = transactionRepository;
        this.xrpWalletService = xrpWalletService;
    }

    /**
     * Monitor pending funding transactions and update their status
     * Runs every 30 seconds
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void monitorPendingFundingTransactions() {
        List<Transaction> pending = transactionRepository.findByStatusAndTypeInOrderByTimestampDesc(
            "PENDING",
            List.of("FUNDING", "MINTING", "FAUCET_FUNDING")
        );

        log.info("Monitoring {} pending funding transactions", pending.size());

        for (Transaction tx : pending) {
            try {
                // Skip if too old (>5 minutes)
                if (tx.getTimestamp().isBefore(LocalDateTime.now().minusMinutes(5))) {
                    tx.setStatus("FAILED");
                    transactionRepository.save(tx);
                    continue;
                }

                // Check transaction status on blockchain
                boolean confirmed = checkTransactionStatus(tx);

                if (confirmed) {
                    tx.setStatus("CONFIRMED");
                    transactionRepository.save(tx);
                    log.info("Confirmed pending transaction: {}", tx.getTxHash());
                }
            } catch (Exception e) {
                log.error("Failed to monitor transaction {}: {}", tx.getId(), e.getMessage());
            }
        }
    }

    private boolean checkTransactionStatus(Transaction tx) {
        // Implement network-specific confirmation logic
        switch (tx.getNetwork()) {
            case "XRP":
                return checkXrpTransaction(tx);
            case "ETHEREUM":
                return checkEthereumTransaction(tx);
            case "SOLANA":
                return checkSolanaTransaction(tx);
            default:
                return false;
        }
    }

    private boolean checkXrpTransaction(Transaction tx) {
        // Query XRP ledger for transaction
        // Implementation depends on XRPL4J transaction query methods
        return false;
    }

    private boolean checkEthereumTransaction(Transaction tx) {
        // Use Web3j to check if transaction is mined
        return false;
    }

    private boolean checkSolanaTransaction(Transaction tx) {
        // Use Solana RPC to check transaction status
        return false;
    }
}
```

---

## 5. Error Scenarios and Recovery

### 5.1 Error Categories

#### Category 1: Network Errors (Recoverable)
- **Scenario**: Infura/RPC node timeout
- **Detection**: IOException, TimeoutException
- **Recovery**: Retry with exponential backoff
- **Recording**: Record PENDING, retry confirmation

```java
@Retryable(
    value = {IOException.class, TimeoutException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 2000, multiplier = 2)
)
public String fundWalletWithRetry(String address, BigDecimal amount) {
    return fundWallet(address, amount);
}
```

#### Category 2: Insufficient Funds (Non-Recoverable)
- **Scenario**: Funding wallet has insufficient balance
- **Detection**: Transaction error message contains "insufficient"
- **Recovery**: Alert admin, record as FAILED
- **Recording**: Record FAILED transaction immediately

```java
catch (Exception e) {
    if (e.getMessage().contains("insufficient")) {
        log.error("Insufficient funds in funding wallet. Cannot proceed.");
        transactionService.recordFundingTransaction(
            userId, address, amount, token, network, null, "FUNDING"
        );
        throw new InsufficientFundsException("Funding wallet depleted");
    }
}
```

#### Category 3: Invalid Address (Non-Recoverable)
- **Scenario**: Recipient address is invalid
- **Detection**: WalletUtils.isValidAddress() returns false
- **Recovery**: None - data validation error
- **Recording**: Do not record transaction

```java
if (!WalletUtils.isValidAddress(recipientAddress)) {
    throw new IllegalArgumentException("Invalid address: " + recipientAddress);
    // No transaction recording
}
```

#### Category 4: Partial Success (Complex)
- **Scenario**: USDC minting succeeds but DAI minting fails
- **Detection**: Exception during second minting
- **Recovery**: Record successful USDC, record failed DAI
- **Recording**: Two separate transaction records

```java
public Map<String, String> fundTestTokens(String walletAddress) {
    Map<String, String> results = new HashMap<>();
    User user = userRepository.findByWalletAddress(walletAddress).orElseThrow();

    // USDC minting
    try {
        String usdcTxHash = contractService.mintTestTokens(...);
        transactionService.recordFundingTransaction(
            user.getId(), walletAddress, initialUsdcAmount,
            "TEST-USDC", "ETHEREUM", usdcTxHash, "MINTING"
        );
        results.put("usdc", usdcTxHash);
    } catch (Exception e) {
        log.error("USDC minting failed: {}", e.getMessage());
        transactionService.recordFundingTransaction(
            user.getId(), walletAddress, initialUsdcAmount,
            "TEST-USDC", "ETHEREUM", null, "MINTING"
        );
        results.put("usdc", null);
    }

    // DAI minting (proceed even if USDC failed)
    try {
        String daiTxHash = contractService.mintTestTokens(...);
        transactionService.recordFundingTransaction(
            user.getId(), walletAddress, initialDaiAmount,
            "TEST-DAI", "ETHEREUM", daiTxHash, "MINTING"
        );
        results.put("dai", daiTxHash);
    } catch (Exception e) {
        log.error("DAI minting failed: {}", e.getMessage());
        transactionService.recordFundingTransaction(
            user.getId(), walletAddress, initialDaiAmount,
            "TEST-DAI", "ETHEREUM", null, "MINTING"
        );
        results.put("dai", null);
    }

    return results;
}
```

---

### 5.2 Compensation Patterns

**Scenario**: User wallet created but funding failed

**Strategy**: Mark user for manual funding review

```java
// Add to User entity
@Column
private Boolean needsFunding = false;

// After failed funding
public User createUserWithWalletAndFunding(String username) {
    User user = createUserWithWallet(username);

    try {
        String ethTxHash = fundWallet(user.getWalletAddress(), initialAmount);
        if (ethTxHash == null) {
            user.setNeedsFunding(true);
            userRepository.save(user);
        }
    } catch (Exception e) {
        user.setNeedsFunding(true);
        userRepository.save(user);
        log.error("User created but funding failed: {}", user.getId());
    }

    return user;
}

// Admin endpoint to retry funding
@PostMapping("/admin/retry-funding/{userId}")
public ResponseEntity<?> retryFunding(@PathVariable Long userId) {
    User user = userRepository.findById(userId).orElseThrow();

    if (!user.getNeedsFunding()) {
        return ResponseEntity.badRequest().body("User does not need funding");
    }

    String txHash = walletService.fundWallet(user.getWalletAddress(), initialAmount);

    if (txHash != null) {
        user.setNeedsFunding(false);
        userRepository.save(user);
        return ResponseEntity.ok("Funding successful: " + txHash);
    }

    return ResponseEntity.status(500).body("Funding failed");
}
```

---

## 6. Multi-Network Transaction Metadata

### 6.1 Network-Specific Metadata

**Add Metadata JSON Field to Transaction**

```java
// Add to Transaction.java
@Column(columnDefinition = "jsonb")
private String metadata;

// Getter/Setter
public String getMetadata() { return metadata; }
public void setMetadata(String metadata) { this.metadata = metadata; }
```

**Metadata Structure Examples**

**Ethereum Funding:**
```json
{
  "fundingWallet": "0x1234...abcd",
  "gasUsed": "21000",
  "gasPrice": "20000000000",
  "blockNumber": "12345678",
  "confirmations": 12
}
```

**USDC/DAI Minting:**
```json
{
  "contractAddress": "0xTestUSDC...",
  "mintFunction": "mint(address,uint256)",
  "ownerWallet": "0xOwner...",
  "initialSupply": "1000000000000000000000"
}
```

**XRP Faucet Funding:**
```json
{
  "faucetUrl": "https://faucet.altnet.rippletest.net",
  "fundingMethod": "FAUCET_AIRDROP",
  "ledgerIndex": "12345678",
  "synthetic": false
}
```

**Solana Airdrop:**
```json
{
  "cluster": "DEVNET",
  "faucetUrl": "https://api.devnet.solana.com",
  "lamports": "2000000000",
  "slot": "123456789"
}
```

**Helper Method for Metadata**

```java
// In TransactionService
private String buildFundingMetadata(String network, String fundingType, Map<String, Object> details) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("network", network);
    metadata.put("fundingType", fundingType);
    metadata.put("timestamp", LocalDateTime.now().toString());
    metadata.putAll(details);

    try {
        return new ObjectMapper().writeValueAsString(metadata);
    } catch (Exception e) {
        log.error("Failed to serialize metadata: {}", e.getMessage());
        return "{}";
    }
}

// Usage
String metadata = buildFundingMetadata("ETHEREUM", "FUNDING", Map.of(
    "fundingWallet", fundingCredentials.getAddress(),
    "gasUsed", receipt.getGasUsed().toString(),
    "blockNumber", receipt.getBlockNumber().toString()
));

transaction.setMetadata(metadata);
```

---

### 6.2 Cross-Network Transaction Correlation

**Scenario**: User receives funding on multiple networks simultaneously

**Solution**: Transaction batch ID

```java
// Add to Transaction.java
@Column
private String batchId;

// In WalletService.createUserWithWalletAndFunding()
String batchId = "FUNDING_BATCH_" + user.getId() + "_" + System.currentTimeMillis();

// Set batchId for all funding transactions
transactionService.recordFundingTransaction(
    user.getId(), address, amount, "ETH", "ETHEREUM", txHash, "FUNDING"
).setBatchId(batchId);

transactionService.recordFundingTransaction(
    user.getId(), xrpAddress, amount, "XRP", "XRP", xrpTxHash, "FAUCET_FUNDING"
).setBatchId(batchId);

// Query by batchId
List<Transaction> fundingBatch = transactionRepository.findByBatchIdOrderByTimestampDesc(batchId);
```

---

## 7. Blockchain API Changes Summary

### 7.1 Required Changes

#### XrpWalletService

**Change 1: `fundWalletFromFaucet()` return type**
```java
// BEFORE
public String fundWalletFromFaucet(String address) {
    // ...
    return address; // ❌ Returns address instead of txHash
}

// AFTER
public String fundWalletFromFaucet(String address) {
    // ...
    String txHash = extractTxHashFromFaucetResponse(response);
    return txHash; // ✅ Returns actual transaction hash
}
```

**Change 2: Add transaction polling method**
```java
// NEW METHOD
private String findRecentFundingTransaction(String address, BigDecimal previousBalance) {
    // Poll XRP ledger for recent payment transactions
    // Return transaction hash
}
```

#### WalletService

**Change 1: Add TransactionService dependency**
```java
// Add to constructor
private final TransactionService transactionService;

public WalletService(
    UserRepository userRepository,
    Web3j web3j,
    XrpWalletService xrpWalletService,
    SolanaWalletService solanaWalletService,
    ContractService contractService,
    TransactionService transactionService // ✅ NEW
) {
    // ...
    this.transactionService = transactionService;
}
```

**Change 2: Record transactions in funding methods**
- `fundWallet()` - Record ETH funding
- `fundTestTokens()` - Record USDC/DAI minting
- `createUserWithWalletAndFunding()` - Record XRP funding

#### TransactionService

**New Methods:**
1. `recordFundingTransaction()` - Record funding operations
2. `getFundingTransactions()` - Query funding transactions
3. `getTransactionsByType()` - Filter by transaction type
4. `recordPendingFunding()` - For async operations
5. `confirmFundingTransaction()` - Update pending transactions

#### TransactionRepository

**New Query Methods:**
1. `findByUserIdAndStatusOrderByTimestampDesc()`
2. `findByUserIdAndTypeOrderByTimestampDesc()`
3. `findByUserIdAndTypeInOrderByTimestampDesc()`
4. `findByStatusAndTypeInOrderByTimestampDesc()`
5. `findByBatchIdOrderByTimestampDesc()` (optional)

---

### 7.2 Configuration Changes

**application.properties additions:**

```properties
# Transaction monitoring
transaction.monitoring.enabled=true
transaction.monitoring.pending-timeout-minutes=5
transaction.monitoring.check-interval-seconds=30

# Funding configuration
wallet.funding.record-transactions=true
wallet.funding.batch-funding=true

# XRP faucet
xrp.faucet.poll-interval-ms=3000
xrp.faucet.max-poll-attempts=5
xrp.faucet.use-synthetic-hash=true

# Solana airdrop
solana.airdrop.confirmation-timeout-seconds=10
```

---

## 8. Testing Strategy

### 8.1 Unit Tests

**TransactionService Tests:**
```java
@Test
void shouldRecordEthFundingTransaction() {
    // Test successful ETH funding recording
    Transaction tx = transactionService.recordFundingTransaction(
        1L, "0xAddress", new BigDecimal("10"), "ETH", "ETHEREUM", "0xHash", "FUNDING"
    );

    assertThat(tx.getType()).isEqualTo("FUNDING");
    assertThat(tx.getStatus()).isEqualTo("CONFIRMED");
}

@Test
void shouldRecordFailedFundingWithNullTxHash() {
    // Test failed funding (null txHash)
    Transaction tx = transactionService.recordFundingTransaction(
        1L, "0xAddress", new BigDecimal("10"), "ETH", "ETHEREUM", null, "FUNDING"
    );

    assertThat(tx.getTxHash()).isNull();
    assertThat(tx.getStatus()).isEqualTo("FAILED");
}
```

**XrpWalletService Tests:**
```java
@Test
void shouldReturnTxHashFromFaucetFunding() {
    String address = "rTestAddress";
    String txHash = xrpWalletService.fundWalletFromFaucet(address);

    assertThat(txHash).isNotNull();
    assertThat(txHash).doesNotContain("rTestAddress"); // Should be txHash, not address
}
```

### 8.2 Integration Tests

**Funding Transaction Integration Test:**
```java
@SpringBootTest
@Testcontainers
class FundingTransactionIntegrationTest {

    @Test
    void shouldRecordAllFundingTransactionsForNewUser() {
        // Create user with funding
        User user = walletService.createUserWithWalletAndFunding("test_user");

        // Verify funding transactions recorded
        List<Transaction> funding = transactionService.getFundingTransactions(user.getId());

        // Should have ETH funding + XRP funding (at minimum)
        assertThat(funding).hasSizeGreaterThanOrEqualTo(2);

        // Verify ETH funding
        Transaction ethFunding = funding.stream()
            .filter(tx -> "ETH".equals(tx.getToken()))
            .findFirst()
            .orElseThrow();

        assertThat(ethFunding.getType()).isEqualTo("FUNDING");
        assertThat(ethFunding.getTxHash()).isNotNull();

        // Verify XRP funding
        Transaction xrpFunding = funding.stream()
            .filter(tx -> "XRP".equals(tx.getToken()))
            .findFirst()
            .orElseThrow();

        assertThat(xrpFunding.getType()).isEqualTo("FAUCET_FUNDING");
        assertThat(xrpFunding.getTxHash()).isNotNull();
    }
}
```

### 8.3 Manual Testing Checklist

- [ ] Create new user - verify ETH funding transaction recorded
- [ ] Mint test tokens - verify USDC and DAI minting transactions recorded
- [ ] XRP wallet funding - verify faucet funding transaction recorded with valid txHash
- [ ] Solana wallet funding - verify airdrop transaction recorded
- [ ] Failed funding - verify FAILED transaction recorded with null txHash
- [ ] Query funding transactions - verify filtering works correctly
- [ ] Transaction history UI - verify funding transactions appear separately

---

## 9. Implementation Roadmap

### Phase 1: Data Model & Repository (Day 1)
**Priority: Critical**

- [ ] Add `type` field to Transaction entity
- [ ] Create database migration script (V2__add_transaction_type.sql)
- [ ] Add new query methods to TransactionRepository
- [ ] Run migration on test database
- [ ] Test repository queries

**Deliverable**: Transaction model supports type differentiation

---

### Phase 2: TransactionService Methods (Day 1-2)
**Priority: Critical**

- [ ] Implement `recordFundingTransaction()`
- [ ] Implement `getFundingTransactions()`
- [ ] Implement `getTransactionsByType()`
- [ ] Add error handling for null txHash
- [ ] Write unit tests for new methods

**Deliverable**: Service layer can record and query funding transactions

---

### Phase 3: Ethereum Integration (Day 2)
**Priority: High**

- [ ] Update `WalletService.fundWallet()` to record transactions
- [ ] Update `WalletService.fundTestTokens()` to record minting
- [ ] Add try-catch blocks for partial failure handling
- [ ] Test ETH funding transaction recording
- [ ] Test USDC/DAI minting transaction recording

**Deliverable**: All Ethereum funding operations record transactions

---

### Phase 4: XRP Integration (Day 2-3)
**Priority: High**

- [ ] Update `XrpWalletService.fundWalletFromFaucet()` to return txHash
- [ ] Implement transaction polling OR synthetic hash generation
- [ ] Update `WalletService.createUserWithWalletAndFunding()` for XRP
- [ ] Test XRP faucet funding transaction recording
- [ ] Handle cases where faucet doesn't return txHash

**Deliverable**: XRP funding operations record transaction hashes

---

### Phase 5: Solana Integration (Day 3)
**Priority: Medium**

- [ ] Add Solana funding recording to `createUserWithWalletAndFunding()`
- [ ] Test Solana airdrop transaction recording
- [ ] Handle devnet rate limiting errors

**Deliverable**: Solana funding operations record transaction signatures

---

### Phase 6: Async Monitoring (Day 3-4)
**Priority: Medium**

- [ ] Create `FundingMonitoringService`
- [ ] Implement pending transaction monitoring
- [ ] Add scheduled task for status updates
- [ ] Test timeout handling (failed after 5 minutes)

**Deliverable**: Background service monitors pending funding transactions

---

### Phase 7: Error Handling & Recovery (Day 4)
**Priority: High**

- [ ] Implement partial failure recording (USDC success, DAI fail)
- [ ] Add compensation patterns for failed funding
- [ ] Add `needsFunding` flag to User entity
- [ ] Create admin retry endpoint
- [ ] Test error scenarios

**Deliverable**: Robust error handling for all funding operations

---

### Phase 8: Integration Testing (Day 4-5)
**Priority: Critical**

- [ ] Run all integration tests
- [ ] Test complete user creation flow
- [ ] Test partial failures
- [ ] Test async operations
- [ ] Verify all 36 test cases pass

**Deliverable**: All funding transaction tests pass

---

### Phase 9: UI Updates (Day 5)
**Priority: Low**

- [ ] Update `wallet/dashboard.jte` to display funding transactions
- [ ] Add transaction type filter (All / Transfers / Funding)
- [ ] Show funding transactions with special indicator
- [ ] Test UI changes

**Deliverable**: Users can view funding transactions in dashboard

---

### Phase 10: Documentation & Monitoring (Day 5)
**Priority: Medium**

- [ ] Update API documentation
- [ ] Add logging for funding operations
- [ ] Set up monitoring dashboards
- [ ] Create admin guide for funding troubleshooting

**Deliverable**: Complete documentation and monitoring

---

## 10. Risk Mitigation

### Risk 1: XRP Faucet Doesn't Return TxHash
**Likelihood**: High
**Impact**: Medium

**Mitigation**:
1. Implement transaction polling as fallback
2. Generate synthetic transaction IDs for tracking
3. Long-term: Replace faucet with controlled funding wallet (like ETH)

**Contingency**:
```java
if (txHash == null || txHash.isEmpty()) {
    txHash = "XRP_FAUCET_" + address.substring(0, 8) + "_" + System.currentTimeMillis();
    transaction.setMetadata("{\"synthetic\": true}");
}
```

---

### Risk 2: Network Failures During Funding
**Likelihood**: Medium
**Impact**: High (user has wallet but no funds)

**Mitigation**:
1. Implement retry logic with exponential backoff
2. Record failed funding with `needsFunding` flag
3. Provide admin retry endpoint
4. Monitor funding failure rate

**Contingency**:
```java
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
public String fundWalletWithRetry(String address, BigDecimal amount) {
    return fundWallet(address, amount);
}
```

---

### Risk 3: Partial Funding Success
**Likelihood**: Low
**Impact**: High (user has ETH but no tokens)

**Mitigation**:
1. Record each funding operation separately
2. Continue funding even if one fails
3. Flag user for manual review
4. Provide admin dashboard for partial funding

**Contingency**: Already implemented in Section 5.1 (Partial Success pattern)

---

### Risk 4: Database Transaction Conflicts
**Likelihood**: Low
**Impact**: Medium

**Mitigation**:
1. Use `@Transactional` on funding methods
2. Separate transaction recording into independent transactions
3. Use optimistic locking for concurrent updates

**Contingency**:
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public Transaction recordFundingTransaction(...) {
    // Independent transaction - won't rollback parent
}
```

---

## 11. Success Metrics

### Operational Metrics
- **Funding Success Rate**: >95% of funding operations should succeed
- **Transaction Recording Rate**: 100% of funding operations should be recorded
- **Average Funding Time**: <10 seconds for complete user setup
- **Failed Funding Recovery**: <1 hour to retry failed funding

### Technical Metrics
- **Test Coverage**: 85%+ for transaction recording logic
- **Query Performance**: <100ms for transaction queries
- **Async Processing**: <30 seconds to confirm pending transactions
- **Error Rate**: <5% for network-related failures

### Monitoring Alerts
- Alert if funding success rate drops below 90%
- Alert if pending transactions exceed 10 minutes
- Alert if funding wallet balance drops below threshold
- Alert if XRP/Solana faucet failures exceed 20%

---

## 12. Conclusion

This design provides a comprehensive solution for blockchain transaction monitoring across all networks (Ethereum, XRP, Solana). The key innovations are:

1. **Universal Transaction Recording**: All funding operations record transactions regardless of source (wallet, faucet, minting)

2. **Multi-Network Support**: Handles different blockchain patterns (Web3j receipts, XRPL submissions, Solana signatures)

3. **Async Operation Handling**: Two-phase recording pattern for operations that complete asynchronously

4. **Robust Error Recovery**: Partial failure handling, retry logic, and admin compensation tools

5. **Transaction Type Differentiation**: Clear separation between user transfers and system funding

The implementation roadmap prioritizes critical infrastructure (data model, service methods) before integration and UI updates. With proper testing and monitoring, this design will provide **100% visibility** into all blockchain funding operations.

---

**Next Steps for Implementation Team**:
1. Review this design document
2. Create Jira tickets for each phase
3. Begin with Phase 1 (Data Model & Repository)
4. Run TDD cycle: Tests written → Implementation → Refactor
5. Deploy to staging for integration testing
6. Monitor metrics and adjust as needed

**Estimated Total Implementation Effort**: 4-5 developer days
**Estimated Testing/QA Effort**: 1-2 days
**Total Project Duration**: 5-7 days

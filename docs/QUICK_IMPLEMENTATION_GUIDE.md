# Quick Implementation Guide - Funding Transaction Tracking

## Overview

This guide provides the EXACT code changes needed to implement funding transaction tracking. Follow these steps in order.

**Estimated Time**: 4-5 hours for core implementation

---

## Step 1: Database Migration (15 minutes)

### Create Migration File

**File**: `src/main/resources/db/migration/V2__add_transaction_type.sql`

```sql
-- Add type column to transactions table
ALTER TABLE transactions
ADD COLUMN type VARCHAR(50) NOT NULL DEFAULT 'TRANSFER';

-- Create index for type-based queries
CREATE INDEX idx_transactions_user_type_timestamp
ON transactions(user_id, type, timestamp DESC);

-- Create index for status-based queries
CREATE INDEX idx_transactions_user_status_timestamp
ON transactions(user_id, status, timestamp DESC);

-- Add check constraint
ALTER TABLE transactions
ADD CONSTRAINT chk_transaction_type
CHECK (type IN ('TRANSFER', 'FUNDING', 'MINTING', 'FAUCET_FUNDING'));
```

### Update Transaction Entity

**File**: `src/main/java/co/grtk/stableips/model/Transaction.java`

```java
// Add after line 34 (after status field)
@Column(nullable = false)
private String type = "TRANSFER";

// Add getter/setter at end of class
public String getType() {
    return type;
}

public void setType(String type) {
    this.type = type;
}
```

---

## Step 2: Repository Query Methods (10 minutes)

**File**: `src/main/java/co/grtk/stableips/repository/TransactionRepository.java`

Add these methods after line 35:

```java
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
 * Find transactions by status and types (for monitoring)
 */
List<Transaction> findByStatusAndTypeInOrderByTimestampDesc(String status, List<String> types);
```

---

## Step 3: TransactionService Methods (30 minutes)

**File**: `src/main/java/co/grtk/stableips/service/TransactionService.java`

Add these methods after line 135:

```java
/**
 * Record a system-initiated funding transaction
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
 */
public List<Transaction> getFundingTransactions(Long userId) {
    return transactionRepository.findByUserIdAndTypeInOrderByTimestampDesc(
        userId,
        List.of("FUNDING", "MINTING", "FAUCET_FUNDING")
    );
}

/**
 * Get transactions filtered by type
 */
public List<Transaction> getTransactionsByType(Long userId, String type) {
    return transactionRepository.findByUserIdAndTypeOrderByTimestampDesc(userId, type);
}
```

---

## Step 4: Fix XRP Faucet (45 minutes)

**File**: `src/main/java/co/grtk/stableips/service/XrpWalletService.java`

Replace the `fundWalletFromFaucet()` method (lines 72-82):

```java
/**
 * Fund a wallet using the testnet faucet
 * Returns transaction hash by polling account transactions
 */
public String fundWalletFromFaucet(String address) {
    try {
        // Get initial balance
        BigDecimal initialBalance = getBalance(address);

        // Request faucet funding
        FundAccountRequest fundRequest = FundAccountRequest.of(Address.of(address));
        faucetClient.fundAccount(fundRequest);

        log.info("Requested faucet funding for XRP wallet: {}", address);

        // Wait for faucet processing (async operation)
        Thread.sleep(3000);

        // Poll for the funding transaction
        String txHash = findRecentFundingTransaction(address, initialBalance);

        if (txHash != null) {
            log.info("Captured XRP faucet funding txHash: {}", txHash);
            return txHash;
        } else {
            log.warn("Could not find XRP faucet funding transaction for: {}", address);
            // Fallback to synthetic ID for tracking
            return "XRP_FAUCET_" + address.substring(0, Math.min(8, address.length()))
                + "_" + System.currentTimeMillis();
        }
    } catch (Exception e) {
        log.error("Failed to fund XRP wallet from faucet: {}", e.getMessage());
        return null;
    }
}

/**
 * Find recent funding transaction by querying account transactions
 */
private String findRecentFundingTransaction(String address, BigDecimal previousBalance) {
    try {
        // Get recent account transactions
        AccountTransactionsRequestParams params = AccountTransactionsRequestParams.builder()
            .account(Address.of(address))
            .limit(UnsignedInteger.valueOf(10))
            .build();

        AccountTransactionsResult result = xrplClient.accountTransactions(params);

        // Find payment transaction that increased balance
        for (var txResult : result.transactions()) {
            if (txResult.transaction() instanceof Payment payment) {
                if (payment.destination().equals(Address.of(address))) {
                    // This is a received payment (faucet funding)
                    return payment.hash()
                        .map(Hash::value)
                        .orElse(null);
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

Add required imports at top of file:

```java
import org.xrpl.xrpl4j.model.client.accounts.AccountTransactionsRequestParams;
import org.xrpl.xrpl4j.model.client.accounts.AccountTransactionsResult;
import org.xrpl.xrpl4j.model.transactions.Hash;
```

---

## Step 5: Update WalletService - ETH Funding (20 minutes)

**File**: `src/main/java/co/grtk/stableips/service/WalletService.java`

### Add TransactionService dependency

Update constructor (around line 50):

```java
private final TransactionService transactionService;

public WalletService(
    UserRepository userRepository,
    Web3j web3j,
    XrpWalletService xrpWalletService,
    SolanaWalletService solanaWalletService,
    ContractService contractService,
    TransactionService transactionService
) {
    this.userRepository = userRepository;
    this.web3j = web3j;
    this.xrpWalletService = xrpWalletService;
    this.solanaWalletService = solanaWalletService;
    this.contractService = contractService;
    this.transactionService = transactionService;
}
```

### Update fundWallet() method

Replace the method (lines 125-149):

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

        // Record funding transaction
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

        // Record failed funding
        try {
            User user = userRepository.findByWalletAddress(toAddress).orElse(null);
            if (user != null) {
                transactionService.recordFundingTransaction(
                    user.getId(),
                    toAddress,
                    amountInEth,
                    "ETH",
                    "ETHEREUM",
                    null,
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

---

## Step 6: Update WalletService - Token Minting (20 minutes)

**File**: `src/main/java/co/grtk/stableips/service/WalletService.java`

Replace the `fundTestTokens()` method (lines 179-212):

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

        // Record USDC minting
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

        // Record DAI minting
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
        throw new RuntimeException("Failed to fund test tokens: " + e.getMessage(), e);
    }
}
```

---

## Step 7: Update WalletService - XRP Funding (15 minutes)

**File**: `src/main/java/co/grtk/stableips/service/WalletService.java`

Replace the `createUserWithWalletAndFunding()` method (lines 151-163):

```java
public User createUserWithWalletAndFunding(String username) {
    User user = createUserWithWallet(username);

    // Fund Ethereum wallet with ETH
    fundWallet(user.getWalletAddress(), initialAmount);
    // Note: fundWallet() now records the transaction

    // Fund XRP wallet from faucet
    String xrpTxHash = xrpWalletService.fundUserWallet(user.getXrpAddress());

    // Record XRP funding
    if (xrpTxHash != null && !xrpTxHash.isEmpty()) {
        transactionService.recordFundingTransaction(
            user.getId(),
            user.getXrpAddress(),
            new BigDecimal("1000"),
            "XRP",
            "XRP",
            xrpTxHash,
            "FAUCET_FUNDING"
        );
    }

    return user;
}
```

---

## Step 8: Run Tests (30 minutes)

### Run Database Migration

```bash
./gradlew flywayMigrate
```

### Run Repository Tests

```bash
./gradlew test --tests "TransactionRepositoryTest"
```

**Expected**: New query methods should work

### Run Service Tests

```bash
./gradlew test --tests "TransactionServiceTest"
```

**Expected**: All funding transaction tests should pass

### Run Integration Tests

```bash
./gradlew test --tests "FundingTransactionIntegrationTest"
```

**Expected**: Complete user creation flow with funding tracking should work

### Run All Tests

```bash
./gradlew test
```

**Expected**: All 36 new tests + existing tests should pass

---

## Step 9: Manual Testing (20 minutes)

### Test 1: Create User with Funding

```bash
# Start application
./gradlew bootRun

# Create user (via UI or API)
# Verify:
# 1. User created successfully
# 2. ETH funding transaction recorded in database
# 3. XRP funding transaction recorded (if faucet works)
```

### Test 2: Mint Test Tokens

```bash
# Call fundTestTokens endpoint
# Verify:
# 1. USDC minting transaction recorded
# 2. DAI minting transaction recorded
# 3. Both transactions have valid txHash
```

### Test 3: Query Funding Transactions

```sql
-- Check funding transactions in database
SELECT * FROM transactions WHERE type IN ('FUNDING', 'MINTING', 'FAUCET_FUNDING');

-- Should see:
-- - ETH FUNDING transaction
-- - TEST-USDC MINTING transaction
-- - TEST-DAI MINTING transaction
-- - XRP FAUCET_FUNDING transaction (if successful)
```

---

## Step 10: Verify Transaction Hashes (15 minutes)

### Ethereum Transactions

1. Copy ETH funding txHash from database
2. Visit: `https://sepolia.etherscan.io/tx/{txHash}`
3. Verify transaction exists and is confirmed

### XRP Transactions

1. Copy XRP funding txHash from database
2. If real hash: Visit `https://testnet.xrpl.org/transactions/{txHash}`
3. If synthetic hash (starts with "XRP_FAUCET_"): Mark for manual review

### Solana Transactions (if enabled)

1. Copy SOL funding signature from database
2. Visit: `https://explorer.solana.com/tx/{signature}?cluster=devnet`
3. Verify transaction exists

---

## Common Issues and Solutions

### Issue 1: Circular Dependency Error

**Error**: `The dependencies of some beans in the application context form a cycle`

**Solution**: Add `@Lazy` annotation to TransactionService in WalletService:

```java
public WalletService(
    UserRepository userRepository,
    Web3j web3j,
    XrpWalletService xrpWalletService,
    SolanaWalletService solanaWalletService,
    ContractService contractService,
    @Lazy TransactionService transactionService
) {
    this.transactionService = transactionService;
}
```

### Issue 2: XRP Faucet Returns Null

**Error**: XRP faucet funding returns null txHash

**Solution**: This is expected behavior when:
- Faucet is rate-limited
- Network is slow
- Account already funded

Check logs for synthetic hash generation.

### Issue 3: Migration Already Applied

**Error**: `Migration checksum mismatch`

**Solution**:
1. Check if migration already ran: `SELECT * FROM flyway_schema_history;`
2. If needed, manually update: `ALTER TABLE transactions ADD COLUMN type VARCHAR(50);`
3. Or reset Flyway: `./gradlew flywayClean flywayMigrate` (DEV ONLY!)

### Issue 4: Tests Fail - Method Not Found

**Error**: `Method 'recordFundingTransaction' not found`

**Solution**:
1. Verify TransactionService has the new methods
2. Rebuild project: `./gradlew clean build`
3. Restart IDE to refresh caches

---

## Verification Checklist

After implementation, verify:

- [ ] Database migration applied successfully
- [ ] `type` column added to transactions table
- [ ] Indexes created on `user_id, type, timestamp`
- [ ] TransactionService has `recordFundingTransaction()` method
- [ ] XrpWalletService.fundWalletFromFaucet() returns txHash (not address)
- [ ] WalletService.fundWallet() records ETH funding
- [ ] WalletService.fundTestTokens() records USDC/DAI minting
- [ ] WalletService.createUserWithWalletAndFunding() records XRP funding
- [ ] All 36 new tests pass
- [ ] Manual testing shows transactions recorded correctly
- [ ] Transaction hashes verified on blockchain explorers

---

## Performance Notes

**Database Queries**:
- Indexes added for `user_id, type, timestamp` - queries should be <50ms
- Funding transaction queries are read-heavy, write-light

**Blockchain Calls**:
- ETH funding: ~10-30 seconds (waiting for mining)
- USDC/DAI minting: ~10-30 seconds (same as ETH)
- XRP faucet: 3-5 seconds (async + polling)
- Solana airdrop: 2-3 seconds (devnet)

**Total User Creation Time**:
- Without funding: ~1 second
- With ETH funding: ~10-30 seconds
- With ETH + XRP + tokens: ~30-60 seconds

---

## Next Steps

After completing implementation:

1. **Code Review**: Have team review changes
2. **Staging Deploy**: Deploy to staging environment
3. **Integration Testing**: Test with real testnets
4. **Monitoring**: Set up alerts for funding failures
5. **Documentation**: Update user docs for transaction history
6. **Production Deploy**: Deploy after successful staging tests

---

**Implementation Time Estimate**: 4-5 hours
**Testing Time Estimate**: 1-2 hours
**Total**: 5-7 hours for complete implementation

**Good luck!** 🚀

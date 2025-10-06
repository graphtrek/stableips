package co.grtk.stableips.service;

import com.google.common.primitives.UnsignedInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.client.faucet.FaucetClient;
import org.xrpl.xrpl4j.client.faucet.FundAccountRequest;
import org.xrpl.xrpl4j.crypto.keys.Seed;
import org.xrpl.xrpl4j.crypto.keys.Base58EncodedSecret;
import org.xrpl.xrpl4j.crypto.keys.Entropy;
import org.xrpl.xrpl4j.crypto.signing.bc.BcSignatureService;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoRequestParams;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoResult;
import org.xrpl.xrpl4j.model.client.fees.FeeResult;
import org.xrpl.xrpl4j.model.client.transactions.SubmitResult;
import org.xrpl.xrpl4j.model.transactions.Address;
import org.xrpl.xrpl4j.model.transactions.Payment;
import org.xrpl.xrpl4j.model.transactions.XrpCurrencyAmount;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing XRP Ledger wallet operations and transactions.
 *
 * <p>This service handles wallet generation, funding, balance queries, and XRP transfers
 * on the XRP Ledger testnet. It supports multiple seed formats for backward compatibility
 * with legacy wallet data and provides automatic seed format detection and migration.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>ED25519 wallet generation with secure entropy-based seeds</li>
 *   <li>Testnet funding via faucet API or configured funding wallet</li>
 *   <li>XRP balance queries using XRPL client</li>
 *   <li>Payment transaction signing and submission</li>
 *   <li>Legacy seed format detection and migration guidance</li>
 * </ul>
 *
 * <p><strong>Seed Format Support:</strong>
 * <ul>
 *   <li>Base58-encoded secrets (starts with 's')</li>
 *   <li>Hex-encoded entropy (32 hex characters = 16 bytes)</li>
 *   <li>Corrupted format detection (Seed.toString() output)</li>
 *   <li>Legacy address-only format detection (starts with 'r')</li>
 * </ul>
 *
 * <p><strong>Security Note:</strong> This implementation uses in-memory seed caching
 * for demo purposes. Production systems should use encrypted key storage solutions
 * like hardware security modules (HSM) or encrypted key management services (KMS).</p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 * @see XrplClient
 * @see Seed
 */
@Service
public class XrpWalletService {

    private static final Logger log = LoggerFactory.getLogger(XrpWalletService.class);

    // Error message constants for seed parsing
    private static final String CORRUPTED_SEED_PREFIX = "Seed{";
    private static final String ERROR_CORRUPTED_SEED =
        "XRP wallet seed is corrupted. Please regenerate your XRP wallet using the 'Regenerate XRP Wallet' button. " +
        "This error occurs because the wallet was created with an older version that stored the seed incorrectly.";
    private static final String ERROR_ADDRESS_AS_SEED =
        "Cannot derive seed from XRP address. Please regenerate your wallet. " +
        "This error occurs because the wallet was created with an older version that didn't properly store the seed.";
    private static final String ERROR_UNSUPPORTED_FORMAT_PREFIX =
        "Unsupported XRP seed format. Please regenerate your XRP wallet using the 'Regenerate XRP Wallet' button. " +
        "Expected: base58 (starts with 's') or 32-character hex, but got: ";

    private final XrplClient xrplClient;
    private final FaucetClient faucetClient;
    private final BcSignatureService signatureService;
    private final Map<String, Seed> seedCache = new HashMap<>(); // Temporary storage

    @Value("${xrp.funding.address:}")
    private String fundingAddress;

    @Value("${xrp.funding.secret:}")
    private String fundingSecret;

    @Value("${xrp.funding.initial-amount:10}")
    private BigDecimal initialAmount;

    /**
     * Constructs an XrpWalletService with XRPL client connection and signature service.
     *
     * <p>Initializes the XRP Ledger client for testnet operations and configures
     * the faucet client for automated wallet funding. The BouncyCastle signature
     * service is used for transaction signing.</p>
     *
     * @param xrplClient XRPL client configured for testnet or mainnet connection
     */
    public XrpWalletService(XrplClient xrplClient) {
        this.xrplClient = xrplClient;
        this.faucetClient = FaucetClient.construct(okhttp3.HttpUrl.parse("https://faucet.altnet.rippletest.net"));
        this.signatureService = new BcSignatureService();
    }

    /**
     * Generates a new XRP Ledger wallet with ED25519 cryptography.
     *
     * <p>This method creates a new random ED25519 seed, derives the corresponding
     * key pair and address, and stores the seed as hex-encoded entropy (32 characters)
     * for future transaction signing.</p>
     *
     * <p><strong>Security Warning:</strong> The seed is cached in memory and returned
     * as plaintext hex. In production, seeds should be encrypted before storage using
     * AES-256-GCM or stored in hardware security modules (HSM).</p>
     *
     * <p>The generated seed format (32-character hex) is compatible with
     * {@link #parseSeedFromString(String)} for future transaction signing operations.
     *
     * <p>Example output:
     * <pre>{@code
     * XrpWallet wallet = generateWallet();
     * // wallet.getAddress() -> "rN7n7otQDd6FczFgLdllrq4OhiX1zp7n8"
     * // wallet.getSecret() -> "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6" (16 bytes as hex)
     * }</pre>
     *
     * @return XrpWallet containing the XRP address and hex-encoded seed
     */
    public XrpWallet generateWallet() {
        // Generate a random ED25519 seed
        Seed seed = Seed.ed25519Seed();
        var keyPair = seed.deriveKeyPair();
        Address address = keyPair.publicKey().deriveAddress();

        // Get the 16-byte seed entropy and store as hex (32 characters)
        // In production, this should be encrypted before storage
        String seedHex = seed.decodedSeed().bytes().hexValue();

        // Cache seed for later use
        seedCache.put(address.value(), seed);

        return new XrpWallet(
            address.value(),
            seedHex // Store 16-byte seed as hex (32 characters)
        );
    }

    /**
     * Funds an XRP Ledger wallet using the testnet faucet.
     *
     * <p>This method requests funding from the XRP testnet faucet at
     * https://faucet.altnet.rippletest.net. The faucet typically provides
     * 1,000 XRP for testing purposes.</p>
     *
     * <p><strong>Important:</strong> The XRP faucet API does not return transaction hashes.
     * This method generates a synthetic tracking ID for logging purposes. For production
     * systems, use {@link #fundUserWallet(String)} with a configured funding wallet,
     * which returns real blockchain transaction hashes.</p>
     *
     * <p>Synthetic transaction ID format: {@code XRP_FAUCET_<address_prefix>_<timestamp>}
     *
     * @param address the XRP address to fund (must be a valid XRP address starting with 'r')
     * @return synthetic transaction ID for tracking (format: XRP_FAUCET_&lt;prefix&gt;_&lt;timestamp&gt;),
     *         or null if faucet request fails
     */
    public String fundWalletFromFaucet(String address) {
        try {
            // Request faucet funding
            FundAccountRequest fundRequest = FundAccountRequest.of(Address.of(address));
            faucetClient.fundAccount(fundRequest);

            log.info("Requested faucet funding for XRP wallet: {}", address);

            // Generate synthetic tracking ID
            // Format: XRP_FAUCET_<address_prefix>_<timestamp>
            String syntheticTxId = "XRP_FAUCET_" + address.substring(0, Math.min(8, address.length()))
                + "_" + System.currentTimeMillis();

            log.info("Generated synthetic txId for XRP faucet: {}", syntheticTxId);
            return syntheticTxId;
        } catch (Exception e) {
            log.error("Failed to fund XRP wallet from faucet: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves the XRP balance for a given address.
     *
     * <p>This method queries the XRP Ledger to retrieve the current account balance
     * in XRP. The balance is automatically converted from drops (1 XRP = 1,000,000 drops)
     * to XRP units.</p>
     *
     * <p>If the account does not exist on the ledger (not yet activated), this method
     * returns {@link BigDecimal#ZERO}. XRP Ledger accounts require a minimum reserve
     * (typically 10 XRP) to be activated.</p>
     *
     * @param address the XRP address to query (must be a valid address starting with 'r')
     * @return account balance in XRP (e.g., 100.50), or {@link BigDecimal#ZERO} if
     *         the account does not exist or query fails
     */
    public BigDecimal getBalance(String address) {
        try {
            AccountInfoRequestParams requestParams = AccountInfoRequestParams.of(Address.of(address));
            AccountInfoResult accountInfo = xrplClient.accountInfo(requestParams);

            XrpCurrencyAmount balance = accountInfo.accountData().balance();
            return new BigDecimal(balance.value().toString()).divide(new BigDecimal("1000000"));
        } catch (Exception e) {
            log.error("Failed to get XRP balance for {}: {}", address, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Sends XRP from one address to another using the stored seed.
     *
     * <p>This method handles XRP payment transactions by signing and submitting them
     * to the XRP Ledger. It supports two input modes:</p>
     * <ul>
     *   <li><strong>Address lookup:</strong> If the input is an address in the seed cache,
     *       the cached seed is retrieved</li>
     *   <li><strong>Direct seed:</strong> If the input is a seed string, it is parsed
     *       using {@link #parseSeedFromString(String)} and cached</li>
     * </ul>
     *
     * <p><strong>Transaction Process:</strong></p>
     * <ol>
     *   <li>Derive source address from seed</li>
     *   <li>Query account sequence number from ledger</li>
     *   <li>Retrieve current network fee from ledger</li>
     *   <li>Build Payment transaction with amount converted to drops</li>
     *   <li>Sign transaction with ED25519 private key</li>
     *   <li>Submit signed transaction to ledger</li>
     *   <li>Verify tesSUCCESS result code</li>
     * </ol>
     *
     * <p><strong>Amount Conversion:</strong> The amount parameter is in XRP units
     * and is automatically converted to drops (1 XRP = 1,000,000 drops).</p>
     *
     * <p>Example usage:
     * <pre>{@code
     * // Send using cached address
     * String txHash = sendXrp("rSourceAddress123", "rDestination456", new BigDecimal("10.5"));
     *
     * // Send using direct seed
     * String txHash = sendXrp("a1b2c3d4e5f6...", "rDestination456", new BigDecimal("10.5"));
     * }</pre>
     *
     * @param fromAddressOrSeed either the XRP address (for seed cache lookup) or the seed
     *        string itself in hex or base58 format
     * @param toAddress the recipient XRP address (must start with 'r')
     * @param amount the transfer amount in XRP units (e.g., 10.5 XRP)
     * @return the blockchain transaction hash if successful
     * @throws RuntimeException if the transaction fails (insufficient balance, invalid address,
     *         network error, or transaction result is not tesSUCCESS)
     * @throws IllegalArgumentException if the seed format is invalid or corrupted
     *         (see {@link #parseSeedFromString(String)} for details)
     */
    public String sendXrp(String fromAddressOrSeed, String toAddress, BigDecimal amount) {
        try {
            Seed seed;
            String fromAddress;

            // First try to get from cache (if it's an address)
            if (seedCache.containsKey(fromAddressOrSeed)) {
                seed = seedCache.get(fromAddressOrSeed);
                fromAddress = fromAddressOrSeed;
            } else {
                // Otherwise treat it as a seed string and store it in cache
                seed = parseSeedFromString(fromAddressOrSeed);
                fromAddress = seed.deriveKeyPair().publicKey().deriveAddress().value();
                seedCache.put(fromAddress, seed);
            }

            // Get account info for sequence number
            AccountInfoRequestParams accountInfoParams = AccountInfoRequestParams.of(Address.of(fromAddress));
            AccountInfoResult accountInfo = xrplClient.accountInfo(accountInfoParams);
            UnsignedInteger sequence = accountInfo.accountData().sequence();

            // Get current network fee
            FeeResult feeResult = xrplClient.fee();
            XrpCurrencyAmount openLedgerFee = feeResult.drops().openLedgerFee();

            // Convert amount to drops (1 XRP = 1,000,000 drops)
            XrpCurrencyAmount xrpAmount = XrpCurrencyAmount.ofDrops(
                amount.multiply(new BigDecimal("1000000")).longValue()
            );

            // Build payment transaction
            Payment payment = Payment.builder()
                .account(Address.of(fromAddress))
                .destination(Address.of(toAddress))
                .amount(xrpAmount)
                .sequence(sequence)
                .fee(openLedgerFee)
                .signingPublicKey(seed.deriveKeyPair().publicKey())
                .build();

            // Sign the transaction
            var signedTransaction = signatureService.sign(seed.deriveKeyPair().privateKey(), payment);

            // Submit to the ledger
            SubmitResult<Payment> submitResult = xrplClient.submit(signedTransaction);

            if (submitResult.engineResult().equals("tesSUCCESS")) {
                String txHash = submitResult.transactionResult().hash().value();
                log.info("XRP transfer successful: {}", txHash);
                return txHash;
            } else {
                throw new RuntimeException("XRP transfer failed: " + submitResult.engineResult());
            }

        } catch (Exception e) {
            log.error("Failed to send XRP: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send XRP: " + e.getMessage(), e);
        }
    }

    /**
     * Funds a user wallet from the configured funding wallet or testnet faucet.
     *
     * <p>This method provides automatic funding for new user wallets. It attempts to use
     * the configured funding wallet (if {@code xrp.funding.secret} is set in application
     * properties), otherwise falls back to the testnet faucet.</p>
     *
     * <p><strong>Funding Modes:</strong></p>
     * <ul>
     *   <li><strong>Configured Funding Wallet:</strong> Uses {@link #sendXrp(String, String, BigDecimal)}
     *       to transfer XRP from the system wallet. Returns real transaction hash.</li>
     *   <li><strong>Testnet Faucet:</strong> Uses {@link #fundWalletFromFaucet(String)}
     *       when funding wallet is not configured. Returns synthetic tracking ID.</li>
     * </ul>
     *
     * <p>The funding amount is controlled by the {@code xrp.funding.initial-amount}
     * application property (default: 10 XRP).</p>
     *
     * @param toAddress the user's XRP address to fund
     * @return transaction hash (if funding wallet configured) or synthetic tracking ID
     *         (if faucet used)
     */
    public String fundUserWallet(String toAddress) {
        if (fundingSecret == null || fundingSecret.isEmpty()) {
            log.info("XRP funding wallet not configured. Using faucet instead.");
            return fundWalletFromFaucet(toAddress);
        }

        return sendXrp(fundingSecret, toAddress, initialAmount);
    }

    /**
     * Parses an XRP Ledger seed from various string formats with automatic format detection.
     *
     * <p>This method supports multiple seed formats for backward compatibility with legacy
     * wallet data. It detects corrupted seed formats and provides actionable error messages
     * to guide users through wallet regeneration.</p>
     *
     * <p><strong>Supported Formats:</strong></p>
     * <ul>
     *   <li><strong>Base58-encoded secret:</strong> Starts with 's' (e.g., "sEdTM1uX8pu2do5XvTnutH6HsouMaM2")
     *       - Standard XRPL secret format
     *       - Parsed using {@link Seed#fromBase58EncodedSecret(Base58EncodedSecret)}</li>
     *   <li><strong>Hex-encoded entropy:</strong> 32 hex characters (e.g., "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6")
     *       - Represents 16 bytes of entropy
     *       - Parsed using {@link Seed#ed25519SeedFromEntropy(Entropy)}</li>
     * </ul>
     *
     * <p><strong>Detected Error Formats:</strong></p>
     * <ul>
     *   <li><strong>Corrupted Seed.toString() output:</strong> Starts with "Seed{"
     *       - Indicates seed was stored using toString() instead of proper encoding
     *       - Requires wallet regeneration (seed cannot be recovered)</li>
     *   <li><strong>XRP Address:</strong> Starts with 'r'
     *       - Indicates address was stored instead of seed (legacy bug)
     *       - Requires wallet regeneration (private key cannot be derived)</li>
     *   <li><strong>Unknown format:</strong> Any other format
     *       - Unsupported encoding or corrupted data</li>
     * </ul>
     *
     * <p><strong>Error Recovery:</strong> When corrupted or unsupported formats are detected,
     * this method throws {@link IllegalArgumentException} with user-friendly messages
     * directing users to regenerate their wallet via the dashboard.</p>
     *
     * <p>Example usage:
     * <pre>{@code
     * // Valid hex seed
     * Seed seed = parseSeedFromString("a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6");
     *
     * // Valid base58 secret
     * Seed seed = parseSeedFromString("sEdTM1uX8pu2do5XvTnutH6HsouMaM2");
     *
     * // Corrupted format (throws exception)
     * Seed seed = parseSeedFromString("Seed{value=[redacted], destroyed=false}");
     * // Throws: IllegalArgumentException with regeneration guidance
     * }</pre></p>
     *
     * @param seedString the seed string in any supported format (base58, hex, or legacy)
     * @return parsed Seed object ready for key derivation
     * @throws IllegalArgumentException if the seed string is null/empty, corrupted (Seed.toString()),
     *         is an address (starts with 'r'), or is an unsupported format. Error messages
     *         provide actionable guidance for wallet regeneration.
     * @throws RuntimeException if seed parsing fails due to invalid entropy or encoding errors
     */
    private Seed parseSeedFromString(String seedString) {
        // Input validation
        if (seedString == null || seedString.trim().isEmpty()) {
            throw new IllegalArgumentException("Seed string cannot be null or empty");
        }

        try {
            // Detect corrupted Seed.toString() format (e.g., "Seed{value=[redacted], destroyed=false}")
            if (seedString.startsWith(CORRUPTED_SEED_PREFIX)) {
                throw new IllegalArgumentException(ERROR_CORRUPTED_SEED);
            }
            // Try parsing as base58 encoded secret (starts with 's')
            if (seedString.startsWith("s")) {
                return Seed.fromBase58EncodedSecret(Base58EncodedSecret.of(seedString));
            }
            // If it starts with 'r', it's an address (legacy data) - cannot recover seed
            if (seedString.startsWith("r")) {
                throw new IllegalArgumentException(ERROR_ADDRESS_AS_SEED);
            }
            // Try parsing as hex-encoded seed (32 character hex = 16 bytes)
            if (seedString.matches("[0-9a-fA-F]{32}")) {
                byte[] seedBytes = hexStringToByteArray(seedString);
                return Seed.ed25519SeedFromEntropy(Entropy.of(seedBytes));
            }
            // Otherwise unsupported format
            throw new IllegalArgumentException(
                ERROR_UNSUPPORTED_FORMAT_PREFIX + seedString.substring(0, Math.min(20, seedString.length()))
            );
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse seed: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a hexadecimal string to a byte array.
     *
     * <p>This utility method parses a hex string (e.g., "a1b2c3d4") into its
     * binary byte array representation. Each pair of hex characters represents
     * one byte.</p>
     *
     * <p>Used internally by {@link #parseSeedFromString(String)} to convert
     * hex-encoded seeds (32 hex characters) into 16-byte entropy for ED25519
     * seed reconstruction.</p>
     *
     * @param hex the hexadecimal string (must have even length, characters 0-9, a-f, A-F)
     * @return byte array representation of the hex string
     * @throws NumberFormatException if the string contains non-hex characters
     */
    private static byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Simple DTO for XRP wallet credentials
     */
    public static class XrpWallet {
        private final String address;
        private final String secret;

        public XrpWallet(String address, String secret) {
            this.address = address;
            this.secret = secret;
        }

        public String getAddress() {
            return address;
        }

        public String getSecret() {
            return secret;
        }
    }
}

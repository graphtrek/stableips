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

@Service
public class XrpWalletService {

    private static final Logger log = LoggerFactory.getLogger(XrpWalletService.class);

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

    public XrpWalletService(XrplClient xrplClient) {
        this.xrplClient = xrplClient;
        this.faucetClient = FaucetClient.construct(okhttp3.HttpUrl.parse("https://faucet.altnet.rippletest.net"));
        this.signatureService = new BcSignatureService();
    }

    /**
     * Generate a new XRP wallet (address + secret)
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
     * Fund a wallet using the testnet faucet
     * Returns synthetic transaction ID for tracking (faucet doesn't provide real txHash)
     *
     * Note: The XRP faucet API doesn't return transaction hashes. For a production system,
     * you would use a funded wallet (like fundUserWallet with fundingSecret configured)
     * which uses sendXrp() and returns real blockchain transaction hashes.
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
     * Get XRP balance for an address
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
     * Send XRP from one address to another using the seed string
     * @param fromAddressOrSeed - can be either the XRP address (to look up in cache) or the seed string itself
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
     * Fund a user wallet from the configured funding wallet
     */
    public String fundUserWallet(String toAddress) {
        if (fundingSecret == null || fundingSecret.isEmpty()) {
            log.info("XRP funding wallet not configured. Using faucet instead.");
            return fundWalletFromFaucet(toAddress);
        }

        return sendXrp(fundingSecret, toAddress, initialAmount);
    }

    /**
     * Parse a Seed from various string formats
     */
    private Seed parseSeedFromString(String seedString) {
        try {
            // Try parsing as base58 encoded secret (starts with 's')
            if (seedString.startsWith("s")) {
                return Seed.fromBase58EncodedSecret(Base58EncodedSecret.of(seedString));
            }
            // If it starts with 'r', it's an address (legacy data) - cannot recover seed
            if (seedString.startsWith("r")) {
                throw new IllegalArgumentException(
                    "Cannot derive seed from XRP address. Please regenerate your wallet. " +
                    "This error occurs because the wallet was created with an older version that didn't properly store the seed."
                );
            }
            // Try parsing as hex-encoded seed (32 character hex = 16 bytes)
            if (seedString.matches("[0-9a-fA-F]{32}")) {
                byte[] seedBytes = hexStringToByteArray(seedString);
                return Seed.ed25519SeedFromEntropy(Entropy.of(seedBytes));
            }
            // Otherwise unsupported format
            throw new IllegalArgumentException("Unsupported seed format: " + seedString);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse seed: " + e.getMessage(), e);
        }
    }

    /**
     * Convert hex string to byte array
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

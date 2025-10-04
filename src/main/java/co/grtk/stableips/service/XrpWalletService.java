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
        Seed seed = Seed.ed25519Seed();
        var keyPair = seed.deriveKeyPair();
        Address address = keyPair.publicKey().deriveAddress();

        // Cache seed for later use (in production, store encrypted in DB)
        seedCache.put(address.value(), seed);

        return new XrpWallet(
            address.value(),
            address.value() // Store address as "secret" for now (will use seed from cache)
        );
    }

    /**
     * Fund a wallet using the testnet faucet
     */
    public String fundWalletFromFaucet(String address) {
        try {
            FundAccountRequest fundRequest = FundAccountRequest.of(Address.of(address));
            faucetClient.fundAccount(fundRequest);
            log.info("Funded XRP wallet from faucet: {}", address);
            return address;
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
     * Send XRP from one address to another
     */
    public String sendXrp(String fromAddress, String toAddress, BigDecimal amount) {
        try {
            // Get the seed from cache
            Seed seed = seedCache.get(fromAddress);
            if (seed == null) {
                throw new RuntimeException("Seed not found for address: " + fromAddress);
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

        return sendXrp(fundingAddress, toAddress, initialAmount);
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

package co.grtk.stableips.service;

import com.google.common.primitives.UnsignedInteger;
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
            System.out.println("Funded XRP wallet from faucet: " + address);
            return address;
        } catch (Exception e) {
            System.err.println("Failed to fund XRP wallet from faucet: " + e.getMessage());
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
            System.err.println("Failed to get XRP balance for " + address + ": " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Send XRP from one address to another
     */
    public String sendXrp(String fromAddress, String toAddress, BigDecimal amount) {
        System.out.println("XRP transfer not yet implemented - from: " + fromAddress + " to: " + toAddress + " amount: " + amount);
        return "pending_xrp_transfer";
    }

    /**
     * Fund a user wallet from the configured funding wallet
     */
    public String fundUserWallet(String toAddress) {
        if (fundingSecret == null || fundingSecret.isEmpty()) {
            System.out.println("XRP funding wallet not configured. Using faucet instead.");
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

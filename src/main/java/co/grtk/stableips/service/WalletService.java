package co.grtk.stableips.service;

import co.grtk.stableips.model.User;
import co.grtk.stableips.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

@Service
@Transactional
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final UserRepository userRepository;
    private final Web3j web3j;
    private final XrpWalletService xrpWalletService;
    private final SolanaWalletService solanaWalletService;
    private final ContractService contractService;
    private final TransactionService transactionService;

    @Value("${wallet.funding.private-key:}")
    private String fundingPrivateKey;

    @Value("${wallet.funding.initial-amount:10}")
    private BigDecimal initialAmount;

    @Value("${token.funding.initial-usdc:1000}")
    private BigDecimal initialUsdcAmount;

    @Value("${token.funding.initial-dai:1000}")
    private BigDecimal initialDaiAmount;

    public WalletService(
        UserRepository userRepository,
        Web3j web3j,
        XrpWalletService xrpWalletService,
        SolanaWalletService solanaWalletService,
        ContractService contractService,
        @Lazy TransactionService transactionService
    ) {
        this.userRepository = userRepository;
        this.web3j = web3j;
        this.xrpWalletService = xrpWalletService;
        this.solanaWalletService = solanaWalletService;
        this.contractService = contractService;
        this.transactionService = transactionService;
    }

    public Credentials generateWallet(String username) {
        try {
            ECKeyPair keyPair = Keys.createEcKeyPair();
            return Credentials.create(keyPair);
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException("Failed to generate wallet for user: " + username, e);
        }
    }

    public User createUserWithWallet(String username) {
        // Generate Ethereum wallet
        Credentials credentials = generateWallet(username);

        // Generate XRP wallet
        XrpWalletService.XrpWallet xrpWallet = xrpWalletService.generateWallet();

        // Generate Solana wallet
        SolanaWalletService.SolanaWallet solanaWallet = solanaWalletService.generateWallet();

        User user = new User(username, credentials.getAddress());
        user.setPrivateKey(credentials.getEcKeyPair().getPrivateKey().toString(16));
        user.setXrpAddress(xrpWallet.getAddress());
        user.setXrpSecret(xrpWallet.getSecret());
        user.setSolanaPublicKey(solanaWallet.getPublicKey());
        user.setSolanaPrivateKey(solanaWallet.getPrivateKey());

        return userRepository.save(user);
    }

    /**
     * Regenerate XRP wallet for a user (useful for fixing legacy data)
     */
    public User regenerateXrpWallet(User user) {
        XrpWalletService.XrpWallet xrpWallet = xrpWalletService.generateWallet();
        user.setXrpAddress(xrpWallet.getAddress());
        user.setXrpSecret(xrpWallet.getSecret());

        User savedUser = userRepository.save(user);

        // Fund the new wallet
        xrpWalletService.fundWalletFromFaucet(xrpWallet.getAddress());

        return savedUser;
    }

    public BigDecimal getEthBalance(String address) {
        try {
            BigInteger weiBalance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                .send()
                .getBalance();

            return Convert.fromWei(weiBalance.toString(), Convert.Unit.ETHER);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public Credentials getUserCredentials(String walletAddress) {
        User user = userRepository.findByWalletAddress(walletAddress)
            .orElseThrow(() -> new RuntimeException("User not found for wallet address: " + walletAddress));

        BigInteger privateKey = new BigInteger(user.getPrivateKey(), 16);
        ECKeyPair keyPair = ECKeyPair.create(privateKey);

        return Credentials.create(keyPair);
    }

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

        // Note: Solana funding removed - users can manually fund via https://faucet.solana.com/

        return user;
    }

    public BigDecimal getXrpBalance(String xrpAddress) {
        return xrpWalletService.getBalance(xrpAddress);
    }

    public BigDecimal getSolanaBalance(String publicKey) {
        return solanaWalletService.getBalance(publicKey);
    }

    /**
     * Fund a user's wallet with test USDC and DAI tokens
     * Requires test token contracts to be deployed and owner credentials configured
     * @param walletAddress The user's wallet address to receive tokens
     * @return Map containing transaction hashes for USDC and DAI minting
     */
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
}

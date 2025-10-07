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

/**
 * Service for managing cryptocurrency wallets across multiple blockchain networks.
 *
 * <p>This service provides comprehensive wallet operations including generation, funding,
 * and balance queries across Ethereum, XRP Ledger, and Solana blockchains. It coordinates
 * with blockchain-specific services to provide a unified wallet management interface.</p>
 *
 * <p>Key features:
 * <ul>
 *   <li>Multi-blockchain wallet generation (Ethereum, XRP, Solana)</li>
 *   <li>Automated wallet funding with ETH, XRP, and test tokens</li>
 *   <li>Balance queries across all supported networks</li>
 *   <li>User credential management and recovery</li>
 *   <li>Test token minting for USDC and EURC</li>
 * </ul>
 *
 * <p>Supported networks:
 * <ul>
 *   <li><strong>Ethereum</strong>: Sepolia testnet with ETH, USDC, and EURC support</li>
 *   <li><strong>XRP Ledger</strong>: Testnet with native XRP transfers</li>
 *   <li><strong>Solana</strong>: Devnet with SOL transfers</li>
 * </ul>
 *
 * @author StableIPs Development Team
 * @since 1.0
 * @see XrpWalletService
 * @see SolanaWalletService
 * @see ContractService
 */
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

    @Value("${token.funding.initial-eurc:1000}")
    private BigDecimal initialEurcAmount;

    /**
     * Constructs a WalletService with required blockchain service dependencies.
     *
     * @param userRepository repository for user entity persistence
     * @param web3j Web3j instance for Ethereum blockchain interaction
     * @param xrpWalletService service for XRP Ledger operations
     * @param solanaWalletService service for Solana blockchain operations
     * @param contractService service for ERC-20 token contract interactions
     * @param transactionService service for transaction logging (lazy to avoid circular dependency)
     */
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

    /**
     * Generates a new Ethereum wallet with cryptographically secure key pair.
     *
     * <p>This method creates an Ethereum-compatible wallet using secp256k1 elliptic curve
     * cryptography. The generated wallet is MetaMask-compatible and can be imported using
     * the private key.</p>
     *
     * @param username the username for logging purposes (not stored in wallet)
     * @return Ethereum credentials containing the key pair and address
     * @throws RuntimeException if wallet generation fails due to cryptographic algorithm issues
     */
    public Credentials generateWallet(String username) {
        try {
            ECKeyPair keyPair = Keys.createEcKeyPair();
            return Credentials.create(keyPair);
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException("Failed to generate wallet for user: " + username, e);
        }
    }

    /**
     * Creates a new user with wallets for all supported blockchain networks.
     *
     * <p>This method generates fresh wallets across three blockchains and persists
     * the user entity with all wallet credentials. The private keys are stored
     * securely in the database for later transaction signing.</p>
     *
     * <p>Generated wallets:
     * <ul>
     *   <li><strong>Ethereum</strong>: Address and private key</li>
     *   <li><strong>XRP Ledger</strong>: Address and secret</li>
     *   <li><strong>Solana</strong>: Public key and private key</li>
     * </ul>
     *
     * <p><strong>Note:</strong> This method does NOT fund the wallets. Use
     * {@link #createUserWithWalletAndFunding(String)} for automatic funding.</p>
     *
     * @param username the unique username for the new user
     * @return the persisted user entity with all wallet addresses
     * @see #createUserWithWalletAndFunding(String)
     */
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
     * Regenerates the XRP wallet for an existing user.
     *
     * <p>This method creates a new XRP Ledger wallet and updates the user's XRP credentials.
     * It's primarily used for migrating legacy users who were created before XRP support was
     * added, or for users whose XRP wallets were corrupted.</p>
     *
     * <p>After regeneration, the new wallet is automatically funded via the XRP testnet faucet
     * to provide initial XRP for transaction fees.</p>
     *
     * @param user the user whose XRP wallet needs regeneration
     * @return the updated user entity with new XRP credentials
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

    /**
     * Retrieves the ETH balance for an Ethereum address.
     *
     * <p>This method queries the Ethereum Sepolia testnet to get the current ETH balance
     * for the specified address. The result is converted from wei to ETH for readability.</p>
     *
     * @param address the Ethereum address to query
     * @return the ETH balance in Ether units, or BigDecimal.ZERO if query fails
     */
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

    /**
     * Retrieves user's Ethereum credentials for transaction signing.
     *
     * <p>This method reconstructs the Credentials object from the user's stored private key.
     * The credentials are required for signing and broadcasting Ethereum transactions.</p>
     *
     * @param walletAddress the user's Ethereum wallet address
     * @return the user's Ethereum credentials containing private key and address
     * @throws RuntimeException if no user is found with the specified wallet address
     */
    public Credentials getUserCredentials(String walletAddress) {
        User user = userRepository.findByWalletAddress(walletAddress)
            .orElseThrow(() -> new RuntimeException("User not found for wallet address: " + walletAddress));

        BigInteger privateKey = new BigInteger(user.getPrivateKey(), 16);
        ECKeyPair keyPair = ECKeyPair.create(privateKey);

        return Credentials.create(keyPair);
    }

    /**
     * Funds an Ethereum wallet with ETH from the configured funding wallet.
     *
     * <p>This method sends ETH from the system's funding wallet (configured via
     * environment variable) to a user's wallet. It's typically used for initial
     * wallet funding when new users are created.</p>
     *
     * <p>The transaction is logged in the database with type "FUNDING" for tracking.</p>
     *
     * <p><strong>Configuration:</strong> Requires FUNDED_SEPOLIA_WALLET_PRIVATE_KEY
     * environment variable to be set with a funded wallet's private key.</p>
     *
     * @param toAddress the recipient's Ethereum address
     * @param amountInEth the amount of ETH to send
     * @return the transaction hash, or null if funding wallet is not configured or transfer fails
     */
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

    /**
     * Creates a new user with multi-blockchain wallets and automatic funding.
     *
     * <p>This is a convenience method that combines wallet generation and funding in a
     * single operation. It creates wallets across Ethereum, XRP Ledger, and Solana, then
     * funds them with initial amounts for testing.</p>
     *
     * <p>Funding operations:
     * <ul>
     *   <li><strong>Ethereum</strong>: Funded with ETH from system wallet (configured amount)</li>
     *   <li><strong>XRP Ledger</strong>: Funded via XRP testnet faucet (1000 XRP)</li>
     *   <li><strong>Solana</strong>: Manual funding required via https://faucet.solana.com/</li>
     * </ul>
     *
     * <p>All funding transactions are logged in the database for tracking.</p>
     *
     * @param username the unique username for the new user
     * @return the persisted user entity with funded wallets
     */
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

    /**
     * Retrieves the XRP balance for an XRP Ledger address.
     *
     * @param xrpAddress the XRP Ledger address to query
     * @return the XRP balance
     */
    public BigDecimal getXrpBalance(String xrpAddress) {
        return xrpWalletService.getBalance(xrpAddress);
    }

    /**
     * Retrieves the SOL balance for a Solana public key.
     *
     * @param publicKey the Solana public key to query
     * @return the SOL balance
     */
    public BigDecimal getSolanaBalance(String publicKey) {
        return solanaWalletService.getBalance(publicKey);
    }

    /**
     * Funds a user's wallet with test USDC and EURC tokens for development.
     *
     * <p>This method mints test tokens to the specified wallet address using the
     * configured funding wallet as the minting authority. Both TEST-USDC and TEST-EURC
     * are minted with amounts specified in application.properties.</p>
     *
     * <p>Funding amounts (default 1000 each):
     * <ul>
     *   <li><strong>TEST-USDC</strong>: Configured via token.funding.initial-usdc</li>
     *   <li><strong>TEST-EURC</strong>: Configured via token.funding.initial-eurc</li>
     * </ul>
     *
     * <p>Both minting transactions are logged in the database with type "MINTING".</p>
     *
     * <p><strong>Requirements:</strong>
     * <ul>
     *   <li>Test token contracts must be deployed (TEST-USDC, TEST-EURC)</li>
     *   <li>Funding wallet must have owner/minter privileges on both contracts</li>
     *   <li>FUNDED_SEPOLIA_WALLET_PRIVATE_KEY environment variable must be set</li>
     * </ul>
     *
     * @param walletAddress the user's Ethereum wallet address to receive tokens
     * @return map containing transaction hashes with keys "usdc" and "eurc"
     * @throws IllegalStateException if funding wallet is not configured
     * @throws RuntimeException if minting fails for either token
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

            // Mint test EURC
            String eurcTxHash = contractService.mintTestTokens(
                ownerCredentials,
                walletAddress,
                initialEurcAmount,
                "TEST-EURC"
            );

            // Record EURC minting
            transactionService.recordFundingTransaction(
                user.getId(),
                walletAddress,
                initialEurcAmount,
                "TEST-EURC",
                "ETHEREUM",
                eurcTxHash,
                "MINTING"
            );

            return java.util.Map.of(
                "usdc", usdcTxHash,
                "eurc", eurcTxHash
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to fund test tokens: " + e.getMessage(), e);
        }
    }
}

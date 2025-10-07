package co.grtk.stableips.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for interacting with ERC-20 token smart contracts on Ethereum.
 *
 * <p>This service provides comprehensive ERC-20 token operations including transfers,
 * balance queries, and test token minting. It handles proper decimal conversion,
 * gas estimation, and transaction management for multiple token types (USDC, EURC,
 * TEST-USDC, TEST-EURC) as well as native ETH transfers.</p>
 *
 * <p>Key features:
 * <ul>
 *   <li>Multi-token support with automatic decimal handling (6 for USDC, 18 for EURC/ETH)</li>
 *   <li>Smart contract interaction via Web3j with RawTransactionManager</li>
 *   <li>Intelligent gas estimation with configurable buffers</li>
 *   <li>Token decimal caching to minimize blockchain queries</li>
 *   <li>Test token minting for development environments</li>
 * </ul>
 *
 * <p>Supported tokens:
 * <ul>
 *   <li><strong>USDC</strong>: Circle USD Coin (6 decimals on mainnet, may vary on testnets)</li>
 *   <li><strong>EURC</strong>: Circle Euro Coin (18 decimals)</li>
 *   <li><strong>ETH</strong>: Native Ethereum (18 decimals)</li>
 *   <li><strong>TEST-USDC</strong>: Test USDC token for development (18 decimals)</li>
 *   <li><strong>TEST-EURC</strong>: Test EURC token for development (18 decimals)</li>
 * </ul>
 *
 * <p><strong>Note:</strong> XRP transfers are not handled by this service.
 * Use {@link XrpWalletService} for XRP Ledger operations.</p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 * @see XrpWalletService
 * @see WalletService
 */
@Service
public class ContractService {

    private static final Logger log = LoggerFactory.getLogger(ContractService.class);

    private static final int GAS_LIMIT_BUFFER_PERCENT = 120;
    private static final int GAS_PRICE_BUFFER_PERCENT = 110;
    private static final int PERCENTAGE_DIVISOR = 100;

    private final Web3j web3j;

    // Cache for token decimals to avoid repeated contract calls
    private final Map<String, Integer> decimalsCache = new ConcurrentHashMap<>();

    @Value("${contract.usdc.address:0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238}")
    private String usdcAddress;

    @Value("${contract.eurc.address:0xc339141906318E29c6D11f0F352097cfe967e7EE}")
    private String eurcAddress;

    @Value("${contract.test-usdc.address:}")
    private String testUsdcAddress;

    @Value("${contract.test-eurc.address:}")
    private String testEurcAddress;

    @Value("${blockchain.chain-id:11155111}")
    private long chainId;

    /**
     * Constructs a ContractService with Web3j connectivity.
     *
     * @param web3j the Web3j instance for blockchain interaction
     */
    public ContractService(Web3j web3j) {
        this.web3j = web3j;
    }

    /**
     * Executes a token or ETH transfer on the Ethereum blockchain.
     *
     * <p>This method handles both ERC-20 token transfers (USDC, EURC) and native ETH transfers.
     * It validates addresses, converts amounts to proper decimal units, estimates gas, and
     * broadcasts the transaction to the network.</p>
     *
     * <p>Transfer flow:
     * <ol>
     *   <li>Validate token type and recipient address</li>
     *   <li>Route to appropriate transfer method (native ETH or ERC-20)</li>
     *   <li>Convert amount using token-specific decimals</li>
     *   <li>Estimate gas with 20% buffer for reliability</li>
     *   <li>Sign and broadcast transaction</li>
     * </ol>
     *
     * <p><strong>Gas estimation:</strong> Uses network-based estimation with a {GAS_LIMIT_BUFFER_PERCENT}%
     * buffer to reduce transaction failure risk.</p>
     *
     * @param credentials the sender's wallet credentials (private key)
     * @param recipient the recipient's Ethereum address (must be valid checksummed address)
     * @param amount the transfer amount in token units (e.g., 100.50 for 100.50 USDC)
     * @param token the token symbol (USDC, EURC, ETH)
     * @return the blockchain transaction hash
     * @throws IllegalArgumentException if token is XRP, recipient address is invalid, or token is unsupported
     * @throws RuntimeException if the blockchain transaction fails
     */
    public String transfer(Credentials credentials, String recipient, BigDecimal amount, String token) {
        // XRP transfers are handled by XrpWalletService, not here
        if ("XRP".equalsIgnoreCase(token)) {
            throw new IllegalArgumentException("XRP transfers should use XrpWalletService");
        }

        // Validate recipient address
        if (!WalletUtils.isValidAddress(recipient)) {
            throw new IllegalArgumentException("Invalid recipient address: " + recipient);
        }

        try {
            String contractAddress = getContractAddress(token);

            // ETH native transfer (no contract interaction)
            if ("ETH".equalsIgnoreCase(token)) {
                return transferNativeETH(credentials, recipient, amount);
            }

            // Validate contract address
            if (contractAddress != null && !WalletUtils.isValidAddress(contractAddress)) {
                throw new IllegalStateException("Invalid contract address configured for " + token);
            }

            // ERC-20 token transfer with proper decimal handling
            BigInteger value = convertToTokenUnits(amount, token, contractAddress);

            Function function = new Function(
                "transfer",
                Arrays.asList(new Address(recipient), new Uint256(value)),
                Collections.emptyList()
            );

            String encodedFunction = FunctionEncoder.encode(function);

            log.info("Preparing {} transfer: {} {} from {} to {} (value in token units: {})",
                token, amount, token, credentials.getAddress(), recipient, value);

            TransactionManager transactionManager = new RawTransactionManager(
                web3j, credentials, chainId
            );

            // Estimate gas for this specific transaction
            BigInteger gasLimit = estimateGas(credentials.getAddress(), contractAddress, encodedFunction);

            // Get current gas price from network
            BigInteger gasPrice = getGasPrice();

            EthSendTransaction transactionResponse = transactionManager.sendTransaction(
                gasPrice,
                gasLimit,
                contractAddress,
                encodedFunction,
                BigInteger.ZERO
            );

            if (transactionResponse.hasError()) {
                log.error("Transfer failed: token={}, amount={}, recipient={}, error={}",
                    token, amount, recipient, transactionResponse.getError().getMessage());
                throw new RuntimeException("Transfer failed: " + transactionResponse.getError().getMessage());
            }

            log.info("{} transfer successful: txHash={}, from={}, to={}, amount={}",
                token, transactionResponse.getTransactionHash(), credentials.getAddress(), recipient, amount);

            return transactionResponse.getTransactionHash();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid transfer request: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute {} transfer: token={}, amount={}, recipient={}, error={}",
                token, token, amount, recipient, e.getMessage(), e);
            throw new RuntimeException("Failed to execute transfer: " + e.getMessage(), e);
        }
    }

    private String transferNativeETH(Credentials credentials, String recipient, BigDecimal amount) {
        try {
            // ETH always uses 18 decimals
            BigInteger value = convertToTokenUnits(amount, "ETH", null);

            log.info("Preparing ETH transfer: {} ETH from {} to {} (value in wei: {})",
                amount, credentials.getAddress(), recipient, value);

            TransactionManager transactionManager = new RawTransactionManager(
                web3j, credentials, chainId
            );

            // Estimate gas for ETH transfer
            BigInteger gasLimit = estimateGas(credentials.getAddress(), recipient, "");

            // Get current gas price
            BigInteger gasPrice = getGasPrice();

            EthSendTransaction transactionResponse = transactionManager.sendTransaction(
                gasPrice,
                gasLimit,
                recipient,
                "",
                value
            );

            if (transactionResponse.hasError()) {
                log.error("ETH transfer failed: from={}, to={}, amount={}, error={}",
                    credentials.getAddress(), recipient, amount, transactionResponse.getError().getMessage());
                throw new RuntimeException("ETH transfer failed: " + transactionResponse.getError().getMessage());
            }

            log.info("ETH transfer successful: txHash={}, from={}, to={}, amount={}",
                transactionResponse.getTransactionHash(), credentials.getAddress(), recipient, amount);

            return transactionResponse.getTransactionHash();
        } catch (Exception e) {
            log.error("Failed to send ETH: from={}, to={}, amount={}, error={}",
                credentials.getAddress(), recipient, amount, e.getMessage(), e);
            throw new RuntimeException("Failed to send ETH: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the token balance for an Ethereum wallet address.
     *
     * <p>This method queries the blockchain to get the current ERC-20 token balance
     * at the specified address. It automatically handles decimal conversion to return
     * human-readable values.</p>
     *
     * <p><strong>Note:</strong> This method only handles ERC-20 tokens (USDC, EURC).
     * For ETH balance, use {@link WalletService#getEthBalance(String)}.
     * For XRP balance, use {@link XrpWalletService#getBalance(String)}.</p>
     *
     * @param walletAddress the Ethereum wallet address to query
     * @param token the token symbol (USDC, EURC)
     * @return the token balance in human-readable units, or BigDecimal.ZERO if query fails
     */
    public BigDecimal getBalance(String walletAddress, String token) {
        // XRP balance is handled by XrpWalletService
        if ("XRP".equalsIgnoreCase(token)) {
            return BigDecimal.ZERO; // Not handled here
        }

        // ETH balance is handled by WalletService.getEthBalance()
        if ("ETH".equalsIgnoreCase(token)) {
            return BigDecimal.ZERO; // Not handled here
        }

        // ERC-20 token balance
        try {
            String contractAddress = getContractAddress(token);

            Function function = new Function(
                "balanceOf",
                Collections.singletonList(new Address(walletAddress)),
                Collections.singletonList(new TypeReference<Uint256>() {})
            );

            String encodedFunction = FunctionEncoder.encode(function);

            EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(walletAddress, contractAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
            ).send();

            List<Type> result = FunctionReturnDecoder.decode(
                response.getValue(),
                function.getOutputParameters()
            );

            if (result.isEmpty()) {
                return BigDecimal.ZERO;
            }

            BigInteger balance = (BigInteger) result.get(0).getValue();
            return convertFromTokenUnits(balance, token, contractAddress);
        } catch (Exception e) {
            log.error("Failed to get {} balance for {}: {}", token, walletAddress, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Resolves the smart contract address for a given token symbol.
     *
     * <p>This method maps token symbols to their configured contract addresses on the
     * Ethereum Sepolia testnet. Native currencies (ETH, XRP) return null as they don't
     * have contract addresses.</p>
     *
     * @param token the token symbol (USDC, EURC, ETH, XRP)
     * @return the contract address, or null for native currencies
     * @throws IllegalArgumentException if the token is not supported
     */
    private String getContractAddress(String token) {
        return switch (token.toUpperCase()) {
            case "USDC" -> usdcAddress;
            case "EURC" -> eurcAddress;
            case "ETH", "XRP" -> null; // Native currencies don't have contract addresses
            default -> throw new IllegalArgumentException("Unsupported token: " + token);
        };
    }

    /**
     * Converts human-readable token amount to blockchain units using proper decimals.
     *
     * <p>This method transforms user-friendly amounts (like 100.50 USDC) into the smallest
     * unit representation required by smart contracts (like 100500000 for 6-decimal USDC).</p>
     *
     * @param amount the amount in human-readable format (e.g., 100 USDC)
     * @param token the token symbol (USDC, EURC, ETH, etc.)
     * @param contractAddress the contract address (null for native currencies)
     * @return the amount in token's smallest unit (e.g., 100000000 for 100 USDC with 6 decimals)
     */
    private BigInteger convertToTokenUnits(BigDecimal amount, String token, String contractAddress) {
        int decimals = getTokenDecimals(token, contractAddress);
        BigDecimal multiplier = BigDecimal.TEN.pow(decimals);
        BigInteger result = amount.multiply(multiplier).toBigInteger();

        log.debug("Converting {} {} to token units: {} (decimals: {})", amount, token, result, decimals);
        return result;
    }

    /**
     * Converts blockchain units to human-readable token amount.
     *
     * <p>This method transforms the smallest unit representation from smart contracts
     * (like 100500000) into user-friendly amounts (like 100.50 USDC).</p>
     *
     * @param value the amount in token's smallest unit
     * @param token the token symbol
     * @param contractAddress the contract address (null for native currencies)
     * @return the amount in human-readable format
     */
    private BigDecimal convertFromTokenUnits(BigInteger value, String token, String contractAddress) {
        int decimals = getTokenDecimals(token, contractAddress);
        BigDecimal divisor = BigDecimal.TEN.pow(decimals);
        return new BigDecimal(value).divide(divisor);
    }

    /**
     * Retrieves the number of decimals for a token with intelligent caching.
     *
     * <p>This method uses a multi-tier strategy to determine token decimals:</p>
     * <ol>
     *   <li>Check in-memory cache for previously queried tokens</li>
     *   <li>Use static configuration for known tokens (EURC: 18, ETH: 18, TEST-EURC: 18)</li>
     *   <li>Query the contract's decimals() function for USDC and unknown tokens</li>
     * </ol>
     *
     * <p><strong>Decimal values:</strong></p>
     * <ul>
     *   <li><strong>EURC</strong>: 18 decimals (standard for Euro Coin)</li>
     *   <li><strong>ETH</strong>: 18 decimals (native Ethereum)</li>
     *   <li><strong>USDC</strong>: 6 decimals (mainnet) or 18 decimals (Sepolia testnet) - queried dynamically</li>
     *   <li><strong>TEST-EURC</strong>: 18 decimals</li>
     *   <li><strong>TEST-USDC</strong>: 18 decimals</li>
     * </ul>
     *
     * @param token the token symbol (USDC, EURC, ETH, etc.)
     * @param contractAddress the contract address (null for native currencies)
     * @return the number of decimals for the token
     */
    private int getTokenDecimals(String token, String contractAddress) {
        String cacheKey = token.toUpperCase() + ":" + (contractAddress != null ? contractAddress : "native");

        // Check cache first
        if (decimalsCache.containsKey(cacheKey)) {
            return decimalsCache.get(cacheKey);
        }

        int decimals;

        // Static configuration for known tokens
        switch (token.toUpperCase()) {
            case "ETH":
            case "EURC":
            case "TEST-EURC":
                decimals = 18;
                break;
            case "USDC":
                // Real USDC uses 6 decimals, but Sepolia test USDC might use 18
                // Query the contract to be sure
                if (contractAddress != null) {
                    decimals = queryContractDecimals(contractAddress);
                    log.info("Queried USDC contract decimals: {} (address: {})", decimals, contractAddress);
                } else {
                    decimals = 6; // Default for real USDC
                    log.warn("No contract address for USDC, using default 6 decimals");
                }
                break;
            case "TEST-USDC":
                // Test USDC typically uses 18 decimals for simplicity
                decimals = 18;
                break;
            default:
                // For unknown tokens, try to query the contract
                if (contractAddress != null) {
                    decimals = queryContractDecimals(contractAddress);
                    log.info("Queried decimals for {}: {} (address: {})", token, decimals, contractAddress);
                } else {
                    decimals = 18; // Safe default
                    log.warn("Unknown token {} with no contract address, using default 18 decimals", token);
                }
        }

        // Cache the result
        decimalsCache.put(cacheKey, decimals);
        return decimals;
    }

    /**
     * Queries the decimals() function from an ERC-20 contract.
     *
     * <p>This method calls the standard ERC-20 decimals() function to retrieve
     * the decimal precision from the smart contract. If the query fails (network
     * issues, invalid contract, etc.), it returns 18 as a safe default.</p>
     *
     * @param contractAddress the ERC-20 contract address
     * @return the number of decimals, or 18 if query fails
     */
    private int queryContractDecimals(String contractAddress) {
        try {
            Function function = new Function(
                "decimals",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Uint8>() {})
            );

            String encodedFunction = FunctionEncoder.encode(function);

            EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(null, contractAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
            ).send();

            List<Type> result = FunctionReturnDecoder.decode(
                response.getValue(),
                function.getOutputParameters()
            );

            if (!result.isEmpty()) {
                int decimals = ((BigInteger) result.get(0).getValue()).intValue();
                log.debug("Successfully queried decimals from contract {}: {}", contractAddress, decimals);
                return decimals;
            }
        } catch (Exception e) {
            log.warn("Failed to query decimals from contract {}: {}. Using default 18.",
                contractAddress, e.getMessage());
        }

        return 18; // Default fallback
    }

    /**
     * Estimates gas required for a transaction with safety buffer.
     *
     * <p>This method queries the Ethereum network to estimate the gas required for
     * a transaction, then adds a {GAS_LIMIT_BUFFER_PERCENT}% buffer to reduce the risk
     * of out-of-gas errors. If estimation fails, it falls back to the Web3j default.</p>
     *
     * @param from the sender address
     * @param to the recipient address (contract or EOA)
     * @param data the transaction data (empty for ETH transfers, encoded function for contract calls)
     * @return the estimated gas limit with buffer applied
     */
    private BigInteger estimateGas(String from, String to, String data) {
        try {
            BigInteger estimated = web3j.ethEstimateGas(
                Transaction.createFunctionCallTransaction(
                    from, null, null, null, to, data
                )
            ).send().getAmountUsed();

            // Add buffer to estimated gas for reliability
            BigInteger buffered = estimated.multiply(BigInteger.valueOf(GAS_LIMIT_BUFFER_PERCENT))
                .divide(BigInteger.valueOf(PERCENTAGE_DIVISOR));

            log.debug("Gas estimation: {} (with {}% buffer: {})",
                estimated, GAS_LIMIT_BUFFER_PERCENT - PERCENTAGE_DIVISOR, buffered);
            return buffered;
        } catch (Exception e) {
            log.warn("Gas estimation failed, using default: {}", e.getMessage());
            return DefaultGasProvider.GAS_LIMIT;
        }
    }

    /**
     * Retrieves current network gas price with buffer for faster confirmation.
     *
     * <p>This method queries the Ethereum network for the current gas price, then applies
     * a {GAS_PRICE_BUFFER_PERCENT}% buffer to increase transaction priority and reduce
     * confirmation time. If the query fails, it falls back to the Web3j default.</p>
     *
     * @return the gas price in wei with buffer applied
     */
    private BigInteger getGasPrice() {
        try {
            EthGasPrice ethGasPrice = web3j.ethGasPrice().send();
            BigInteger gasPrice = ethGasPrice.getGasPrice();

            // Add buffer for faster confirmation
            BigInteger buffered = gasPrice.multiply(BigInteger.valueOf(GAS_PRICE_BUFFER_PERCENT))
                .divide(BigInteger.valueOf(PERCENTAGE_DIVISOR));

            log.debug("Gas price: {} wei (with {}% buffer: {} wei)",
                gasPrice, GAS_PRICE_BUFFER_PERCENT - PERCENTAGE_DIVISOR, buffered);
            return buffered;
        } catch (Exception e) {
            log.warn("Failed to get gas price, using default: {}", e.getMessage());
            return DefaultGasProvider.GAS_PRICE;
        }
    }

    /**
     * Mints test tokens to a recipient wallet for development and testing.
     *
     * <p>This method is only for deployed TestToken contracts (TEST-USDC, TEST-EURC)
     * that support the mint() function. It requires owner credentials with minting
     * privileges on the contract.</p>
     *
     * <p>Test token decimals: Both TEST-USDC and TEST-EURC use 18 decimals for simplicity.</p>
     *
     * @param ownerCredentials the credentials of the contract owner with minting rights
     * @param recipientAddress the address to receive the minted tokens
     * @param amount the amount in token units (e.g., 1000 for 1000 tokens)
     * @param token the token type (TEST-USDC or TEST-EURC)
     * @return the blockchain transaction hash
     * @throws IllegalStateException if test token contracts are not deployed
     * @throws IllegalArgumentException if token type is not TEST-USDC or TEST-EURC
     * @throws RuntimeException if the minting transaction fails
     */
    public String mintTestTokens(Credentials ownerCredentials, String recipientAddress, BigDecimal amount, String token) {
        try {
            String contractAddress = getTestContractAddress(token);

            if (contractAddress == null || contractAddress.isEmpty()) {
                throw new IllegalStateException("Test token contract not deployed. Please deploy contracts first.");
            }

            // Test tokens use 18 decimals
            BigInteger value = convertToTokenUnits(amount, token, contractAddress);

            Function function = new Function(
                "mint",
                Arrays.asList(new Address(recipientAddress), new Uint256(value)),
                Collections.emptyList()
            );

            String encodedFunction = FunctionEncoder.encode(function);

            TransactionManager transactionManager = new RawTransactionManager(
                web3j, ownerCredentials, chainId
            );

            EthSendTransaction transactionResponse = transactionManager.sendTransaction(
                DefaultGasProvider.GAS_PRICE,
                DefaultGasProvider.GAS_LIMIT,
                contractAddress,
                encodedFunction,
                BigInteger.ZERO
            );

            if (transactionResponse.hasError()) {
                throw new RuntimeException("Mint failed: " + transactionResponse.getError().getMessage());
            }

            return transactionResponse.getTransactionHash();
        } catch (Exception e) {
            throw new RuntimeException("Failed to mint test tokens: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves the smart contract address for test tokens.
     *
     * <p>This method maps test token symbols to their configured contract addresses.
     * Test tokens must be deployed manually before use.</p>
     *
     * @param token the test token symbol (TEST-USDC or TEST-EURC)
     * @return the contract address
     * @throws IllegalArgumentException if the token is not a supported test token
     */
    private String getTestContractAddress(String token) {
        return switch (token.toUpperCase()) {
            case "TEST-USDC" -> testUsdcAddress;
            case "TEST-EURC" -> testEurcAddress;
            default -> throw new IllegalArgumentException("Unsupported test token: " + token + ". Use TEST-USDC or TEST-EURC");
        };
    }
}

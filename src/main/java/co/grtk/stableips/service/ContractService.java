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

@Service
public class ContractService {

    private static final Logger log = LoggerFactory.getLogger(ContractService.class);

    private final Web3j web3j;

    // Cache for token decimals to avoid repeated contract calls
    private final Map<String, Integer> decimalsCache = new ConcurrentHashMap<>();

    @Value("${contract.usdc.address:0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238}")
    private String usdcAddress;

    @Value("${contract.dai.address:0x3e622317f8C93f7328350cF0B56d9eD4C620C5d6}")
    private String daiAddress;

    @Value("${contract.test-usdc.address:}")
    private String testUsdcAddress;

    @Value("${contract.test-dai.address:}")
    private String testDaiAddress;

    @Value("${blockchain.chain-id:11155111}")
    private long chainId;

    public ContractService(Web3j web3j) {
        this.web3j = web3j;
    }

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

    private String getContractAddress(String token) {
        return switch (token.toUpperCase()) {
            case "USDC" -> usdcAddress;
            case "DAI" -> daiAddress;
            case "ETH", "XRP" -> null; // Native currencies don't have contract addresses
            default -> throw new IllegalArgumentException("Unsupported token: " + token);
        };
    }

    /**
     * Convert token amount to blockchain units using proper decimals
     * @param amount Amount in human-readable format (e.g., 100 USDC)
     * @param token Token symbol (USDC, DAI, ETH, etc.)
     * @param contractAddress Contract address (null for native currencies)
     * @return Amount in token's smallest unit (e.g., 100000000 for 100 USDC with 6 decimals)
     */
    private BigInteger convertToTokenUnits(BigDecimal amount, String token, String contractAddress) {
        int decimals = getTokenDecimals(token, contractAddress);
        BigDecimal multiplier = BigDecimal.TEN.pow(decimals);
        BigInteger result = amount.multiply(multiplier).toBigInteger();

        log.debug("Converting {} {} to token units: {} (decimals: {})", amount, token, result, decimals);
        return result;
    }

    /**
     * Convert blockchain units to human-readable token amount
     * @param value Amount in token's smallest unit
     * @param token Token symbol
     * @param contractAddress Contract address (null for native currencies)
     * @return Amount in human-readable format
     */
    private BigDecimal convertFromTokenUnits(BigInteger value, String token, String contractAddress) {
        int decimals = getTokenDecimals(token, contractAddress);
        BigDecimal divisor = BigDecimal.TEN.pow(decimals);
        return new BigDecimal(value).divide(divisor);
    }

    /**
     * Get the number of decimals for a token
     * First checks static configuration, then queries the contract
     * Results are cached to avoid repeated queries
     *
     * @param token Token symbol (USDC, DAI, ETH, etc.)
     * @param contractAddress Contract address (null for native currencies)
     * @return Number of decimals (6 for USDC, 18 for DAI/ETH)
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
            case "DAI":
            case "TEST-DAI":
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
     * Query the decimals() function from an ERC20 contract
     * @param contractAddress The ERC20 contract address
     * @return Number of decimals, or 18 if query fails
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
     * Estimate gas for a transaction
     * @param from Sender address
     * @param to Recipient address
     * @param data Transaction data (empty for ETH transfers, encoded function for contract calls)
     * @return Estimated gas limit with 20% buffer
     */
    private BigInteger estimateGas(String from, String to, String data) {
        try {
            BigInteger estimated = web3j.ethEstimateGas(
                Transaction.createFunctionCallTransaction(
                    from, null, null, null, to, data
                )
            ).send().getAmountUsed();

            // Add 20% buffer to estimated gas
            BigInteger buffered = estimated.multiply(BigInteger.valueOf(120))
                .divide(BigInteger.valueOf(100));

            log.debug("Gas estimation: {} (with 20% buffer: {})", estimated, buffered);
            return buffered;
        } catch (Exception e) {
            log.warn("Gas estimation failed, using default: {}", e.getMessage());
            return DefaultGasProvider.GAS_LIMIT;
        }
    }

    /**
     * Get current network gas price with 10% buffer for faster confirmation
     * @return Gas price in wei
     */
    private BigInteger getGasPrice() {
        try {
            EthGasPrice ethGasPrice = web3j.ethGasPrice().send();
            BigInteger gasPrice = ethGasPrice.getGasPrice();

            // Add 10% buffer for faster confirmation
            BigInteger buffered = gasPrice.multiply(BigInteger.valueOf(110))
                .divide(BigInteger.valueOf(100));

            log.debug("Gas price: {} wei (with 10% buffer: {} wei)", gasPrice, buffered);
            return buffered;
        } catch (Exception e) {
            log.warn("Failed to get gas price, using default: {}", e.getMessage());
            return DefaultGasProvider.GAS_PRICE;
        }
    }

    /**
     * Mint test tokens to a wallet address
     * Only works with deployed TestToken contracts (test-usdc, test-dai)
     * @param ownerCredentials Credentials of the contract owner
     * @param recipientAddress Address to receive the minted tokens
     * @param amount Amount in token units (e.g., 1000 for 1000 tokens)
     * @param token Token type ("TEST-USDC" or "TEST-DAI")
     * @return Transaction hash
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

    private String getTestContractAddress(String token) {
        return switch (token.toUpperCase()) {
            case "TEST-USDC" -> testUsdcAddress;
            case "TEST-DAI" -> testDaiAddress;
            default -> throw new IllegalArgumentException("Unsupported test token: " + token + ". Use TEST-USDC or TEST-DAI");
        };
    }
}

package co.grtk.stableips.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class ContractService {

    private final Web3j web3j;

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

        try {
            String contractAddress = getContractAddress(token);

            // ETH native transfer (no contract interaction)
            if ("ETH".equalsIgnoreCase(token)) {
                return transferNativeETH(credentials, recipient, amount);
            }

            // ERC-20 token transfer
            BigInteger value = convertToTokenUnits(amount); // Assumes 18 decimals for both USDC and DAI

            Function function = new Function(
                "transfer",
                Arrays.asList(new Address(recipient), new Uint256(value)),
                Collections.emptyList()
            );

            String encodedFunction = FunctionEncoder.encode(function);

            TransactionManager transactionManager = new RawTransactionManager(
                web3j, credentials, chainId
            );

            EthSendTransaction transactionResponse = transactionManager.sendTransaction(
                DefaultGasProvider.GAS_PRICE,
                DefaultGasProvider.GAS_LIMIT,
                contractAddress,
                encodedFunction,
                BigInteger.ZERO
            );

            if (transactionResponse.hasError()) {
                throw new RuntimeException("Transfer failed: " + transactionResponse.getError().getMessage());
            }

            return transactionResponse.getTransactionHash();
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute transfer: " + e.getMessage(), e);
        }
    }

    private String transferNativeETH(Credentials credentials, String recipient, BigDecimal amount) {
        try {
            BigInteger value = convertToTokenUnits(amount);

            TransactionManager transactionManager = new RawTransactionManager(
                web3j, credentials, chainId
            );

            EthSendTransaction transactionResponse = transactionManager.sendTransaction(
                DefaultGasProvider.GAS_PRICE,
                DefaultGasProvider.GAS_LIMIT,
                recipient,
                "",
                value
            );

            if (transactionResponse.hasError()) {
                throw new RuntimeException("ETH transfer failed: " + transactionResponse.getError().getMessage());
            }

            return transactionResponse.getTransactionHash();
        } catch (Exception e) {
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
            return convertFromTokenUnits(balance);
        } catch (Exception e) {
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

    private BigInteger convertToTokenUnits(BigDecimal amount) {
        // Both USDC and DAI use 18 decimals on Sepolia testnet
        BigDecimal multiplier = new BigDecimal("1000000000000000000"); // 10^18
        return amount.multiply(multiplier).toBigInteger();
    }

    private BigDecimal convertFromTokenUnits(BigInteger value) {
        BigDecimal divisor = new BigDecimal("1000000000000000000"); // 10^18
        return new BigDecimal(value).divide(divisor);
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

            BigInteger value = convertToTokenUnits(amount);

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

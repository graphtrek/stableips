# Blockchain Integration Subagent

## Purpose
Specialized agent for handling Web3j integration, smart contract interactions, and Ethereum wallet operations in the StableIPs project.

## Available Tools
- Read
- Edit
- Grep
- Glob
- Bash
- Write

## Use Cases
- Adding blockchain features (wallet generation, balance checking, transfers)
- Debugging transaction issues (pending, failed, or reverted transactions)
- Updating contract addresses (USDC, DAI, custom tokens)
- Implementing new Web3j contract wrappers
- Configuring Infura or other RPC providers
- Adding support for new blockchain networks

## Architecture Context

### Package Structure
```
src/main/java/co/grtk/stableips/blockchain/
├── Web3JConfig.java          # Infura connection, Web3j bean
├── ContractService.java      # USDC/DAI contract interaction
├── WalletGenerator.java      # Wallet creation utilities
└── TransactionMonitor.java   # Transaction status polling
```

### Key Technologies
- **Web3j 4.10.3**: Ethereum Java library
- **Infura API**: Sepolia testnet RPC access
- **ERC20 Tokens**: USDC, DAI on Sepolia testnet

### Configuration Properties
```properties
# Infura API
web3j.infura.url=https://sepolia.infura.io/v3/{YOUR_PROJECT_ID}

# Sepolia testnet
blockchain.network=sepolia
blockchain.chain-id=11155111

# USDC/DAI contract addresses (Sepolia testnet)
contract.usdc.address=0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238
contract.dai.address=0x3e622317f8C93f7328350cF0B56d9eD4C620C5d6
```

## Common Patterns

### Web3j Bean Configuration
```java
@Configuration
public class Web3JConfig {
    @Value("${web3j.infura.url}")
    private String infuraUrl;

    @Bean
    public Web3j web3j() {
        return Web3j.build(new HttpService(infuraUrl));
    }
}
```

### ERC20 Contract Interaction
```java
@Service
public class ContractService {
    private final Web3j web3j;

    public BigInteger getBalance(String address, String contractAddress) {
        ERC20 contract = ERC20.load(
            contractAddress,
            web3j,
            credentials,
            new DefaultGasProvider()
        );
        return contract.balanceOf(address).send();
    }

    public String transfer(String to, BigInteger amount, String contractAddress) {
        ERC20 contract = ERC20.load(contractAddress, web3j, credentials, gasProvider);
        TransactionReceipt receipt = contract.transfer(to, amount).send();
        return receipt.getTransactionHash();
    }
}
```

### Wallet Generation
```java
public class WalletGenerator {
    public static Credentials generateWallet() {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        return Credentials.create(keyPair);
    }

    public static String getAddress(Credentials credentials) {
        return credentials.getAddress();
    }
}
```

### Transaction Monitoring
```java
@Service
public class TransactionMonitor {
    private final Web3j web3j;

    public TransactionReceipt waitForReceipt(String txHash) {
        return new TransactionReceiptProcessor(web3j, 1000, 40)
            .waitForTransactionReceipt(txHash);
    }

    public boolean isTransactionSuccessful(String txHash) {
        Optional<TransactionReceipt> receipt = web3j
            .ethGetTransactionReceipt(txHash)
            .send()
            .getTransactionReceipt();

        return receipt.map(r -> r.getStatus().equals("0x1")).orElse(false);
    }
}
```

## Best Practices

### Security
- **Never commit private keys** to version control
- Store credentials in environment variables or secure vaults
- Use separate wallets for testnet vs mainnet
- Validate addresses before transactions

### Gas Management
```java
public class CustomGasProvider extends DefaultGasProvider {
    @Override
    public BigInteger getGasPrice(String contractFunc) {
        // Get current gas price from network
        return web3j.ethGasPrice().send().getGasPrice();
    }

    @Override
    public BigInteger getGasLimit(String contractFunc) {
        // Set reasonable gas limits per function
        return BigInteger.valueOf(100000);
    }
}
```

### Error Handling
```java
try {
    TransactionReceipt receipt = contract.transfer(to, amount).send();
    if (!receipt.isStatusOK()) {
        throw new TransactionFailedException("Transaction reverted");
    }
} catch (TransactionException e) {
    // Handle timeout, revert, etc.
    log.error("Transaction failed: {}", e.getMessage());
} catch (IOException e) {
    // Handle network issues
    log.error("Network error: {}", e.getMessage());
}
```

### Testing
- Use **Ganache** or **Hardhat** for local blockchain testing
- Mock Web3j responses for unit tests
- Use Sepolia testnet for integration tests
- Never test on mainnet

## Integration Points

### Service Layer
The blockchain package is used by:
- `WalletService`: Wallet generation, balance queries
- `TransactionService`: Initiating transfers, checking status
- `ContractService`: ERC20 token interactions

### Controllers
Controllers should **never call blockchain code directly**:
```java
// ❌ Bad
@Controller
public class WalletController {
    @Autowired private Web3j web3j;  // NO!
}

// ✅ Good
@Controller
public class WalletController {
    @Autowired private WalletService walletService;  // YES!
}
```

## Debugging Tips

### Common Issues
1. **Transaction pending forever**: Check gas price, network congestion
2. **"Insufficient funds"**: Need Sepolia ETH for gas (use faucet)
3. **"Nonce too low"**: Transaction already processed or nonce conflict
4. **Contract call reverts**: Check token balance, allowances, recipient address

### Useful Web3j Commands
```java
// Get current block number
BigInteger blockNumber = web3j.ethBlockNumber().send().getBlockNumber();

// Get transaction by hash
Optional<Transaction> tx = web3j.ethGetTransactionByHash(txHash)
    .send()
    .getTransaction();

// Get ETH balance
BigInteger balance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
    .send()
    .getBalance();

// Estimate gas
BigInteger gasEstimate = web3j.ethEstimateGas(transaction).send().getAmountUsed();
```

## Testing Strategy

### Unit Tests
Mock Web3j and contract interactions:
```java
@Mock private Web3j web3j;
@Mock private ERC20 usdcContract;
@InjectMocks private ContractService contractService;

@Test
void shouldGetBalance() {
    when(usdcContract.balanceOf(anyString()))
        .thenReturn(RemoteFunctionCall.of(() -> BigInteger.valueOf(1000)));

    BigInteger balance = contractService.getUsdcBalance("0x123");
    assertThat(balance).isEqualTo(BigInteger.valueOf(1000));
}
```

### Integration Tests
Use testnet or local blockchain:
```java
@SpringBootTest
class ContractServiceIntegrationTest {
    @Autowired private ContractService contractService;

    @Test
    void shouldTransferUsdc() {
        // Requires Sepolia testnet connection
        String txHash = contractService.transferUsdc(recipient, amount);
        assertThat(txHash).isNotEmpty();
    }
}
```

## Resources
- [Web3j Documentation](https://docs.web3j.io/)
- [Ethereum Sepolia Faucet](https://sepoliafaucet.com/)
- [Infura Dashboard](https://infura.io/dashboard)
- [ERC20 Token Standard](https://eips.ethereum.org/EIPS/eip-20)

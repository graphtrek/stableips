# StableIPs Architecture

> **Demo stablecoin wallet application for Ethereum Sepolia testnet**

**Last Updated**: 2025-10-03
**Status**: Design Document

---

## 📋 Table of Contents
1. [System Overview](#system-overview)
2. [Technology Stack](#technology-stack)
3. [Architecture Diagram](#architecture-diagram)
4. [Component Details](#component-details)
5. [Data Flow](#data-flow)
6. [Security Considerations](#security-considerations)
7. [API Reference](#api-reference)

---

## System Overview

**StableIPs** is a demonstration application for transferring stablecoins (USDC/DAI) on the Ethereum Sepolia testnet. It provides a simple web interface for:

- Creating demo wallet accounts
- Viewing wallet balances
- Initiating stablecoin transfers
- Tracking transaction status

**⚠️ Important**: This is a DEMO application with minimal security. Not suitable for production use.

---

## Technology Stack

### Frontend
- **HTMX 2.x** - Dynamic HTML updates without full page reloads
- **JTE (Java Template Engine)** - Server-side HTML rendering
- **Bootstrap 5.3.8** - UI components and styling
- **jQuery 3.7.x** - DOM manipulation (if needed)
- **Vanilla JavaScript** - Client-side interactions

### Backend
- **Spring Boot 3.5.6** - Application framework
- **Java 21** - Programming language
- **Spring Web** - REST endpoints
- **Spring Data JPA** - Database access
- **Spring Session** - Session management (simple auth)
- **Web3J 4.10.3** - Ethereum blockchain interaction

### Blockchain
- **Ethereum Sepolia Testnet** - Test network
- **Infura API** - Node access provider
- **USDC/DAI Testnet Tokens** - ERC-20 stablecoins

### Database
- **PostgreSQL 16** - Transaction logging
- **H2** (optional) - In-memory development database

### Build & Deploy
- **Gradle 8.x** - Build automation
- **Docker** - Containerization (optional)

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         FRONTEND (Browser)                       │
│  ┌────────────┐  ┌─────────────┐  ┌──────────────────────────┐ │
│  │ Login Page │  │  Dashboard  │  │ Transaction Status Page  │ │
│  └────────────┘  └─────────────┘  └──────────────────────────┘ │
│         │                 │                      │               │
│         └─────────────────┴──────────────────────┘               │
│                          │                                        │
│                     HTMX Requests                                │
└──────────────────────────┼──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                   SPRING BOOT BACKEND                            │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                     Controllers                             │ │
│  │  ┌────────────┐  ┌────────────┐  ┌───────────────────────┐ │ │
│  │  │   Auth     │  │   Wallet   │  │     Transfer          │ │ │
│  │  │ Controller │  │ Controller │  │   Controller          │ │ │
│  │  └────────────┘  └────────────┘  └───────────────────────┘ │ │
│  └─────────┬──────────────┬────────────────┬────────────────────┘ │
│            │              │                │                      │
│            ▼              ▼                ▼                      │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                       Services                              │ │
│  │  ┌────────────┐  ┌────────────┐  ┌───────────────────────┐ │ │
│  │  │   Auth     │  │   Wallet   │  │    Transaction        │ │ │
│  │  │  Service   │  │  Service   │  │     Service           │ │ │
│  │  └────────────┘  └────┬───────┘  └──┬────────────────────┘ │ │
│  └────────┬───────────────┼─────────────┼──────────────────────┘ │
│           │               │             │                        │
│           ▼               ▼             ▼                        │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────────┐ │
│  │ Repositories│  │ Web3J Config │  │  Contract Service       │ │
│  │  (JPA)      │  │   (Infura)   │  │  (USDC/DAI ERC-20)      │ │
│  └─────────────┘  └──────────────┘  └─────────────────────────┘ │
│           │               │                     │                │
└───────────┼───────────────┼─────────────────────┼────────────────┘
            │               │                     │
            ▼               ▼                     ▼
  ┌──────────────┐  ┌─────────────────────────────────────────┐
  │  PostgreSQL  │  │      Ethereum Sepolia Testnet           │
  │   Database   │  │         (via Infura API)                │
  │              │  │  ┌────────────┐  ┌──────────────────┐   │
  │  - Users     │  │  │ USDC Token │  │   DAI Token      │   │
  │  - Txs       │  │  │ (ERC-20)   │  │   (ERC-20)       │   │
  └──────────────┘  │  └────────────┘  └──────────────────┘   │
                    └─────────────────────────────────────────┘
```

---

## Component Details

### 1. Frontend Components

#### Pages (JTE Templates)
- **`login.jte`** - Simple login form (username only)
- **`wallet/dashboard.jte`** - Wallet balance + transfer form
- **`wallet/transaction-status.jte`** - Transaction details

#### HTMX Integration
- Form submissions swap HTML fragments dynamically
- No full page reloads for transfers
- Real-time transaction status updates

#### Example HTMX Form:
```html
<form hx-post="/wallet/transfer"
      hx-target="#tx-status"
      hx-swap="innerHTML">
    <input type="text" name="recipient" placeholder="0x..." required>
    <input type="number" name="amount" step="0.01" required>
    <button type="submit">Send USDC</button>
</form>
<div id="tx-status"></div>
```

---

### 2. Backend Components

#### Controllers
**`AuthController.java`**
```java
@Controller
public class AuthController {

    @PostMapping("/login")
    public String login(@RequestParam String username, HttpSession session) {
        User user = authService.loginOrCreate(username);
        session.setAttribute("userId", user.getId());
        return "redirect:/wallet";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
```

**`WalletController.java`**
```java
@Controller
@RequestMapping("/wallet")
public class WalletController {

    @GetMapping
    public String dashboard(Model model, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        User user = userRepository.findById(userId).orElseThrow();

        BigDecimal balance = walletService.getBalance(user.getWalletAddress());
        model.addAttribute("balance", balance);
        model.addAttribute("user", user);

        return "wallet/dashboard";
    }
}
```

**`TransferController.java`**
```java
@Controller
@RequestMapping("/wallet")
public class TransferController {

    @PostMapping("/transfer")
    @ResponseBody  // Returns HTML fragment
    public String transfer(
        @RequestParam String recipient,
        @RequestParam BigDecimal amount,
        HttpSession session
    ) {
        Long userId = (Long) session.getAttribute("userId");
        Transaction tx = transactionService.initiateTransfer(userId, recipient, amount);

        return String.format(
            "<div class='alert alert-success'>Transfer sent! TX Hash: <a href='https://sepolia.etherscan.io/tx/%s'>%s</a></div>",
            tx.getTxHash(), tx.getTxHash().substring(0, 10) + "..."
        );
    }

    @GetMapping("/tx/{id}")
    public String transactionStatus(@PathVariable Long id, Model model) {
        Transaction tx = transactionRepository.findById(id).orElseThrow();
        model.addAttribute("transaction", tx);
        return "wallet/transaction-status";
    }
}
```

#### Services

**`WalletService.java`**
```java
@Service
public class WalletService {

    @Autowired
    private Web3j web3j;

    @Value("${contract.usdc.address}")
    private String usdcAddress;

    /**
     * Generate new Ethereum wallet (MetaMask compatible)
     */
    public Credentials createWallet() {
        try {
            ECKeyPair keyPair = Keys.createEcKeyPair();
            return Credentials.create(keyPair);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create wallet", e);
        }
    }

    /**
     * Get USDC balance for address
     */
    public BigDecimal getBalance(String address) {
        try {
            // Load USDC ERC-20 contract
            Function function = new Function(
                "balanceOf",
                Arrays.asList(new Address(address)),
                Arrays.asList(new TypeReference<Uint256>() {})
            );

            String encodedFunction = FunctionEncoder.encode(function);
            EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(address, usdcAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
            ).send();

            BigInteger balance = (BigInteger) FunctionReturnDecoder.decode(
                response.getValue(),
                function.getOutputParameters()
            ).get(0).getValue();

            // USDC has 6 decimals
            return new BigDecimal(balance).divide(BigDecimal.TEN.pow(6));
        } catch (Exception e) {
            throw new RuntimeException("Failed to get balance", e);
        }
    }
}
```

**`TransactionService.java`**
```java
@Service
@Transactional
public class TransactionService {

    @Autowired
    private WalletService walletService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private Web3j web3j;

    @Value("${contract.usdc.address}")
    private String usdcAddress;

    /**
     * Initiate USDC transfer
     */
    public Transaction initiateTransfer(Long userId, String recipient, BigDecimal amount) {
        User user = userRepository.findById(userId).orElseThrow();

        try {
            // Load user's credentials (in real app, use secure key storage)
            Credentials credentials = Credentials.create(user.getPrivateKey());

            // Create transfer function (ERC-20 transfer)
            BigInteger amountInWei = amount.multiply(BigDecimal.TEN.pow(6)).toBigInteger();
            Function function = new Function(
                "transfer",
                Arrays.asList(new Address(recipient), new Uint256(amountInWei)),
                Collections.emptyList()
            );

            String encodedFunction = FunctionEncoder.encode(function);

            // Send transaction
            EthGetTransactionCount txCount = web3j.ethGetTransactionCount(
                user.getWalletAddress(),
                DefaultBlockParameterName.LATEST
            ).send();

            RawTransaction rawTransaction = RawTransaction.createTransaction(
                txCount.getTransactionCount(),
                DefaultGasProvider.GAS_PRICE,
                DefaultGasProvider.GAS_LIMIT,
                usdcAddress,
                encodedFunction
            );

            byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
            String hexValue = Numeric.toHexString(signedMessage);

            EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();
            String txHash = ethSendTransaction.getTransactionHash();

            // Log transaction
            Transaction tx = new Transaction();
            tx.setUserId(userId);
            tx.setRecipient(recipient);
            tx.setAmount(amount);
            tx.setTxHash(txHash);
            tx.setStatus("PENDING");
            tx.setTimestamp(Instant.now());

            return transactionRepository.save(tx);

        } catch (Exception e) {
            throw new RuntimeException("Transfer failed", e);
        }
    }
}
```

---

### 3. Database Schema

**Users Table**
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    wallet_address VARCHAR(42) NOT NULL,
    private_key VARCHAR(66) NOT NULL,  -- WARNING: Insecure, demo only!
    created_at TIMESTAMP DEFAULT NOW()
);
```

**Transactions Table**
```sql
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    recipient VARCHAR(42) NOT NULL,
    amount DECIMAL(18, 6) NOT NULL,
    tx_hash VARCHAR(66) NOT NULL,
    status VARCHAR(20) NOT NULL,  -- PENDING, CONFIRMED, FAILED
    timestamp TIMESTAMP DEFAULT NOW()
);
```

---

## Data Flow

### 1. User Login Flow
```
User enters username
    ↓
POST /login
    ↓
AuthService checks if user exists
    ↓
If not exists: create new wallet (Web3J)
    ↓
Save user to database
    ↓
Store userId in session
    ↓
Redirect to /wallet
```

### 2. Transfer Flow
```
User fills transfer form (recipient, amount)
    ↓
HTMX POST /wallet/transfer
    ↓
TransactionService validates inputs
    ↓
Load user's wallet credentials
    ↓
Create ERC-20 transfer transaction (Web3J)
    ↓
Sign transaction with private key
    ↓
Send to Sepolia via Infura
    ↓
Receive transaction hash
    ↓
Log transaction to database (PENDING)
    ↓
Return HTML fragment with TX hash
    ↓
HTMX swaps into #tx-status div
    ↓
User sees confirmation + Etherscan link
```

### 3. Balance Check Flow
```
User loads /wallet dashboard
    ↓
WalletService.getBalance(walletAddress)
    ↓
Call USDC contract balanceOf(address) via Web3J
    ↓
Query Sepolia via Infura
    ↓
Decode response (BigInteger)
    ↓
Convert from wei to USDC (6 decimals)
    ↓
Return balance to template
    ↓
Display in UI
```

---

## Security Considerations

### ⚠️ DEMO LIMITATIONS - DO NOT USE IN PRODUCTION

1. **No Password Hashing**: Usernames are not protected
2. **Private Keys in Database**: HIGHLY INSECURE - store in plaintext
3. **No Encryption**: All data unencrypted
4. **Session Only Auth**: No refresh tokens or JWT
5. **No Input Validation**: Minimal validation on transfers
6. **No Rate Limiting**: Vulnerable to abuse
7. **No 2FA**: Single-factor auth only
8. **No KYC**: Anyone can create accounts

### Recommended for Production
- Use hardware wallets (Ledger, Trezor) or key management services (AWS KMS)
- Implement OAuth2/OIDC for auth
- Add 2FA (TOTP, SMS)
- Encrypt private keys at rest
- Use HTTPS only
- Add rate limiting
- Implement proper input validation
- Add transaction confirmation (email/SMS)

---

## API Reference

### Authentication

#### `POST /login`
**Description**: Login or create new user

**Request**:
```http
POST /login
Content-Type: application/x-www-form-urlencoded

username=alice
```

**Response**: Redirect to `/wallet`

---

### Wallet Operations

#### `GET /wallet`
**Description**: Get wallet dashboard

**Response**: HTML page with balance and transfer form

---

#### `POST /wallet/transfer`
**Description**: Initiate stablecoin transfer

**Request**:
```http
POST /wallet/transfer
Content-Type: application/x-www-form-urlencoded

recipient=0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb0
amount=10.5
```

**Response**: HTML fragment
```html
<div class='alert alert-success'>
    Transfer sent! TX Hash: <a href='...'>0x123abc...</a>
</div>
```

---

#### `GET /tx/{id}`
**Description**: Get transaction status

**Response**: HTML page with transaction details

---

### Health Check

#### `GET /actuator/health`
**Description**: Application health status

**Response**:
```json
{
  "status": "UP"
}
```

---

## Configuration Reference

### `application.properties`
```properties
# Server
server.port=8080

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/stableips
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update

# JTE
gg.jte.development-mode=true

# Web3J / Infura
web3j.infura.url=https://sepolia.infura.io/v3/{YOUR_PROJECT_ID}

# Blockchain
blockchain.network=sepolia
blockchain.chain-id=11155111

# Contracts (Sepolia testnet)
contract.usdc.address=0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238
contract.dai.address=0x3e622317f8C93f7328350cF0B56d9eD4C620C5d6

# Session
server.servlet.session.timeout=30m
```

---

## Development Roadmap

### Phase 1: Foundation ✅
- [x] Project setup
- [x] Documentation

### Phase 2: Core Features (Next)
- [ ] User model and repository
- [ ] Transaction model and repository
- [ ] Web3J configuration
- [ ] Wallet service (create, balance)
- [ ] Auth controller (login/logout)
- [ ] Wallet controller (dashboard)

### Phase 3: Transfer Logic
- [ ] Transfer service
- [ ] Transfer controller
- [ ] Transaction status endpoint
- [ ] Error handling

### Phase 4: Frontend
- [ ] Login page (JTE)
- [ ] Dashboard page (JTE + HTMX)
- [ ] Transaction status page
- [ ] Bootstrap styling

### Phase 5: Testing & Polish
- [ ] Unit tests (services)
- [ ] Integration tests (controllers)
- [ ] Manual testing on Sepolia
- [ ] Documentation updates

---

## References

- [Web3J Documentation](https://docs.web3j.io/)
- [Infura Sepolia API](https://docs.infura.io/networks/ethereum/how-to/choose-a-network)
- [HTMX Documentation](https://htmx.org/docs/)
- [ERC-20 Token Standard](https://ethereum.org/en/developers/docs/standards/tokens/erc-20/)
- [Sepolia Testnet Faucet](https://sepoliafaucet.com/)
- [Etherscan Sepolia](https://sepolia.etherscan.io/)

# Spring Service Layer Subagent

## Purpose
Specialized agent for implementing service classes following StableIPs architecture: thin controllers, testable services with clear separation of concerns.

## Available Tools
- Read
- Edit
- Grep
- Glob

## Use Cases
- Creating new service classes with business logic
- Refactoring existing services for better testability
- Implementing transaction management
- Adding validation and error handling
- Integrating multiple repositories and external services
- Converting controller logic to service layer

## Architecture Context

### Package Structure
```
src/main/java/co/grtk/stableips/
├── service/
│   ├── AuthService.java          # Session management, login/logout
│   ├── UserService.java          # User CRUD operations
│   ├── WalletService.java        # Wallet operations (uses blockchain package)
│   ├── TransactionService.java   # Transfer logic, transaction logging
│   └── NotificationService.java  # Email/SMS notifications (future)
├── controller/
│   ├── AuthController.java       # Thin - delegates to AuthService
│   ├── WalletController.java     # Thin - delegates to WalletService
│   └── TransferController.java   # Thin - delegates to TransactionService
├── repository/
│   ├── UserRepository.java
│   └── TransactionRepository.java
└── blockchain/
    ├── ContractService.java      # Called by WalletService/TransactionService
    └── WalletGenerator.java
```

### Key Principles
1. **Thin Controllers**: Controllers handle HTTP concerns only (requests, responses, sessions)
2. **Fat Services**: Business logic, validation, orchestration lives in services
3. **Single Responsibility**: Each service has one clear purpose
4. **Transactional Boundaries**: Use `@Transactional` at service method level
5. **Testability**: Services should be unit-testable with mocked dependencies

## Service Patterns

### Basic Service Structure
```java
package co.grtk.stableips.service;

import co.grtk.stableips.model.User;
import co.grtk.stableips.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    public User createUser(String username, String email) {
        log.info("Creating user: {}", username);

        // Validation
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        // Business logic
        User user = User.builder()
            .username(username)
            .email(email)
            .active(true)
            .build();

        // Persistence
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public User getUserOrThrow(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    }
}
```

### Service with Multiple Dependencies
```java
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final UserRepository userRepository;
    private final ContractService contractService;  // Blockchain integration
    private final WalletGenerator walletGenerator;

    public WalletDto getWallet(Long userId) {
        User user = getUserOrThrow(userId);

        String address = user.getWalletAddress();
        if (address == null) {
            throw new IllegalStateException("User has no wallet");
        }

        // Fetch balances from blockchain
        BigDecimal usdcBalance = contractService.getUsdcBalance(address);
        BigDecimal daiBalance = contractService.getDaiBalance(address);
        BigDecimal ethBalance = contractService.getEthBalance(address);

        return WalletDto.builder()
            .address(address)
            .usdcBalance(usdcBalance)
            .daiBalance(daiBalance)
            .ethBalance(ethBalance)
            .build();
    }

    public User generateWallet(Long userId) {
        log.info("Generating wallet for user: {}", userId);

        User user = getUserOrThrow(userId);
        if (user.getWalletAddress() != null) {
            throw new IllegalStateException("User already has a wallet");
        }

        // Generate new wallet
        Credentials credentials = walletGenerator.generateWallet();
        String address = credentials.getAddress();

        // Update user
        user.setWalletAddress(address);
        user.setPrivateKey(encryptPrivateKey(credentials.getEcKeyPair().getPrivateKey()));

        return userRepository.save(user);
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
    }

    private String encryptPrivateKey(BigInteger privateKey) {
        // TODO: Implement encryption (DO NOT store plain text!)
        return privateKey.toString(16);
    }
}
```

### Complex Service (Transaction Orchestration)
```java
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final ContractService contractService;
    private final NotificationService notificationService;

    public Transaction initiateTransfer(Long userId, TransferDto dto) {
        log.info("Initiating transfer: {} {} to {}", dto.getAmount(), dto.getToken(), dto.getRecipient());

        // 1. Validate sender
        User sender = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("Sender not found"));

        if (sender.getWalletAddress() == null) {
            throw new IllegalStateException("Sender has no wallet");
        }

        // 2. Validate recipient address
        if (!isValidEthereumAddress(dto.getRecipient())) {
            throw new IllegalArgumentException("Invalid recipient address");
        }

        // 3. Check balance
        BigDecimal balance = getTokenBalance(sender.getWalletAddress(), dto.getToken());
        if (balance.compareTo(dto.getAmount()) < 0) {
            throw new InsufficientFundsException("Insufficient balance");
        }

        // 4. Create transaction record (PENDING)
        Transaction tx = Transaction.builder()
            .userId(userId)
            .recipient(dto.getRecipient())
            .amount(dto.getAmount())
            .token(dto.getToken())
            .status(TransactionStatus.PENDING)
            .createdAt(Instant.now())
            .build();

        tx = transactionRepository.save(tx);

        // 5. Execute blockchain transfer (may take time)
        try {
            String txHash = executeTransfer(sender, dto);
            tx.setTxHash(txHash);
            tx.setStatus(TransactionStatus.SUBMITTED);
            tx = transactionRepository.save(tx);

            // 6. Notify user (async)
            notificationService.sendTransferNotification(sender, tx);

        } catch (Exception e) {
            log.error("Transfer failed: {}", e.getMessage(), e);
            tx.setStatus(TransactionStatus.FAILED);
            tx.setErrorMessage(e.getMessage());
            tx = transactionRepository.save(tx);
            throw new TransferFailedException("Transfer failed", e);
        }

        return tx;
    }

    @Transactional(readOnly = true)
    public TransactionStatus checkStatus(Long transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new TransactionNotFoundException("Transaction not found"));

        // If still pending, check blockchain status
        if (tx.getStatus() == TransactionStatus.SUBMITTED && tx.getTxHash() != null) {
            boolean confirmed = contractService.isTransactionConfirmed(tx.getTxHash());
            if (confirmed) {
                tx.setStatus(TransactionStatus.CONFIRMED);
                tx.setConfirmedAt(Instant.now());
                transactionRepository.save(tx);
            }
        }

        return tx.getStatus();
    }

    private BigDecimal getTokenBalance(String address, String token) {
        return switch (token.toUpperCase()) {
            case "USDC" -> contractService.getUsdcBalance(address);
            case "DAI" -> contractService.getDaiBalance(address);
            default -> throw new IllegalArgumentException("Unsupported token: " + token);
        };
    }

    private String executeTransfer(User sender, TransferDto dto) {
        return switch (dto.getToken().toUpperCase()) {
            case "USDC" -> contractService.transferUsdc(
                sender.getWalletAddress(),
                sender.getDecryptedPrivateKey(),
                dto.getRecipient(),
                dto.getAmount()
            );
            case "DAI" -> contractService.transferDai(
                sender.getWalletAddress(),
                sender.getDecryptedPrivateKey(),
                dto.getRecipient(),
                dto.getAmount()
            );
            default -> throw new IllegalArgumentException("Unsupported token");
        };
    }

    private boolean isValidEthereumAddress(String address) {
        return address != null && address.matches("^0x[a-fA-F0-9]{40}$");
    }
}
```

### Authentication Service
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;

    /**
     * Simple demo authentication (no password).
     * In production: use Spring Security with proper password hashing.
     */
    public User login(String username) {
        log.info("Login attempt: {}", username);

        return userRepository.findByUsername(username)
            .filter(User::isActive)
            .orElseThrow(() -> new AuthenticationException("User not found or inactive"));
    }

    public void logout(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId != null) {
            log.info("User logged out: {}", userId);
            session.invalidate();
        }
    }

    public User getCurrentUser(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            throw new AuthenticationException("Not authenticated");
        }

        return userRepository.findById(userId)
            .orElseThrow(() -> new AuthenticationException("User not found"));
    }

    public boolean isAuthenticated(HttpSession session) {
        return session.getAttribute("userId") != null;
    }
}
```

## Transaction Management

### Read-Only Transactions
```java
@Transactional(readOnly = true)
public List<Transaction> getUserTransactions(Long userId) {
    return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
}
```

### Propagation Types
```java
// REQUIRED (default): Use existing or create new
@Transactional(propagation = Propagation.REQUIRED)
public void transferWithLogging(TransferDto dto) {
    Transaction tx = initiateTransfer(dto);  // Joins this transaction
    logTransfer(tx);  // Joins this transaction
}

// REQUIRES_NEW: Always create new transaction (independent)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void auditLog(String action) {
    // This commits independently, even if parent transaction rolls back
    auditRepository.save(new AuditLog(action));
}

// NOT_SUPPORTED: Execute without transaction
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public void sendEmail(String to, String message) {
    // External API call, no transaction needed
    emailClient.send(to, message);
}
```

### Isolation Levels
```java
// Prevent dirty reads
@Transactional(isolation = Isolation.READ_COMMITTED)
public BigDecimal getBalance(Long userId) {
    // ...
}

// Prevent phantom reads (for financial operations)
@Transactional(isolation = Isolation.SERIALIZABLE)
public void processPayment(Long userId, BigDecimal amount) {
    // Critical section - full isolation
}
```

### Rollback Rules
```java
// Rollback on checked exceptions too
@Transactional(rollbackFor = Exception.class)
public void riskyOperation() throws Exception {
    // ...
}

// Don't rollback on specific exceptions
@Transactional(noRollbackFor = ValidationException.class)
public void validateAndSave(User user) throws ValidationException {
    // Validation errors don't rollback transaction
}
```

## Validation Patterns

### Bean Validation
```java
@Service
@Validated  // Enable @Valid on method parameters
@RequiredArgsConstructor
public class UserService {

    public User createUser(@Valid CreateUserDto dto) {
        // dto is automatically validated
        // ...
    }
}
```

### Custom Validation
```java
public void validateTransfer(TransferDto dto) {
    List<String> errors = new ArrayList<>();

    if (dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
        errors.add("Amount must be positive");
    }

    if (dto.getAmount().scale() > 2) {
        errors.add("Amount cannot have more than 2 decimal places");
    }

    if (!isValidEthereumAddress(dto.getRecipient())) {
        errors.add("Invalid Ethereum address");
    }

    if (!errors.isEmpty()) {
        throw new ValidationException("Validation failed", errors);
    }
}
```

## Error Handling

### Custom Exceptions
```java
// Domain exceptions
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}

public class TransferFailedException extends RuntimeException {
    public TransferFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### Exception Handling in Services
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    public WalletDto getWallet(Long userId) {
        try {
            User user = getUserOrThrow(userId);
            return fetchWalletFromBlockchain(user.getWalletAddress());
        } catch (IOException e) {
            log.error("Blockchain connection error", e);
            throw new BlockchainConnectionException("Unable to fetch wallet data", e);
        } catch (Exception e) {
            log.error("Unexpected error fetching wallet", e);
            throw new ServiceException("Failed to get wallet", e);
        }
    }
}
```

## Testing Services

### Unit Tests (Mockito)
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldCreateUser() {
        // Given
        CreateUserDto dto = new CreateUserDto("john", "john@example.com");
        User savedUser = User.builder()
            .id(1L)
            .username("john")
            .email("john@example.com")
            .build();

        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // When
        User result = userService.createUser(dto);

        // Then
        assertThat(result.getUsername()).isEqualTo("john");
        verify(userRepository).existsByUsername("john");
        verify(userRepository).save(argThat(user ->
            user.getUsername().equals("john") &&
            user.getEmail().equals("john@example.com")
        ));
    }

    @Test
    void shouldThrowWhenUsernameExists() {
        // Given
        when(userRepository.existsByUsername("john")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> userService.createUser(new CreateUserDto("john", "john@example.com")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }
}
```

### Integration Tests (Spring Boot Test)
```java
@SpringBootTest
@Transactional
class TransactionServiceIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @MockBean
    private ContractService contractService;  // Mock blockchain calls

    @Test
    void shouldInitiateTransfer() {
        // Given
        User sender = userRepository.save(User.builder()
            .username("alice")
            .walletAddress("0xABC...")
            .build());

        when(contractService.getUsdcBalance(anyString()))
            .thenReturn(new BigDecimal("100.00"));
        when(contractService.transferUsdc(any(), any(), any(), any()))
            .thenReturn("0xTxHash123");

        TransferDto dto = TransferDto.builder()
            .recipient("0xDEF...")
            .amount(new BigDecimal("10.00"))
            .token("USDC")
            .build();

        // When
        Transaction tx = transactionService.initiateTransfer(sender.getId(), dto);

        // Then
        assertThat(tx.getTxHash()).isEqualTo("0xTxHash123");
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUBMITTED);

        // Verify persisted
        Transaction savedTx = transactionRepository.findById(tx.getId()).orElseThrow();
        assertThat(savedTx.getTxHash()).isEqualTo("0xTxHash123");
    }
}
```

## Best Practices

### 1. Dependency Injection
```java
// ✅ Constructor injection (recommended)
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
}

// ❌ Field injection (avoid)
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;  // Hard to test
}
```

### 2. Separation of Concerns
```java
// ✅ Service handles business logic
@Service
public class WalletService {
    public WalletDto getWallet(Long userId) {
        User user = getUserOrThrow(userId);
        return buildWalletDto(user);
    }
}

// ❌ Controller handles business logic
@Controller
public class WalletController {
    public String getWallet(Long userId, Model model) {
        User user = userRepository.findById(userId).orElseThrow();
        WalletDto dto = new WalletDto(/* ... */);  // NO!
        model.addAttribute("wallet", dto);
        return "wallet";
    }
}
```

### 3. Return DTOs, Not Entities
```java
// ✅ Return DTO
public WalletDto getWallet(Long userId) {
    User user = getUserOrThrow(userId);
    return WalletDto.from(user);
}

// ❌ Return entity (exposes internal structure)
public User getUser(Long userId) {
    return userRepository.findById(userId).orElseThrow();
}
```

### 4. Logging
```java
@Slf4j
@Service
public class TransactionService {
    public Transaction transfer(TransferDto dto) {
        log.info("Transfer request: {} {} to {}", dto.getAmount(), dto.getToken(), dto.getRecipient());
        try {
            Transaction tx = executeTransfer(dto);
            log.info("Transfer successful: txHash={}", tx.getTxHash());
            return tx;
        } catch (Exception e) {
            log.error("Transfer failed: {}", e.getMessage(), e);
            throw new TransferFailedException("Transfer failed", e);
        }
    }
}
```

## Resources
- [Spring Service Layer Best Practices](https://www.baeldung.com/spring-service-layer)
- [Spring Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- [Testing with Mockito](https://www.baeldung.com/mockito-annotations)

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**StableIPs** is a demo stablecoin wallet application for transferring USDC/DAI on Ethereum Sepolia testnet.

This is a Spring Boot 3.5.6 application using Java 21 and Gradle. The project uses:
- **JTE (Java Template Engine)** + **HTMX** for dynamic server-side rendering
- **Spring Data JPA** with PostgreSQL for transaction logging
- **Spring Web** for REST/MVC endpoints
- **Web3J** for Ethereum blockchain interaction
- **Infura API** for Sepolia testnet access

Package structure: `co.grtk.stableips`

### Application Purpose (DEMO)
- **Wallet Management**: Generate demo Ethereum wallets (MetaMask compatible)
- **Stablecoin Transfers**: Send USDC/DAI on Sepolia testnet
- **Transaction Tracking**: Log and query transaction status
- **Simple Auth**: Session-based authentication (no KYC, demo only)

## Architecture Guidelines

When adding new features, follow this structure:

```
src/main/java/co/grtk/stableips/
├── config/           # Spring configuration (Web3J, Security, etc.)
├── controller/       # REST + HTMX controllers (return HTML fragments for HTMX)
│   ├── AuthController.java       # Login/logout
│   ├── WalletController.java     # Wallet balance, generation
│   └── TransferController.java   # Transfer initiation, status
├── service/          # Business logic
│   ├── AuthService.java          # Session management
│   ├── WalletService.java        # Web3J wallet operations
│   └── TransactionService.java   # Transfer logic, logging
├── repository/       # Data access (Spring Data JPA)
│   ├── UserRepository.java       # User accounts
│   └── TransactionRepository.java # Transaction history
├── model/            # Domain entities and DTOs
│   ├── User.java                 # User entity
│   ├── Transaction.java          # Transaction log entity
│   └── dto/                      # Transfer requests, responses
├── blockchain/       # Web3J integration
│   ├── Web3JConfig.java          # Infura connection
│   └── ContractService.java      # USDC/DAI contract interaction
└── Application.java
```

**Key Principles**:
- Controllers should be thin - delegate business logic to services
- Use DTOs for API requests/responses, keep entities for persistence
- Services contain business logic and should be easily testable
- **HTMX endpoints**: Return HTML fragments for dynamic updates (not JSON)
- **Blockchain**: Isolate Web3J logic in `blockchain` package
- **Security**: Session-based auth (demo only, no password hashing)

## Build and Development Commands

### Quick Start
```bash
./gradlew bootRun  # Run application (port 8080)
./gradlew test     # Run tests
./gradlew build    # Full build with tests
```

### Development
```bash
# Run with development profile
./gradlew bootRun --args='--spring.profiles.active=dev'

# Hot reload (if spring-boot-devtools added)
./gradlew bootRun

# Clean and rebuild
./gradlew clean build
```

### Testing
```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests "co.grtk.stableips.StableipsApplicationTests"

# Specific test method
./gradlew test --tests "UserServiceTest.shouldCreateUser"

# Integration tests (if separated with @Tag("integration"))
./gradlew test --tests "*IntegrationTest"

# Quick unit tests only (exclude integration)
./gradlew test --tests "*Service*Test" --tests "*Util*Test"
```

### Code Quality (when configured)
```bash
# Format code
./gradlew spotlessApply

# Check formatting
./gradlew spotlessCheck

# Run linters (Checkstyle, PMD, SpotBugs)
./gradlew check

# Generate test coverage report
./gradlew jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html

# Security scan
./gradlew dependencyCheckAnalyze
```

## JTE Template Configuration

- Templates are located in `src/main/jte/`
- Currently configured for **development mode** (`gg.jte.development-mode=true` in `application.properties`)
- For production: remove `gg.jte.development-mode=true` and set `gg.jte.use-precompiled-templates=true`
- JTE templates are precompiled during Gradle build via the `gg.jte.gradle` plugin

**HTMX Integration Pattern** (StableIPs specific):
```java
@Controller
@RequestMapping("/wallet")
public class WalletController {

    @GetMapping
    public String walletPage(Model model, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        model.addAttribute("balance", walletService.getBalance(userId));
        return "wallet/dashboard";  // Full page with HTMX
    }

    @PostMapping("/transfer")
    @ResponseBody  // Returns HTML fragment
    public String initiateTransfer(@ModelAttribute TransferDto dto, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        Transaction tx = transactionService.transfer(userId, dto.getRecipient(), dto.getAmount());

        // Return HTML fragment for HTMX to swap (e.g., transaction status)
        return String.format(
            "<div class='alert alert-success'>Transfer initiated! TX: %s</div>",
            tx.getTxHash()
        );
    }
}
```

**Pages to implement**:
- `login.jte` - Login form
- `wallet/dashboard.jte` - Balance display + transfer form (with HTMX)
- `wallet/transaction-status.jte` - Transaction details (fragment)

## API Endpoints

| Method | URL | Description | Returns |
|--------|-----|-------------|---------|
| `POST` | `/login` | User login (simple name only) | HTML page or redirect |
| `GET` | `/wallet` | Wallet dashboard (balance + transfer form) | HTML page |
| `POST` | `/wallet/transfer` | Initiate stablecoin transfer | HTML fragment (status) |
| `GET` | `/tx/{id}` | Transaction status | HTML fragment or page |
| `POST` | `/logout` | End session | Redirect |

## Database

- Uses PostgreSQL for transaction logging
- JPA entities in `co.grtk.stableips.model` package
- Repositories in `co.grtk.stableips.repository` package

**Entities**:
- `User` - User accounts (id, username, walletAddress, createdAt)
- `Transaction` - Transaction logs (id, userId, recipient, amount, txHash, status, timestamp)

**Connection configuration** (`application.properties`):
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/stableips
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update  # Auto-create tables (demo only)
```

## Blockchain Configuration

**Required dependencies** (add to `build.gradle`):
```gradle
dependencies {
    implementation 'org.web3j:core:4.10.3'
    // ... existing dependencies
}
```

**Configuration** (`application.properties`):
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

**Web3J Setup**:
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

## Testing Strategy

### Test Coverage Targets
- **Services**: 85%+ (core business logic)
- **Controllers**: 70%+ (endpoints and error handling)
- **Repositories**: 60%+ (custom queries only, Spring Data methods excluded)
- **Overall**: 80%+

### Test Organization
```
src/test/java/co/grtk/stableips/
├── controller/     # Controller tests (MockMvc)
├── service/        # Service unit tests (Mockito)
├── repository/     # Repository integration tests (Testcontainers)
└── integration/    # Full integration tests
```

### Example Patterns

**Unit Test** (Service):
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock private UserRepository userRepository;
    @InjectMocks private UserService userService;

    @Test
    void shouldCreateUser() {
        // Given
        User user = new User("john@example.com");
        when(userRepository.save(any())).thenReturn(user);

        // When
        User result = userService.create(user);

        // Then
        assertThat(result.getEmail()).isEqualTo("john@example.com");
        verify(userRepository).save(user);
    }
}
```

**Integration Test** (Controller):
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserControllerIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldCreateUser() {
        UserDto dto = new UserDto("john@example.com");
        ResponseEntity<UserDto> response = restTemplate
            .postForEntity("/api/users", dto, UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
```

## Common Development Patterns

### Controller Pattern (REST + HTMX)
```java
@Controller
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        return "users/list";
    }

    @PostMapping
    @ResponseBody  // Return HTML fragment for HTMX
    public String create(@Valid @ModelAttribute UserDto dto) {
        User user = userService.create(dto);
        return "<tr>...</tr>";  // Or use JTE partial
    }
}
```

### Service Pattern
```java
@Service
@Transactional
public class UserService {
    private final UserRepository userRepository;

    public User create(UserDto dto) {
        // Validation, business logic
        User user = new User(dto.email());
        return userRepository.save(user);
    }
}
```

### Repository Pattern
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.active = true")
    List<User> findActiveUsers();
}
```

## Quality Gates (Recommended Workflow)

Before pushing code or creating PRs:

1. **Format code**: `./gradlew spotlessApply` (if configured)
2. **Run tests**: `./gradlew test`
3. **Check coverage**: `./gradlew jacocoTestReport` (if configured)
4. **Security scan**: `./gradlew dependencyCheckAnalyze` (if configured)

## Docker (When Needed)

**Run with Docker Compose**:
```bash
# If docker-compose.yml exists
docker-compose up

# Build and run
docker build -t stableips:latest .
docker run -p 8080:8080 stableips:latest
```

## Upgrade Path to Java 24

When ready to upgrade from Java 21 → Java 24:
1. Update `build.gradle`: `toolchain.languageVersion = JavaLanguageVersion.of(24)`
2. Update Spring Boot to latest 3.x compatible version
3. Test thoroughly - especially JTE and JPA compatibility
4. Update CI/CD to use Java 24

## Specialized Domain Guides

This project includes specialized guides in `/subagents/` that provide comprehensive patterns and best practices for different architectural concerns. **IMPORTANT: Consult the relevant guide before working on tasks in these domains.**

### When to Use Each Guide

| Guide | Use When | Purpose |
|-------|----------|---------|
| **blockchain-integration.md** | Working with Web3j, smart contracts, wallet operations, or any blockchain-related code | Ethereum integration patterns, contract interactions, transaction monitoring |
| **jte-htmx-ui.md** | Creating/modifying JTE templates, implementing HTMX features, or building UI components | Server-side rendering patterns, HTMX integration, template best practices |
| **spring-service-layer.md** | Implementing services, business logic, validation, or transaction management | Service patterns, dependency injection, transaction boundaries, error handling |
| **test-coverage.md** | Writing tests, improving coverage, or fixing test failures | Unit/integration testing patterns, MockMvc, Testcontainers, coverage targets |
| **database-migration.md** | Creating entities, repositories, custom queries, or schema changes | JPA patterns, repository queries, indexing, Flyway migrations |

### Workflow

Before implementing features in any of these domains:
1. **Read the relevant guide** from `/subagents/`
2. **Follow the patterns** and examples provided
3. **Apply best practices** outlined in the guide
4. **Reference the guide** when asking for help with domain-specific tasks

Example: When adding a new ERC20 token transfer feature:
- Consult `/subagents/blockchain-integration.md` for Web3j patterns
- Consult `/subagents/spring-service-layer.md` for service implementation
- Consult `/subagents/test-coverage.md` for testing the feature
- Consult `/subagents/database-migration.md` if adding transaction entities

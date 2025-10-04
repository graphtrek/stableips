# Test Coverage Subagent

## Purpose
Specialized agent for writing comprehensive tests following StableIPs coverage targets: 85%+ services, 70%+ controllers, 80%+ overall.

## Available Tools
- Read
- Edit
- Write
- Bash
- Grep
- Glob

## Use Cases
- Writing unit tests for services with Mockito
- Creating integration tests for controllers with MockMvc
- Implementing repository tests with Testcontainers
- Improving test coverage for existing code
- Fixing failing tests
- Adding test cases for edge cases and error scenarios
- Setting up test fixtures and data builders

## Coverage Targets

| Component | Target | Focus |
|-----------|--------|-------|
| Services | 85%+ | Core business logic, validation, orchestration |
| Controllers | 70%+ | Endpoints, error handling, HTTP responses |
| Repositories | 60%+ | Custom queries only (Spring Data methods excluded) |
| Overall | 80%+ | Full application coverage |

## Test Organization

```
src/test/java/co/grtk/stableips/
├── service/
│   ├── UserServiceTest.java
│   ├── WalletServiceTest.java
│   └── TransactionServiceTest.java
├── controller/
│   ├── AuthControllerTest.java
│   ├── WalletControllerTest.java
│   └── TransferControllerTest.java
├── repository/
│   ├── UserRepositoryTest.java
│   └── TransactionRepositoryTest.java
├── blockchain/
│   ├── ContractServiceTest.java
│   └── WalletGeneratorTest.java
├── integration/
│   ├── TransferIntegrationTest.java
│   └── WalletIntegrationTest.java
└── util/
    ├── TestDataBuilder.java
    └── MockWeb3jUtils.java
```

## Test Patterns

### Service Unit Test (Mockito)
```java
package co.grtk.stableips.service;

import co.grtk.stableips.blockchain.ContractService;
import co.grtk.stableips.model.User;
import co.grtk.stableips.model.dto.WalletDto;
import co.grtk.stableips.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContractService contractService;

    @InjectMocks
    private WalletService walletService;

    @Test
    void shouldGetWalletWithBalances() {
        // Given
        User user = User.builder()
            .id(1L)
            .username("alice")
            .walletAddress("0xABC123")
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(contractService.getUsdcBalance("0xABC123"))
            .thenReturn(new BigDecimal("100.50"));
        when(contractService.getDaiBalance("0xABC123"))
            .thenReturn(new BigDecimal("50.25"));
        when(contractService.getEthBalance("0xABC123"))
            .thenReturn(new BigDecimal("0.5"));

        // When
        WalletDto wallet = walletService.getWallet(1L);

        // Then
        assertThat(wallet.getAddress()).isEqualTo("0xABC123");
        assertThat(wallet.getUsdcBalance()).isEqualByComparingTo("100.50");
        assertThat(wallet.getDaiBalance()).isEqualByComparingTo("50.25");
        assertThat(wallet.getEthBalance()).isEqualByComparingTo("0.5");

        verify(userRepository).findById(1L);
        verify(contractService).getUsdcBalance("0xABC123");
        verify(contractService).getDaiBalance("0xABC123");
        verify(contractService).getEthBalance("0xABC123");
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> walletService.getWallet(999L))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessageContaining("User not found: 999");
    }

    @Test
    void shouldThrowWhenUserHasNoWallet() {
        // Given
        User user = User.builder()
            .id(1L)
            .username("alice")
            .walletAddress(null)  // No wallet
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // When/Then
        assertThatThrownBy(() -> walletService.getWallet(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("has no wallet");
    }

    @Test
    void shouldGenerateWallet() {
        // Given
        User user = User.builder()
            .id(1L)
            .username("bob")
            .walletAddress(null)
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // When
        User result = walletService.generateWallet(1L);

        // Then
        assertThat(result.getWalletAddress()).isNotNull();
        assertThat(result.getWalletAddress()).startsWith("0x");
        assertThat(result.getWalletAddress()).hasSize(42);

        verify(userRepository).save(argThat(u ->
            u.getWalletAddress() != null &&
            u.getWalletAddress().startsWith("0x")
        ));
    }
}
```

### Controller Test (MockMvc)
```java
package co.grtk.stableips.controller;

import co.grtk.stableips.model.Transaction;
import co.grtk.stableips.model.dto.TransferDto;
import co.grtk.stableips.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @Test
    void shouldInitiateTransfer() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);

        Transaction tx = Transaction.builder()
            .id(1L)
            .txHash("0xTxHash123")
            .status(TransactionStatus.SUBMITTED)
            .build();

        when(transactionService.initiateTransfer(eq(1L), any(TransferDto.class)))
            .thenReturn(tx);

        // When/Then
        mockMvc.perform(post("/wallet/transfer")
                .session(session)
                .param("recipient", "0xDEF456")
                .param("amount", "10.50")
                .param("token", "USDC"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Transfer initiated")))
            .andExpect(content().string(containsString("0xTxHash123")));
    }

    @Test
    void shouldReturnErrorWhenNotAuthenticated() throws Exception {
        // When/Then
        mockMvc.perform(post("/wallet/transfer")
                .param("recipient", "0xDEF456")
                .param("amount", "10.50")
                .param("token", "USDC"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(containsString("Not authenticated")));
    }

    @Test
    void shouldValidateTransferAmount() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);

        // When/Then
        mockMvc.perform(post("/wallet/transfer")
                .session(session)
                .param("recipient", "0xDEF456")
                .param("amount", "-10")  // Invalid amount
                .param("token", "USDC"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("must be positive")));
    }

    @Test
    void shouldValidateEthereumAddress() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);

        // When/Then
        mockMvc.perform(post("/wallet/transfer")
                .session(session)
                .param("recipient", "invalid-address")
                .param("amount", "10.50")
                .param("token", "USDC"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("Invalid Ethereum address")));
    }

    @Test
    void shouldHandleInsufficientFunds() throws Exception {
        // Given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);

        when(transactionService.initiateTransfer(eq(1L), any()))
            .thenThrow(new InsufficientFundsException("Insufficient balance"));

        // When/Then
        mockMvc.perform(post("/wallet/transfer")
                .session(session)
                .param("recipient", "0xDEF456")
                .param("amount", "1000")
                .param("token", "USDC"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("Insufficient balance")));
    }
}
```

### Repository Test (Testcontainers)
```java
package co.grtk.stableips.repository;

import co.grtk.stableips.model.Transaction;
import co.grtk.stableips.model.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("stableips_test")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldFindByUserId() {
        // Given
        Transaction tx1 = createTransaction(1L, "0xABC", TransactionStatus.CONFIRMED);
        Transaction tx2 = createTransaction(1L, "0xDEF", TransactionStatus.PENDING);
        Transaction tx3 = createTransaction(2L, "0xGHI", TransactionStatus.CONFIRMED);

        entityManager.persist(tx1);
        entityManager.persist(tx2);
        entityManager.persist(tx3);
        entityManager.flush();

        // When
        List<Transaction> result = transactionRepository.findByUserIdOrderByCreatedAtDesc(1L);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Transaction::getTxHash)
            .containsExactly("0xDEF", "0xABC");  // Descending order
    }

    @Test
    void shouldFindPendingTransactions() {
        // Given
        Transaction pending = createTransaction(1L, "0xABC", TransactionStatus.PENDING);
        Transaction confirmed = createTransaction(1L, "0xDEF", TransactionStatus.CONFIRMED);

        entityManager.persist(pending);
        entityManager.persist(confirmed);
        entityManager.flush();

        // When
        List<Transaction> result = transactionRepository.findByStatus(TransactionStatus.PENDING);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTxHash()).isEqualTo("0xABC");
    }

    @Test
    void shouldFindByTxHash() {
        // Given
        Transaction tx = createTransaction(1L, "0xUniqueHash", TransactionStatus.CONFIRMED);
        entityManager.persist(tx);
        entityManager.flush();

        // When
        var result = transactionRepository.findByTxHash("0xUniqueHash");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(1L);
    }

    private Transaction createTransaction(Long userId, String txHash, TransactionStatus status) {
        return Transaction.builder()
            .userId(userId)
            .recipient("0xRecipient")
            .amount(new BigDecimal("10.00"))
            .token("USDC")
            .txHash(txHash)
            .status(status)
            .createdAt(Instant.now())
            .build();
    }
}
```

### Integration Test (Full Spring Context)
```java
package co.grtk.stableips.integration;

import co.grtk.stableips.model.Transaction;
import co.grtk.stableips.model.User;
import co.grtk.stableips.model.dto.TransferDto;
import co.grtk.stableips.repository.TransactionRepository;
import co.grtk.stableips.repository.UserRepository;
import co.grtk.stableips.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@Transactional
class TransferIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @MockBean
    private ContractService contractService;  // Mock blockchain

    private User sender;

    @BeforeEach
    void setUp() {
        sender = userRepository.save(User.builder()
            .username("alice")
            .walletAddress("0xSender")
            .build());

        // Mock blockchain responses
        when(contractService.getUsdcBalance(anyString()))
            .thenReturn(new BigDecimal("100.00"));
        when(contractService.transferUsdc(any(), any(), any(), any()))
            .thenReturn("0xTxHash");
    }

    @Test
    void shouldTransferUsdcSuccessfully() {
        // Given
        TransferDto dto = TransferDto.builder()
            .recipient("0xRecipient")
            .amount(new BigDecimal("10.00"))
            .token("USDC")
            .build();

        // When
        Transaction tx = transactionService.initiateTransfer(sender.getId(), dto);

        // Then
        assertThat(tx.getId()).isNotNull();
        assertThat(tx.getTxHash()).isEqualTo("0xTxHash");
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUBMITTED);

        // Verify persistence
        Transaction saved = transactionRepository.findById(tx.getId()).orElseThrow();
        assertThat(saved.getUserId()).isEqualTo(sender.getId());
        assertThat(saved.getAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldRollbackOnBlockchainFailure() {
        // Given
        when(contractService.transferUsdc(any(), any(), any(), any()))
            .thenThrow(new RuntimeException("Blockchain error"));

        TransferDto dto = TransferDto.builder()
            .recipient("0xRecipient")
            .amount(new BigDecimal("10.00"))
            .token("USDC")
            .build();

        // When/Then
        assertThatThrownBy(() -> transactionService.initiateTransfer(sender.getId(), dto))
            .isInstanceOf(TransferFailedException.class);

        // Transaction should be marked as FAILED
        List<Transaction> transactions = transactionRepository.findByUserId(sender.getId());
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getStatus()).isEqualTo(TransactionStatus.FAILED);
    }
}
```

## Test Utilities

### Test Data Builder
```java
package co.grtk.stableips.util;

import co.grtk.stableips.model.Transaction;
import co.grtk.stableips.model.TransactionStatus;
import co.grtk.stableips.model.User;

import java.math.BigDecimal;
import java.time.Instant;

public class TestDataBuilder {

    public static User.UserBuilder userBuilder() {
        return User.builder()
            .username("testuser")
            .email("test@example.com")
            .walletAddress("0x" + "0".repeat(40))
            .active(true)
            .createdAt(Instant.now());
    }

    public static Transaction.TransactionBuilder transactionBuilder() {
        return Transaction.builder()
            .userId(1L)
            .recipient("0xRecipient")
            .amount(new BigDecimal("10.00"))
            .token("USDC")
            .status(TransactionStatus.PENDING)
            .createdAt(Instant.now());
    }

    public static User createUser(String username, String walletAddress) {
        return userBuilder()
            .username(username)
            .walletAddress(walletAddress)
            .build();
    }

    public static Transaction createTransaction(Long userId, String txHash, TransactionStatus status) {
        return transactionBuilder()
            .userId(userId)
            .txHash(txHash)
            .status(status)
            .build();
    }
}
```

### Mock Web3j Utilities
```java
package co.grtk.stableips.util;

import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockWeb3jUtils {

    public static TransactionReceipt mockSuccessfulReceipt(String txHash) {
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getTransactionHash()).thenReturn(txHash);
        when(receipt.getStatus()).thenReturn("0x1");  // Success
        when(receipt.isStatusOK()).thenReturn(true);
        return receipt;
    }

    public static TransactionReceipt mockFailedReceipt(String txHash) {
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getTransactionHash()).thenReturn(txHash);
        when(receipt.getStatus()).thenReturn("0x0");  // Failed
        when(receipt.isStatusOK()).thenReturn(false);
        return receipt;
    }
}
```

## Running Tests

### Gradle Commands
```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests "WalletServiceTest"

# Specific test method
./gradlew test --tests "WalletServiceTest.shouldGetWallet"

# Unit tests only (exclude integration)
./gradlew test --tests "*Test" --exclude-tag "integration"

# Integration tests only
./gradlew test --tests "*IntegrationTest"

# With coverage report
./gradlew test jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html

# Continuous testing
./gradlew test --continuous
```

### Test Tags
```java
import org.junit.jupiter.api.Tag;

@Tag("unit")
class UserServiceTest { }

@Tag("integration")
class TransferIntegrationTest { }

@Tag("slow")
class BlockchainIntegrationTest { }
```

## Coverage Configuration

### Gradle JaCoCo Setup
```gradle
// build.gradle
plugins {
    id 'jacoco'
}

jacoco {
    toolVersion = "0.8.11"
}

test {
    finalizedBy jacocoTestReport
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }

    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.collect {
            fileTree(dir: it, exclude: [
                '**/config/**',
                '**/dto/**',
                '**/Application.class'
            ])
        }))
    }
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.80  // 80% overall
            }
        }

        rule {
            element = 'CLASS'
            includes = ['co.grtk.stableips.service.*']
            limit {
                minimum = 0.85  // 85% for services
            }
        }

        rule {
            element = 'CLASS'
            includes = ['co.grtk.stableips.controller.*']
            limit {
                minimum = 0.70  // 70% for controllers
            }
        }
    }
}

check.dependsOn jacocoTestCoverageVerification
```

## Best Practices

### 1. Test Naming
```java
// ✅ Clear, descriptive names
@Test
void shouldCreateUserWhenUsernameIsUnique() { }

@Test
void shouldThrowExceptionWhenUsernameAlreadyExists() { }

// ❌ Vague names
@Test
void testUser() { }

@Test
void test1() { }
```

### 2. AAA Pattern (Arrange-Act-Assert)
```java
@Test
void shouldTransferFunds() {
    // Arrange (Given)
    User sender = createUser("alice");
    when(contractService.getBalance(any())).thenReturn(BigDecimal.valueOf(100));

    // Act (When)
    Transaction tx = transactionService.transfer(sender.getId(), dto);

    // Assert (Then)
    assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUBMITTED);
    verify(contractService).transferUsdc(any(), any(), any(), any());
}
```

### 3. One Assertion Per Test (When Possible)
```java
// ✅ Focused tests
@Test
void shouldReturnCorrectUsdcBalance() {
    WalletDto wallet = walletService.getWallet(userId);
    assertThat(wallet.getUsdcBalance()).isEqualByComparingTo("100.00");
}

@Test
void shouldReturnCorrectDaiBalance() {
    WalletDto wallet = walletService.getWallet(userId);
    assertThat(wallet.getDaiBalance()).isEqualByComparingTo("50.00");
}

// ⚠️ Multiple assertions (acceptable for related properties)
@Test
void shouldReturnCompleteWalletData() {
    WalletDto wallet = walletService.getWallet(userId);
    assertThat(wallet.getAddress()).isNotNull();
    assertThat(wallet.getUsdcBalance()).isPositive();
    assertThat(wallet.getDaiBalance()).isPositive();
}
```

### 4. Test Edge Cases
```java
@Test void shouldHandleZeroBalance() { }
@Test void shouldHandleNegativeAmount() { }
@Test void shouldHandleVeryLargeAmount() { }
@Test void shouldHandleInvalidAddress() { }
@Test void shouldHandleNullInput() { }
@Test void shouldHandleEmptyString() { }
@Test void shouldHandleConcurrentTransfers() { }
```

## Resources
- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [Testcontainers Documentation](https://www.testcontainers.org/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/reference/testing/index.html)

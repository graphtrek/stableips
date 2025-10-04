# Database Migration Subagent

## Purpose
Specialized agent for handling JPA entities, repository patterns, and database schema changes in the StableIPs project.

## Available Tools
- Read
- Edit
- Write
- Grep

## Use Cases
- Creating new JPA entities with proper annotations
- Implementing repository interfaces with custom queries
- Modifying entity relationships (OneToMany, ManyToOne, etc.)
- Adding database indexes for performance
- Creating database migrations (Flyway/Liquibase)
- Implementing soft deletes and audit fields
- Optimizing queries with projections and DTOs

## Architecture Context

### Package Structure
```
src/main/java/co/grtk/stableips/
├── model/
│   ├── User.java                # User entity
│   ├── Transaction.java         # Transaction log entity
│   ├── Wallet.java              # Wallet entity (future)
│   └── AuditLog.java            # Audit trail entity (future)
├── repository/
│   ├── UserRepository.java      # User data access
│   └── TransactionRepository.java # Transaction data access
└── config/
    └── DatabaseConfig.java      # DataSource, JPA configuration

src/main/resources/
├── application.properties       # Database connection
└── db/migration/                # Flyway migrations (optional)
    ├── V1__create_users_table.sql
    └── V2__create_transactions_table.sql
```

### Database Configuration
```properties
# application.properties
spring.datasource.url=jdbc:postgresql://localhost:5432/stableips
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=update  # Dev: update | Prod: validate
spring.jpa.show-sql=true              # Log SQL queries (dev only)
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Connection pool
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=30000
```

## Entity Patterns

### Basic Entity
```java
package co.grtk.stableips.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "wallet_address", unique = true, length = 42)
    private String walletAddress;

    @Column(name = "encrypted_private_key", columnDefinition = "TEXT")
    private String encryptedPrivateKey;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
```

### Entity with Relationships
```java
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 42)
    private String recipient;

    @Column(nullable = false, precision = 36, scale = 18)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String token;  // USDC, DAI, etc.

    @Column(name = "tx_hash", unique = true, length = 66)
    private String txHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}

@Getter
@AllArgsConstructor
public enum TransactionStatus {
    PENDING("Transaction created, not yet submitted"),
    SUBMITTED("Submitted to blockchain"),
    CONFIRMED("Confirmed on blockchain"),
    FAILED("Transaction failed");

    private final String description;
}
```

### Composite Key Entity
```java
@Entity
@Table(name = "wallet_balances")
@IdClass(WalletBalanceId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletBalance {

    @Id
    @Column(name = "wallet_address", length = 42)
    private String walletAddress;

    @Id
    @Column(length = 10)
    private String token;

    @Column(nullable = false, precision = 36, scale = 18)
    private BigDecimal balance;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;
}

// Composite key class
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletBalanceId implements Serializable {
    private String walletAddress;
    private String token;
}
```

### Audit Entity (Base Class)
```java
@MappedSuperclass
@Getter
@Setter
public abstract class AuditableEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 50, updatable = false)
    private String createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        // Get current user from SecurityContext
        createdBy = getCurrentUsername();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        updatedBy = getCurrentUsername();
    }

    private String getCurrentUsername() {
        // TODO: Get from Spring Security context
        return "system";
    }
}

// Usage
@Entity
@Table(name = "users")
public class User extends AuditableEntity {
    // User-specific fields
}
```

### Soft Delete Pattern
```java
@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deleted = true WHERE id = ?")
@Where(clause = "deleted = false")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PreRemove
    protected void onDelete() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
```

## Repository Patterns

### Basic Repository
```java
package co.grtk.stableips.repository;

import co.grtk.stableips.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Derived query methods (no implementation needed)
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByWalletAddress(String walletAddress);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
```

### Repository with Custom Queries
```java
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Derived queries
    List<Transaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Transaction> findByStatus(TransactionStatus status);

    Optional<Transaction> findByTxHash(String txHash);

    // Custom JPQL query
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.status = :status")
    List<Transaction> findByUserIdAndStatus(
        @Param("userId") Long userId,
        @Param("status") TransactionStatus status
    );

    // Native SQL query
    @Query(value = "SELECT * FROM transactions WHERE created_at > :since", nativeQuery = true)
    List<Transaction> findRecentTransactions(@Param("since") Instant since);

    // Custom query with projection
    @Query("SELECT new co.grtk.stableips.model.dto.TransactionSummaryDto(" +
           "t.id, t.txHash, t.amount, t.status) " +
           "FROM Transaction t WHERE t.user.id = :userId")
    List<TransactionSummaryDto> findTransactionSummaries(@Param("userId") Long userId);

    // Aggregate query
    @Query("SELECT SUM(t.amount) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.token = :token AND t.status = 'CONFIRMED'")
    BigDecimal getTotalTransferred(
        @Param("userId") Long userId,
        @Param("token") String token
    );

    // Count query
    long countByUserIdAndStatus(Long userId, TransactionStatus status);
}
```

### Repository with Pagination
```java
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByUserId(Long userId, Pageable pageable);

    Page<Transaction> findByUserIdAndStatus(
        Long userId,
        TransactionStatus status,
        Pageable pageable
    );

    // Custom paginated query
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.amount >= :minAmount")
    Page<Transaction> findLargeTransactions(
        @Param("userId") Long userId,
        @Param("minAmount") BigDecimal minAmount,
        Pageable pageable
    );
}

// Usage in service
public Page<Transaction> getUserTransactions(Long userId, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    return transactionRepository.findByUserId(userId, pageable);
}
```

### Repository with Specifications (Dynamic Queries)
```java
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>,
                                              JpaSpecificationExecutor<Transaction> {
}

// Specification class
public class TransactionSpecifications {

    public static Specification<Transaction> hasUserId(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Transaction> hasStatus(TransactionStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Transaction> hasToken(String token) {
        return (root, query, cb) -> cb.equal(root.get("token"), token);
    }

    public static Specification<Transaction> createdAfter(Instant date) {
        return (root, query, cb) -> cb.greaterThan(root.get("createdAt"), date);
    }

    public static Specification<Transaction> amountBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> cb.between(root.get("amount"), min, max);
    }
}

// Usage in service
public List<Transaction> searchTransactions(Long userId, TransactionStatus status, String token) {
    Specification<Transaction> spec = Specification
        .where(TransactionSpecifications.hasUserId(userId))
        .and(TransactionSpecifications.hasStatus(status))
        .and(TransactionSpecifications.hasToken(token));

    return transactionRepository.findAll(spec);
}
```

## Database Indexes

### Entity-Level Indexes
```java
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_tx_hash", columnList = "tx_hash", unique = true),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_user_status", columnList = "user_id, status")  // Composite
})
public class Transaction {
    // ...
}
```

### Unique Constraints
```java
@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(name = "uk_username", columnNames = "username"),
    @UniqueConstraint(name = "uk_email", columnNames = "email"),
    @UniqueConstraint(name = "uk_wallet", columnNames = "wallet_address")
})
public class User {
    // ...
}
```

## Database Migrations (Flyway)

### Setup
```gradle
// build.gradle
dependencies {
    implementation 'org.flywaydb:flyway-core'
    runtimeOnly 'org.flywaydb:flyway-database-postgresql'
}
```

```properties
# application.properties
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.locations=classpath:db/migration

# Switch from hibernate auto-update
spring.jpa.hibernate.ddl-auto=validate  # Only validate, don't auto-create
```

### Migration Files
```sql
-- V1__create_users_table.sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    wallet_address VARCHAR(42) UNIQUE,
    encrypted_private_key TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_wallet ON users(wallet_address);
```

```sql
-- V2__create_transactions_table.sql
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    recipient VARCHAR(42) NOT NULL,
    amount NUMERIC(36, 18) NOT NULL,
    token VARCHAR(10) NOT NULL,
    tx_hash VARCHAR(66) UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP
);

CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_tx_hash ON transactions(tx_hash);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_transactions_user_status ON transactions(user_id, status);
```

```sql
-- V3__add_user_verification.sql
ALTER TABLE users
ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false,
ADD COLUMN verification_token VARCHAR(100),
ADD COLUMN verification_sent_at TIMESTAMP;

CREATE INDEX idx_users_verification_token ON users(verification_token);
```

## Query Optimization

### Projections (DTO Queries)
```java
// DTO projection interface
public interface TransactionSummary {
    Long getId();
    String getTxHash();
    BigDecimal getAmount();
    TransactionStatus getStatus();
}

// Repository
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Interface projection (Spring generates implementation)
    List<TransactionSummary> findByUserId(Long userId);

    // Class-based DTO projection
    @Query("SELECT new co.grtk.stableips.model.dto.TransactionDto(" +
           "t.id, t.txHash, t.amount, t.status) " +
           "FROM Transaction t WHERE t.user.id = :userId")
    List<TransactionDto> findTransactionDtos(@Param("userId") Long userId);
}
```

### Fetch Strategies
```java
// Lazy loading (default for @ManyToOne, @OneToOne)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id")
private User user;

// Eager loading (use sparingly)
@ManyToOne(fetch = FetchType.EAGER)
@JoinColumn(name = "user_id")
private User user;

// Fetch join in query (avoid N+1)
@Query("SELECT t FROM Transaction t JOIN FETCH t.user WHERE t.id = :id")
Optional<Transaction> findByIdWithUser(@Param("id") Long id);

// Entity graph (alternative to fetch join)
@EntityGraph(attributePaths = {"user"})
@Query("SELECT t FROM Transaction t WHERE t.id = :id")
Optional<Transaction> findByIdWithUser(@Param("id") Long id);
```

### Batch Operations
```java
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Batch update
    @Modifying
    @Query("UPDATE Transaction t SET t.status = :newStatus WHERE t.status = :oldStatus")
    int updateStatus(
        @Param("oldStatus") TransactionStatus oldStatus,
        @Param("newStatus") TransactionStatus newStatus
    );

    // Batch delete
    @Modifying
    @Query("DELETE FROM Transaction t WHERE t.createdAt < :before AND t.status = 'FAILED'")
    int deleteOldFailedTransactions(@Param("before") Instant before);
}

// Service usage
@Transactional
public void cleanupOldTransactions() {
    Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
    int deleted = transactionRepository.deleteOldFailedTransactions(thirtyDaysAgo);
    log.info("Deleted {} old failed transactions", deleted);
}
```

## Testing Repositories

### Repository Test (Testcontainers)
```java
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldFindByUsername() {
        // Given
        User user = User.builder()
            .username("alice")
            .email("alice@example.com")
            .build();
        entityManager.persist(user);
        entityManager.flush();

        // When
        Optional<User> found = userRepository.findByUsername("alice");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void shouldEnforceUniqueUsername() {
        // Given
        User user1 = User.builder().username("alice").email("alice1@example.com").build();
        entityManager.persist(user1);
        entityManager.flush();

        User user2 = User.builder().username("alice").email("alice2@example.com").build();

        // When/Then
        assertThatThrownBy(() -> {
            entityManager.persist(user2);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }
}
```

## Best Practices

### 1. Use Appropriate Fetch Types
```java
// ✅ Lazy for collections
@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
private List<Transaction> transactions;

// ✅ Use fetch joins when you need the data
@Query("SELECT u FROM User u LEFT JOIN FETCH u.transactions WHERE u.id = :id")
Optional<User> findByIdWithTransactions(@Param("id") Long id);
```

### 2. Always Use Indexes for Queries
```java
// ✅ Index on frequently queried columns
@Table(indexes = {
    @Index(columnList = "user_id"),
    @Index(columnList = "status"),
    @Index(columnList = "created_at")
})
```

### 3. Use DTOs for Read Operations
```java
// ✅ Return DTOs
public List<TransactionDto> getUserTransactions(Long userId) {
    return transactionRepository.findTransactionDtos(userId);
}

// ❌ Return entities (may cause lazy loading issues)
public List<Transaction> getUserTransactions(Long userId) {
    return transactionRepository.findByUserId(userId);
}
```

### 4. Use @Transactional Properly
```java
// ✅ Read-only for queries
@Transactional(readOnly = true)
public User findById(Long id) {
    return userRepository.findById(id).orElseThrow();
}

// ✅ Writable for updates
@Transactional
public User updateUser(Long id, UpdateUserDto dto) {
    User user = findById(id);
    user.setEmail(dto.getEmail());
    return userRepository.save(user);
}
```

## Resources
- [Spring Data JPA Documentation](https://docs.spring.io/spring-data/jpa/reference/)
- [Hibernate ORM Documentation](https://hibernate.org/orm/documentation/)
- [Flyway Documentation](https://documentation.red-gate.com/fd)
- [Testcontainers Documentation](https://www.testcontainers.org/)

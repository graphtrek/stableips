package co.grtk.stableips.repository;

import co.grtk.stableips.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveAndRetrieveUser() {
        // Given
        User user = new User("alice", "0x1234567890123456789012345678901234567890");
        user.setPrivateKey("0xprivatekey");

        // When
        User saved = userRepository.save(user);
        entityManager.flush();
        User found = userRepository.findById(saved.getId()).orElse(null);

        // Then
        assertThat(found).isNotNull();
        assertThat(found.getUsername()).isEqualTo("alice");
        assertThat(found.getWalletAddress()).isEqualTo("0x1234567890123456789012345678901234567890");
        assertThat(found.getPrivateKey()).isEqualTo("0xprivatekey");
    }

    @Test
    void shouldFindUserByUsername() {
        // Given
        User user = new User("bob", "0xABCDEF");
        user.setPrivateKey("0xkey123");
        entityManager.persist(user);
        entityManager.flush();

        // When
        Optional<User> found = userRepository.findByUsername("bob");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("bob");
    }

    @Test
    void shouldReturnEmptyWhenUsernameNotFound() {
        // When
        Optional<User> found = userRepository.findByUsername("nonexistent");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindUserByWalletAddress() {
        // Given
        User user = new User("charlie", "0xWALLET123");
        user.setPrivateKey("0xprivate");
        entityManager.persist(user);
        entityManager.flush();

        // When
        Optional<User> found = userRepository.findByWalletAddress("0xWALLET123");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getWalletAddress()).isEqualTo("0xWALLET123");
    }

    @Test
    void shouldEnforceUniqueUsername() {
        // Given
        User user1 = new User("dave", "0xADDR1");
        user1.setPrivateKey("0xkey1");
        entityManager.persist(user1);
        entityManager.flush();
        entityManager.clear();

        // When
        User user2 = new User("dave", "0xADDR2");
        user2.setPrivateKey("0xkey2");

        // Then - trying to save duplicate username should fail
        try {
            userRepository.save(user2);
            entityManager.flush();
            assertThat(false).as("Should have thrown exception for duplicate username").isTrue();
        } catch (Exception e) {
            // Expected: constraint violation
            assertThat(e).isNotNull();
        }
    }
}

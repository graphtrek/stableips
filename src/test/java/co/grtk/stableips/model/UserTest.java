package co.grtk.stableips.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void shouldCreateUserWithUsernameAndWalletAddress() {
        // Given
        String username = "alice";
        String walletAddress = "0x1234567890123456789012345678901234567890";

        // When
        User user = new User(username, walletAddress);

        // Then
        assertThat(user.getUsername()).isEqualTo(username);
        assertThat(user.getWalletAddress()).isEqualTo(walletAddress);
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldGenerateIdWhenPersisted() {
        // Given
        User user = new User("bob", "0xABCDEF");

        // When
        user.setId(1L);

        // Then
        assertThat(user.getId()).isEqualTo(1L);
    }

    @Test
    void shouldSetAndGetPrivateKey() {
        // Given
        User user = new User("charlie", "0x123");
        String privateKey = "0xprivatekey123";

        // When
        user.setPrivateKey(privateKey);

        // Then
        assertThat(user.getPrivateKey()).isEqualTo(privateKey);
    }

    @Test
    void shouldTrackCreationTimestamp() {
        // Given
        LocalDateTime before = LocalDateTime.now();

        // When
        User user = new User("dave", "0x456");
        LocalDateTime after = LocalDateTime.now();

        // Then
        assertThat(user.getCreatedAt()).isAfter(before.minusSeconds(1));
        assertThat(user.getCreatedAt()).isBefore(after.plusSeconds(1));
    }
}

package co.grtk.stableips.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTest {

    @Test
    void shouldCreateTransactionWithAllFields() {
        // Given
        Long userId = 1L;
        String recipient = "0x9876543210987654321098765432109876543210";
        BigDecimal amount = new BigDecimal("100.50");
        String token = "USDC";
        String txHash = "0xabcdef1234567890";
        String status = "PENDING";

        // When
        Transaction transaction = new Transaction(userId, recipient, amount, token, "ETHEREUM", txHash, status);

        // Then
        assertThat(transaction.getUserId()).isEqualTo(userId);
        assertThat(transaction.getRecipient()).isEqualTo(recipient);
        assertThat(transaction.getAmount()).isEqualByComparingTo(amount);
        assertThat(transaction.getToken()).isEqualTo(token);
        assertThat(transaction.getTxHash()).isEqualTo(txHash);
        assertThat(transaction.getStatus()).isEqualTo(status);
        assertThat(transaction.getTimestamp()).isNotNull();
    }

    @Test
    void shouldGenerateIdWhenPersisted() {
        // Given
        Transaction transaction = new Transaction(1L, "0x123", BigDecimal.TEN, "EURC", "ETHEREUM", "0xhash", "CONFIRMED");

        // When
        transaction.setId(100L);

        // Then
        assertThat(transaction.getId()).isEqualTo(100L);
    }

    @Test
    void shouldTrackTimestamp() {
        // Given
        LocalDateTime before = LocalDateTime.now();

        // When
        Transaction transaction = new Transaction(1L, "0x456", new BigDecimal("50"), "USDC", "ETHEREUM", "0xhash2", "PENDING");
        LocalDateTime after = LocalDateTime.now();

        // Then
        assertThat(transaction.getTimestamp()).isAfter(before.minusSeconds(1));
        assertThat(transaction.getTimestamp()).isBefore(after.plusSeconds(1));
    }

    @Test
    void shouldUpdateStatus() {
        // Given
        Transaction transaction = new Transaction(1L, "0x789", BigDecimal.ONE, "EURC", "ETHEREUM", "0xhash3", "PENDING");

        // When
        transaction.setStatus("CONFIRMED");

        // Then
        assertThat(transaction.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void shouldSupportDifferentTokenTypes() {
        // Given & When
        Transaction usdcTx = new Transaction(1L, "0xA", BigDecimal.TEN, "USDC", "ETHEREUM", "0x1", "PENDING");
        Transaction eurcTx = new Transaction(1L, "0xB", BigDecimal.TEN, "EURC", "ETHEREUM", "0x2", "CONFIRMED");

        // Then
        assertThat(usdcTx.getToken()).isEqualTo("USDC");
        assertThat(eurcTx.getToken()).isEqualTo("EURC");
    }
}

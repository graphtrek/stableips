package co.grtk.stableips.service.validation;

import co.grtk.stableips.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for TransferValidationService.
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
class TransferValidationServiceTest {

    private TransferValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new TransferValidationService();
    }

    @Test
    void shouldValidateValidAmount() {
        assertThatCode(() -> validationService.validateAmount(new BigDecimal("100.50")))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNullAmount() {
        assertThatThrownBy(() -> validationService.validateAmount(null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Transfer amount is required");
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> validationService.validateAmount(BigDecimal.ZERO))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("must be greater than 0");
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> validationService.validateAmount(new BigDecimal("-10")))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("must be greater than 0");
    }

    @Test
    void shouldRejectExcessivePrecision() {
        BigDecimal excessivePrecision = new BigDecimal("1.1234567890123456789");
        assertThatThrownBy(() -> validationService.validateAmount(excessivePrecision))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("too many decimal places");
    }

    @Test
    void shouldValidateValidRecipient() {
        assertThatCode(() -> validationService.validateRecipient("0x1234567890abcdef1234567890abcdef12345678"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNullRecipient() {
        assertThatThrownBy(() -> validationService.validateRecipient(null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Recipient address is required");
    }

    @Test
    void shouldRejectEmptyRecipient() {
        assertThatThrownBy(() -> validationService.validateRecipient(""))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Recipient address is required");
    }

    @Test
    void shouldRejectBlankRecipient() {
        assertThatThrownBy(() -> validationService.validateRecipient("   "))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Recipient address is required");
    }

    @Test
    void shouldValidateValidEthereumAddress() {
        assertThatCode(() -> validationService.validateEthereumAddress("0x1234567890abcdef1234567890abcdef12345678"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectInvalidEthereumAddress() {
        assertThatThrownBy(() -> validationService.validateEthereumAddress("invalid-address"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Invalid Ethereum address format");
    }

    @Test
    void shouldRejectShortEthereumAddress() {
        assertThatThrownBy(() -> validationService.validateEthereumAddress("0x123"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Invalid Ethereum address format");
    }

    @Test
    void shouldValidateSupportedTokens() {
        assertThatCode(() -> validationService.validateToken("USDC")).doesNotThrowAnyException();
        assertThatCode(() -> validationService.validateToken("DAI")).doesNotThrowAnyException();
        assertThatCode(() -> validationService.validateToken("ETH")).doesNotThrowAnyException();
        assertThatCode(() -> validationService.validateToken("XRP")).doesNotThrowAnyException();
        assertThatCode(() -> validationService.validateToken("SOL")).doesNotThrowAnyException();
    }

    @Test
    void shouldValidateTokensCaseInsensitive() {
        assertThatCode(() -> validationService.validateToken("usdc")).doesNotThrowAnyException();
        assertThatCode(() -> validationService.validateToken("Dai")).doesNotThrowAnyException();
        assertThatCode(() -> validationService.validateToken("eTh")).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUnsupportedToken() {
        assertThatThrownBy(() -> validationService.validateToken("BTC"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Unsupported token type");
    }

    @Test
    void shouldRejectNullToken() {
        assertThatThrownBy(() -> validationService.validateToken(null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Token type is required");
    }

    @Test
    void shouldRejectEmptyToken() {
        assertThatThrownBy(() -> validationService.validateToken(""))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Token type is required");
    }

    @Test
    void shouldValidateCompleteTransferRequest() {
        assertThatCode(() -> validationService.validateTransferRequest(
            "0x1234567890abcdef1234567890abcdef12345678",
            new BigDecimal("100"),
            "USDC"
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldValidateXrpTransferWithoutEthereumAddressCheck() {
        // XRP addresses don't need to pass Ethereum validation
        assertThatCode(() -> validationService.validateTransferRequest(
            "rN7n7otQDd6FczFgLdllrq4OhiX1zp7n8",
            new BigDecimal("10"),
            "XRP"
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldValidateSolanaTransferWithoutEthereumAddressCheck() {
        // Solana addresses don't need to pass Ethereum validation
        assertThatCode(() -> validationService.validateTransferRequest(
            "DYw8jCTfwHNRJhhmFcbXvVDTqWMEVFBX6Z",
            new BigDecimal("5"),
            "SOL"
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectTransferWithInvalidAmount() {
        assertThatThrownBy(() -> validationService.validateTransferRequest(
            "0x1234567890abcdef1234567890abcdef12345678",
            new BigDecimal("-10"),
            "USDC"
        )).isInstanceOf(ValidationException.class)
            .hasMessageContaining("must be greater than 0");
    }

    @Test
    void shouldRejectTransferWithInvalidToken() {
        assertThatThrownBy(() -> validationService.validateTransferRequest(
            "0x1234567890abcdef1234567890abcdef12345678",
            new BigDecimal("100"),
            "BTC"
        )).isInstanceOf(ValidationException.class)
            .hasMessageContaining("Unsupported token type");
    }

    @Test
    void shouldRejectEthereumTransferWithInvalidAddress() {
        assertThatThrownBy(() -> validationService.validateTransferRequest(
            "invalid-address",
            new BigDecimal("100"),
            "USDC"
        )).isInstanceOf(ValidationException.class)
            .hasMessageContaining("Invalid Ethereum address format");
    }
}

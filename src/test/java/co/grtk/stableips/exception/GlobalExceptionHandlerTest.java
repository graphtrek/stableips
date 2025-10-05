package co.grtk.stableips.exception;

import gg.jte.TemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.ModelAndView;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalExceptionHandler.
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private TemplateEngine templateEngine;

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler(templateEngine);
    }

    @Test
    void shouldHandleValidationException() {
        ValidationException ex = new ValidationException("Invalid amount");

        ModelAndView mav = exceptionHandler.handleValidationException(ex);

        assertThat(mav.getViewName()).isEqualTo("redirect:/wallet");
        assertThat(mav.getModel()).containsEntry("error", "Invalid amount");
    }

    @Test
    void shouldHandleAuthenticationException() {
        AuthenticationException ex = new AuthenticationException("Not authenticated");

        String viewName = exceptionHandler.handleAuthenticationException(ex);

        assertThat(viewName).isEqualTo("redirect:/login");
    }

    @Test
    void shouldHandleInsufficientBalanceException() {
        InsufficientBalanceException ex = new InsufficientBalanceException("Insufficient balance");

        ModelAndView mav = exceptionHandler.handleInsufficientBalanceException(ex);

        assertThat(mav.getViewName()).isEqualTo("redirect:/wallet");
        assertThat(mav.getModel()).containsKey("error");
    }

    @Test
    void shouldHandleInsufficientBalanceExceptionWithDetails() {
        InsufficientBalanceException ex = new InsufficientBalanceException(
            "Insufficient balance",
            new BigDecimal("50"),
            new BigDecimal("100"),
            "USDC"
        );

        ModelAndView mav = exceptionHandler.handleInsufficientBalanceException(ex);

        assertThat(mav.getViewName()).isEqualTo("redirect:/wallet");
        assertThat(mav.getModel()).containsKey("error");
        String errorMessage = (String) mav.getModel().get("error");
        assertThat(errorMessage).contains("Insufficient USDC balance");
        assertThat(errorMessage).contains("Available: 50");
        assertThat(errorMessage).contains("Required: 100");
    }

    @Test
    void shouldHandleBlockchainExceptionWithGasEstimationFailed() {
        BlockchainException ex = new BlockchainException(
            "Gas estimation failed",
            BlockchainException.BlockchainErrorType.GAS_ESTIMATION_FAILED
        );

        ModelAndView mav = exceptionHandler.handleBlockchainException(ex);

        assertThat(mav.getViewName()).isEqualTo("redirect:/wallet");
        String errorMessage = (String) mav.getModel().get("error");
        assertThat(errorMessage).contains("Gas estimation failed");
        assertThat(errorMessage).contains("enough ETH for gas fees");
    }

    @Test
    void shouldHandleBlockchainExceptionWithGasPriceTooHigh() {
        BlockchainException ex = new BlockchainException(
            "Gas price too high",
            BlockchainException.BlockchainErrorType.GAS_PRICE_TOO_HIGH
        );

        ModelAndView mav = exceptionHandler.handleBlockchainException(ex);

        assertThat(mav.getViewName()).isEqualTo("redirect:/wallet");
        String errorMessage = (String) mav.getModel().get("error");
        assertThat(errorMessage).contains("Gas price too high");
    }

    @Test
    void shouldHandleBlockchainExceptionWithTransactionReverted() {
        BlockchainException ex = new BlockchainException(
            "Transaction reverted",
            BlockchainException.BlockchainErrorType.TRANSACTION_REVERTED
        );

        ModelAndView mav = exceptionHandler.handleBlockchainException(ex);

        assertThat(mav.getViewName()).isEqualTo("redirect:/wallet");
        String errorMessage = (String) mav.getModel().get("error");
        assertThat(errorMessage).contains("Transaction would fail");
    }

    @Test
    void shouldHandleBlockchainExceptionWithNonceError() {
        BlockchainException ex = new BlockchainException(
            "Nonce error",
            BlockchainException.BlockchainErrorType.NONCE_ERROR
        );

        ModelAndView mav = exceptionHandler.handleBlockchainException(ex);

        assertThat(mav.getViewName()).isEqualTo("redirect:/wallet");
        String errorMessage = (String) mav.getModel().get("error");
        assertThat(errorMessage).contains("Transaction ordering issue");
    }

    @Test
    void shouldHandleBlockchainExceptionWithNetworkTimeout() {
        BlockchainException ex = new BlockchainException(
            "Network timeout",
            BlockchainException.BlockchainErrorType.NETWORK_TIMEOUT
        );

        ModelAndView mav = exceptionHandler.handleBlockchainException(ex);

        assertThat(mav.getViewName()).isEqualTo("redirect:/wallet");
        String errorMessage = (String) mav.getModel().get("error");
        assertThat(errorMessage).contains("Network timeout");
    }

    @Test
    void shouldHandleBlockchainExceptionWithInvalidAddress() {
        BlockchainException ex = new BlockchainException(
            "Invalid address",
            BlockchainException.BlockchainErrorType.INVALID_ADDRESS
        );

        ModelAndView mav = exceptionHandler.handleBlockchainException(ex);

        assertThat(mav.getViewName()).isEqualTo("redirect:/wallet");
        String errorMessage = (String) mav.getModel().get("error");
        assertThat(errorMessage).contains("Invalid recipient address");
    }

    @Test
    void shouldHandleBlockchainExceptionWithContractError() {
        BlockchainException ex = new BlockchainException(
            "Contract error",
            BlockchainException.BlockchainErrorType.CONTRACT_ERROR
        );

        ModelAndView mav = exceptionHandler.handleBlockchainException(ex);

        assertThat(mav.getViewName()).isEqualTo("redirect:/wallet");
        String errorMessage = (String) mav.getModel().get("error");
        assertThat(errorMessage).contains("Smart contract error");
    }

    @Test
    void shouldHandleBlockchainExceptionWithUnknownError() {
        BlockchainException ex = new BlockchainException(
            "Unknown blockchain error",
            BlockchainException.BlockchainErrorType.UNKNOWN
        );

        ModelAndView mav = exceptionHandler.handleBlockchainException(ex);

        assertThat(mav.getViewName()).isEqualTo("redirect:/wallet");
        String errorMessage = (String) mav.getModel().get("error");
        assertThat(errorMessage).contains("Blockchain transaction failed");
    }

    @Test
    void shouldHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        ModelAndView mav = exceptionHandler.handleIllegalArgumentException(ex);

        assertThat(mav.getViewName()).isEqualTo("redirect:/wallet");
        assertThat(mav.getModel()).containsEntry("error", "Invalid argument");
    }

    @Test
    void shouldHandleIllegalStateException() {
        IllegalStateException ex = new IllegalStateException("Configuration error");

        ModelAndView mav = exceptionHandler.handleIllegalStateException(ex);

        assertThat(mav.getViewName()).isEqualTo("redirect:/wallet");
        String errorMessage = (String) mav.getModel().get("error");
        assertThat(errorMessage).contains("Service configuration error");
        assertThat(errorMessage).contains("Configuration error");
    }

    @Test
    void shouldHandleGenericException() {
        Exception ex = new Exception("Unexpected error");

        ModelAndView mav = exceptionHandler.handleGenericException(ex);

        assertThat(mav.getViewName()).isEqualTo("redirect:/wallet");
        String errorMessage = (String) mav.getModel().get("error");
        assertThat(errorMessage).contains("unexpected error occurred");
    }
}

package co.grtk.stableips.service.validation;

import co.grtk.stableips.exception.AuthenticationException;
import co.grtk.stableips.exception.ValidationException;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for AuthValidationService.
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
class AuthValidationServiceTest {

    private AuthValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new AuthValidationService();
    }

    @Test
    void shouldValidateValidUsername() {
        assertThatCode(() -> validationService.validateUsername("alice"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldValidateUsernameWithSpaces() {
        assertThatCode(() -> validationService.validateUsername("  alice  "))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNullUsername() {
        assertThatThrownBy(() -> validationService.validateUsername(null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Username is required");
    }

    @Test
    void shouldRejectEmptyUsername() {
        assertThatThrownBy(() -> validationService.validateUsername(""))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Username is required");
    }

    @Test
    void shouldRejectBlankUsername() {
        assertThatThrownBy(() -> validationService.validateUsername("   "))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Username is required");
    }

    @Test
    void shouldRejectTooLongUsername() {
        String longUsername = "a".repeat(51);
        assertThatThrownBy(() -> validationService.validateUsername(longUsername))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("must not exceed 50 characters");
    }

    @Test
    void shouldValidateMaxLengthUsername() {
        String maxUsername = "a".repeat(50);
        assertThatCode(() -> validationService.validateUsername(maxUsername))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldValidateAuthenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);

        assertThatCode(() -> validationService.validateAuthenticated(session))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNullSession() {
        assertThatThrownBy(() -> validationService.validateAuthenticated(null))
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("not authenticated");
    }

    @Test
    void shouldRejectUnauthenticatedSession() {
        MockHttpSession session = new MockHttpSession();

        assertThatThrownBy(() -> validationService.validateAuthenticated(session))
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("not authenticated");
    }

    @Test
    void shouldReturnTrueForAuthenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);

        boolean result = validationService.isAuthenticated(session);

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseForNullSession() {
        boolean result = validationService.isAuthenticated(null);

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseForUnauthenticatedSession() {
        MockHttpSession session = new MockHttpSession();

        boolean result = validationService.isAuthenticated(session);

        assertThat(result).isFalse();
    }
}

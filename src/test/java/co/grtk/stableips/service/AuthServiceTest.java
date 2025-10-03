package co.grtk.stableips.service;

import co.grtk.stableips.model.User;
import co.grtk.stableips.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private HttpSession session;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldLoginExistingUser() {
        // Given
        String username = "alice";
        User user = new User(username, "0xWallet123");
        user.setId(1L);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // When
        User loggedInUser = authService.login(username, session);

        // Then
        assertThat(loggedInUser).isNotNull();
        assertThat(loggedInUser.getUsername()).isEqualTo(username);
        verify(session).setAttribute("userId", 1L);
        verify(session).setAttribute("username", username);
        verify(walletService, never()).createUserWithWallet(anyString());
    }

    @Test
    void shouldCreateAndLoginNewUser() {
        // Given
        String username = "bob";
        User newUser = new User(username, "0xNewWallet");
        newUser.setId(2L);

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(walletService.createUserWithWallet(username)).thenReturn(newUser);

        // When
        User loggedInUser = authService.login(username, session);

        // Then
        assertThat(loggedInUser).isNotNull();
        assertThat(loggedInUser.getUsername()).isEqualTo(username);
        verify(walletService).createUserWithWallet(username);
        verify(session).setAttribute("userId", 2L);
        verify(session).setAttribute("username", username);
    }

    @Test
    void shouldLogoutUser() {
        // When
        authService.logout(session);

        // Then
        verify(session).invalidate();
    }

    @Test
    void shouldCheckIfUserIsAuthenticated() {
        // Given
        when(session.getAttribute("userId")).thenReturn(1L);

        // When
        boolean isAuthenticated = authService.isAuthenticated(session);

        // Then
        assertThat(isAuthenticated).isTrue();
    }

    @Test
    void shouldReturnFalseWhenUserNotAuthenticated() {
        // Given
        when(session.getAttribute("userId")).thenReturn(null);

        // When
        boolean isAuthenticated = authService.isAuthenticated(session);

        // Then
        assertThat(isAuthenticated).isFalse();
    }

    @Test
    void shouldGetCurrentUser() {
        // Given
        Long userId = 1L;
        User user = new User("charlie", "0xWallet456");
        user.setId(userId);

        when(session.getAttribute("userId")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        User currentUser = authService.getCurrentUser(session);

        // Then
        assertThat(currentUser).isNotNull();
        assertThat(currentUser.getId()).isEqualTo(userId);
    }

    @Test
    void shouldThrowExceptionWhenGettingCurrentUserNotLoggedIn() {
        // Given
        when(session.getAttribute("userId")).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> authService.getCurrentUser(session))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not authenticated");
    }

    @Test
    void shouldThrowExceptionWhenCurrentUserNotFound() {
        // Given
        Long userId = 999L;
        when(session.getAttribute("userId")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.getCurrentUser(session))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("User not found");
    }
}

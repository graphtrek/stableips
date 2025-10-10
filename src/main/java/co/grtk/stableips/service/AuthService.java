package co.grtk.stableips.service;

import co.grtk.stableips.model.User;
import co.grtk.stableips.model.dto.UserDto;
import co.grtk.stableips.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final WalletService walletService;

    public AuthService(UserRepository userRepository, WalletService walletService) {
        this.userRepository = userRepository;
        this.walletService = walletService;
    }

    public User login(String username, HttpSession session) {
        User user = userRepository.findByUsername(username)
            .orElseGet(() -> walletService.createUserWithWallet(username));

        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());

        return user;
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    public boolean isAuthenticated(HttpSession session) {
        return session.getAttribute("userId") != null;
    }

    public User getCurrentUser(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            throw new RuntimeException("User not authenticated");
        }

        return userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found for ID: " + userId));
    }

    /**
     * Retrieves all users except the current authenticated user.
     *
     * <p>This method is primarily used for populating recipient dropdowns in transfer forms,
     * where users should not be able to transfer funds to themselves.</p>
     *
     * <p>Returns a read-only transaction since no data is modified. The result is a list
     * of lightweight DTOs containing only displayable user information (id, username, wallet address).</p>
     *
     * @param session the HTTP session containing the current user's authentication state
     * @return list of UserDto objects excluding the current user, ordered by username
     * @throws RuntimeException if the user is not authenticated
     */
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsersExceptCurrent(HttpSession session) {
        Long currentUserId = (Long) session.getAttribute("userId");

        if (currentUserId == null) {
            throw new RuntimeException("User not authenticated");
        }

        return userRepository.findAll().stream()
            .filter(user -> !user.getId().equals(currentUserId))
            .map(UserDto::fromUser)
            .sorted((a, b) -> a.username().compareToIgnoreCase(b.username()))
            .collect(Collectors.toList());
    }
}

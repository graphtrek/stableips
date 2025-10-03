package co.grtk.stableips.service;

import co.grtk.stableips.model.User;
import co.grtk.stableips.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}

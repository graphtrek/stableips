package co.grtk.stableips.controller;

import co.grtk.stableips.model.User;
import co.grtk.stableips.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    void shouldShowLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(view().name("login"));
    }

    @Test
    void shouldLoginUser() throws Exception {
        // Given
        String username = "alice";
        User user = new User(username, "0xWallet123");
        user.setId(1L);

        when(authService.login(eq(username), any(HttpSession.class))).thenReturn(user);

        // When & Then
        mockMvc.perform(post("/login")
                .param("username", username))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/wallet"));

        verify(authService).login(eq(username), any(HttpSession.class));
    }

    @Test
    void shouldRejectEmptyUsername() throws Exception {
        mockMvc.perform(post("/login")
                .param("username", ""))
            .andExpect(status().is4xxClientError()); // GlobalExceptionHandler returns 400 for ValidationException
    }

    @Test
    void shouldLogoutUser() throws Exception {
        mockMvc.perform(post("/logout"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));

        verify(authService).logout(any(HttpSession.class));
    }
}

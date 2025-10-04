package co.grtk.stableips.service;

import co.grtk.stableips.model.User;
import co.grtk.stableips.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetBalance;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private Web3j web3j;

    @Mock
    private XrpWalletService xrpWalletService;

    @Mock
    private SolanaWalletService solanaWalletService;

    @InjectMocks
    private WalletService walletService;

    @Test
    void shouldGenerateNewWallet() {
        // Given
        String username = "alice";

        // When
        Credentials credentials = walletService.generateWallet(username);

        // Then
        assertThat(credentials).isNotNull();
        assertThat(credentials.getAddress()).isNotNull();
        assertThat(credentials.getAddress()).startsWith("0x");
        assertThat(credentials.getAddress()).hasSize(42);
        assertThat(credentials.getEcKeyPair()).isNotNull();
    }

    @Test
    void shouldCreateUserWithWallet() {
        // Given
        String username = "bob";

        when(xrpWalletService.generateWallet()).thenReturn(
            new XrpWalletService.XrpWallet("rN7n7otQDd6FczFgLdllrq4OhiX1zp7n8", "test_secret")
        );

        when(solanaWalletService.generateWallet()).thenReturn(
            new SolanaWalletService.SolanaWallet("DYw8jCTfwHNRJhhmFcbXvVDTqWMEVFBX6Z", "test_solana_private_key")
        );

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        // When
        User user = walletService.createUserWithWallet(username);

        // Then
        assertThat(user.getUsername()).isEqualTo(username);
        assertThat(user.getWalletAddress()).isNotNull();
        assertThat(user.getWalletAddress()).startsWith("0x");
        assertThat(user.getPrivateKey()).isNotNull();
        assertThat(user.getXrpAddress()).isNotNull();
        assertThat(user.getXrpAddress()).startsWith("r");
        assertThat(user.getXrpSecret()).isNotNull();
        assertThat(user.getSolanaPublicKey()).isNotNull();
        assertThat(user.getSolanaPrivateKey()).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldGetEthBalanceForAddress() throws IOException {
        // Given
        String address = "0x1234567890123456789012345678901234567890";
        BigInteger weiBalance = new BigInteger("1000000000000000000"); // 1 ETH

        @SuppressWarnings("unchecked")
        Request<Object, EthGetBalance> mockRequest = (Request<Object, EthGetBalance>) mock(Request.class);
        EthGetBalance mockBalance = mock(EthGetBalance.class);

        when(web3j.ethGetBalance(anyString(), any())).thenReturn((Request) mockRequest);
        when(mockRequest.send()).thenReturn(mockBalance);
        when(mockBalance.getBalance()).thenReturn(weiBalance);

        // When
        BigDecimal balance = walletService.getEthBalance(address);

        // Then
        assertThat(balance).isEqualByComparingTo(new BigDecimal("1.0"));
        verify(web3j).ethGetBalance(eq(address), any());
    }

    @Test
    void shouldReturnZeroWhenBalanceCheckFails() throws IOException {
        // Given
        String address = "0x1234567890123456789012345678901234567890";

        @SuppressWarnings("unchecked")
        Request<Object, EthGetBalance> mockRequest = (Request<Object, EthGetBalance>) mock(Request.class);

        when(web3j.ethGetBalance(anyString(), any())).thenReturn((Request) mockRequest);
        when(mockRequest.send()).thenThrow(new IOException("Network error"));

        // When
        BigDecimal balance = walletService.getEthBalance(address);

        // Then
        assertThat(balance).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldGetUserCredentials() {
        // Given - generate a real wallet and use its credentials
        Credentials testCreds = walletService.generateWallet("test");
        String walletAddress = testCreds.getAddress();
        String privateKey = testCreds.getEcKeyPair().getPrivateKey().toString(16);

        User user = new User("charlie", walletAddress);
        user.setPrivateKey(privateKey);

        when(userRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(user));

        // When
        Credentials credentials = walletService.getUserCredentials(walletAddress);

        // Then
        assertThat(credentials).isNotNull();
        assertThat(credentials.getAddress().toLowerCase()).isEqualTo(walletAddress.toLowerCase());
        assertThat(credentials.getEcKeyPair()).isNotNull();
    }

    @Test
    void shouldThrowExceptionWhenUserNotFoundForCredentials() {
        // Given
        String walletAddress = "0x1234567890123456789012345678901234567890";
        when(userRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> walletService.getUserCredentials(walletAddress))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("User not found");
    }
}

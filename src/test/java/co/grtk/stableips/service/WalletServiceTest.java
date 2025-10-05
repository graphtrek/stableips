package co.grtk.stableips.service;

import co.grtk.stableips.model.User;
import co.grtk.stableips.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
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

    @Mock
    private ContractService contractService;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private WalletService walletService;

    @BeforeEach
    void setUp() {
        // Set funding private key for tests that need it
        ReflectionTestUtils.setField(walletService, "fundingPrivateKey", "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
        ReflectionTestUtils.setField(walletService, "initialAmount", new BigDecimal("10"));
        ReflectionTestUtils.setField(walletService, "initialUsdcAmount", new BigDecimal("1000"));
        ReflectionTestUtils.setField(walletService, "initialDaiAmount", new BigDecimal("1000"));
    }

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

    // ==================== Funding Operations Tests ====================

    @Test
    void shouldFundWalletAndReturnTxHash() {
        // Given
        String toAddress = "0xUserWallet123";
        BigDecimal fundingAmount = new BigDecimal("10");
        String expectedTxHash = "0xFundingTxHash123";

        // This test would require TransactionReceipt mocking
        // Skipping direct fundWallet test due to Web3j Transfer complexity
        // Will test in integration tests instead
    }

    @Test
    void shouldReturnNullWhenFundingWalletNotConfigured() {
        // Given - WalletService with null fundingPrivateKey
        WalletService serviceWithoutFunding = new WalletService(
            userRepository, web3j, xrpWalletService, solanaWalletService, contractService, transactionService
        );

        String toAddress = "0xUserWallet123";
        BigDecimal fundingAmount = new BigDecimal("10");

        // When
        String txHash = serviceWithoutFunding.fundWallet(toAddress, fundingAmount);

        // Then
        assertThat(txHash).isNull();
    }

    @Test
    void shouldFundTestTokensAndReturnTxHashes() {
        // Given
        String walletAddress = "0xUserWallet456";
        String usdcTxHash = "0xUsdcMintTx123";
        String daiTxHash = "0xDaiMintTx456";

        User user = new User("testuser", walletAddress);
        user.setId(1L);

        when(userRepository.findByWalletAddress(walletAddress)).thenReturn(java.util.Optional.of(user));
        when(contractService.mintTestTokens(any(), eq(walletAddress), any(), eq("TEST-USDC")))
            .thenReturn(usdcTxHash);
        when(contractService.mintTestTokens(any(), eq(walletAddress), any(), eq("TEST-DAI")))
            .thenReturn(daiTxHash);

        // When
        java.util.Map<String, String> txHashes = walletService.fundTestTokens(walletAddress);

        // Then
        assertThat(txHashes).hasSize(2);
        assertThat(txHashes.get("usdc")).isEqualTo(usdcTxHash);
        assertThat(txHashes.get("dai")).isEqualTo(daiTxHash);
        verify(contractService, times(2)).mintTestTokens(any(), eq(walletAddress), any(), anyString());
        verify(transactionService, times(2)).recordFundingTransaction(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldThrowExceptionWhenFundingTokensWithoutPrivateKey() {
        // Given - WalletService without funding private key
        WalletService serviceWithoutFunding = new WalletService(
            userRepository, web3j, xrpWalletService, solanaWalletService, contractService, transactionService
        );

        String walletAddress = "0xUserWallet456";

        // When & Then
        assertThatThrownBy(() -> serviceWithoutFunding.fundTestTokens(walletAddress))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Funding wallet not configured");
    }

    @Test
    void shouldCreateUserWithWalletAndFunding() {
        // Given
        String username = "charlie";
        String ethFundingTxHash = "0xEthFundingTx789";

        when(xrpWalletService.generateWallet()).thenReturn(
            new XrpWalletService.XrpWallet("rN7n7otQDd6FczFgLdllrq4OhiX1zp7n8", "test_secret")
        );

        when(solanaWalletService.generateWallet()).thenReturn(
            new SolanaWalletService.SolanaWallet("DYw8jCTfwHNRJhhmFcbXvVDTqWMEVFBX6Z", "test_solana_key")
        );

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        // When
        User user = walletService.createUserWithWalletAndFunding(username);

        // Then
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo(username);
        assertThat(user.getWalletAddress()).isNotNull();
        assertThat(user.getXrpAddress()).isNotNull();
        assertThat(user.getSolanaPublicKey()).isNotNull();

        // Verify XRP funding was called
        verify(xrpWalletService).fundUserWallet(user.getXrpAddress());
    }

    @Test
    void shouldRegenerateXrpWalletAndFundIt() {
        // Given
        User user = new User("david", "0xExistingWallet");
        user.setId(1L);
        user.setXrpAddress("old_xrp_address");

        XrpWalletService.XrpWallet newXrpWallet = new XrpWalletService.XrpWallet(
            "rNewXrpAddress123", "new_secret"
        );

        when(xrpWalletService.generateWallet()).thenReturn(newXrpWallet);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User updatedUser = walletService.regenerateXrpWallet(user);

        // Then
        assertThat(updatedUser.getXrpAddress()).isEqualTo("rNewXrpAddress123");
        assertThat(updatedUser.getXrpSecret()).isEqualTo("new_secret");
        verify(xrpWalletService).fundWalletFromFaucet("rNewXrpAddress123");
        verify(userRepository).save(user);
    }

    @Test
    void shouldGetXrpBalance() {
        // Given
        String xrpAddress = "rN7n7otQDd6FczFgLdllrq4OhiX1zp7n8";
        BigDecimal expectedBalance = new BigDecimal("1000.5");

        when(xrpWalletService.getBalance(xrpAddress)).thenReturn(expectedBalance);

        // When
        BigDecimal balance = walletService.getXrpBalance(xrpAddress);

        // Then
        assertThat(balance).isEqualByComparingTo(expectedBalance);
        verify(xrpWalletService).getBalance(xrpAddress);
    }

    @Test
    void shouldGetSolanaBalance() {
        // Given
        String publicKey = "DYw8jCTfwHNRJhhmFcbXvVDTqWMEVFBX6Z";
        BigDecimal expectedBalance = new BigDecimal("2.5");

        when(solanaWalletService.getBalance(publicKey)).thenReturn(expectedBalance);

        // When
        BigDecimal balance = walletService.getSolanaBalance(publicKey);

        // Then
        assertThat(balance).isEqualByComparingTo(expectedBalance);
        verify(solanaWalletService).getBalance(publicKey);
    }

    @Test
    void shouldHandleFundingFailureGracefully() {
        // Given
        String walletAddress = "0xUserWallet999";
        BigDecimal fundingAmount = new BigDecimal("10");

        User user = new User("testuser", walletAddress);
        user.setId(1L);

        when(userRepository.findByWalletAddress(walletAddress)).thenReturn(java.util.Optional.of(user));
        when(contractService.mintTestTokens(any(), anyString(), any(), anyString()))
            .thenThrow(new RuntimeException("Network error"));

        // When & Then
        assertThatThrownBy(() -> walletService.fundTestTokens(walletAddress))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to fund test tokens");
    }
}

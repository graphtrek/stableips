package co.grtk.stableips.service;

import co.grtk.stableips.model.User;
import co.grtk.stableips.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

@Service
@Transactional
public class WalletService {

    private final UserRepository userRepository;
    private final Web3j web3j;

    public WalletService(UserRepository userRepository, Web3j web3j) {
        this.userRepository = userRepository;
        this.web3j = web3j;
    }

    public Credentials generateWallet(String username) {
        try {
            ECKeyPair keyPair = Keys.createEcKeyPair();
            return Credentials.create(keyPair);
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException("Failed to generate wallet for user: " + username, e);
        }
    }

    public User createUserWithWallet(String username) {
        Credentials credentials = generateWallet(username);

        User user = new User(username, credentials.getAddress());
        user.setPrivateKey(credentials.getEcKeyPair().getPrivateKey().toString(16));

        return userRepository.save(user);
    }

    public BigDecimal getEthBalance(String address) {
        try {
            BigInteger weiBalance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                .send()
                .getBalance();

            return Convert.fromWei(weiBalance.toString(), Convert.Unit.ETHER);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public Credentials getUserCredentials(String walletAddress) {
        User user = userRepository.findByWalletAddress(walletAddress)
            .orElseThrow(() -> new RuntimeException("User not found for wallet address: " + walletAddress));

        BigInteger privateKey = new BigInteger(user.getPrivateKey(), 16);
        ECKeyPair keyPair = ECKeyPair.create(privateKey);

        return Credentials.create(keyPair);
    }
}

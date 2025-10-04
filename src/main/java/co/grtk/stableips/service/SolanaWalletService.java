package co.grtk.stableips.service;

import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.programs.SystemProgram;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Base64;

/**
 * Service for managing Solana wallets on Devnet
 * Handles wallet generation, balance queries, and faucet funding
 */
@Service
public class SolanaWalletService {

    private final RpcClient solanaClient;

    @Value("${solana.network.url:https://api.devnet.solana.com}")
    private String networkUrl;

    @Value("${solana.faucet.url:https://api.devnet.solana.com}")
    private String faucetUrl;

    @Value("${solana.funding.initial-amount:2}")
    private BigDecimal initialAmount;

    public SolanaWalletService() {
        // Use Devnet cluster for testing
        this.solanaClient = new RpcClient(Cluster.DEVNET);
    }

    /**
     * Generate a new Solana wallet (keypair)
     * Returns wallet with public key (address) and private key
     */
    public SolanaWallet generateWallet() {
        try {
            // Generate new Solana account (keypair)
            Account account = new Account();

            // Get public key (wallet address)
            String publicKey = account.getPublicKey().toBase58();

            // Get private key (secret key) - 64 bytes encoded as base64
            byte[] secretKey = account.getSecretKey();
            String privateKey = Base64.getEncoder().encodeToString(secretKey);

            System.out.println("Generated new Solana wallet: " + publicKey);

            return new SolanaWallet(publicKey, privateKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Solana wallet", e);
        }
    }

    /**
     * Get SOL balance for an address
     * Returns balance in SOL (not lamports)
     */
    public BigDecimal getBalance(String publicKeyString) {
        try {
            // Convert string to PublicKey
            PublicKey publicKey = new PublicKey(publicKeyString);

            // Get balance in lamports (1 SOL = 1,000,000,000 lamports)
            long lamports = solanaClient.getApi().getBalance(publicKey);

            // Convert lamports to SOL
            BigDecimal sol = new BigDecimal(lamports).divide(new BigDecimal("1000000000"));

            System.out.println("SOL balance for " + publicKeyString + ": " + sol + " SOL");
            return sol;
        } catch (RpcException e) {
            System.err.println("Failed to get SOL balance for " + publicKeyString + ": " + e.getMessage());
            return BigDecimal.ZERO;
        } catch (Exception e) {
            System.err.println("Error parsing Solana public key: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Fund a wallet using Solana devnet faucet
     * Requests airdrop of SOL to the specified address
     */
    public String fundWalletFromFaucet(String publicKeyString) {
        try {
            // Convert string to PublicKey
            PublicKey publicKey = new PublicKey(publicKeyString);

            // Request airdrop from devnet faucet
            // Amount in lamports (2 SOL = 2,000,000,000 lamports)
            long amountLamports = initialAmount.multiply(new BigDecimal("1000000000")).longValue();

            System.out.println("Requesting " + initialAmount + " SOL airdrop for " + publicKeyString);

            // Request airdrop using RPC client
            String signature = solanaClient.getApi().requestAirdrop(publicKey, amountLamports);

            if (signature != null && !signature.isEmpty()) {
                System.out.println("SOL airdrop successful. Signature: " + signature);

                // Wait a bit for the airdrop to be processed
                Thread.sleep(2000);

                return signature;
            } else {
                System.err.println("Failed to request SOL airdrop - no signature returned");
                return null;
            }
        } catch (RpcException e) {
            System.err.println("Failed to request SOL airdrop: " + e.getMessage());
            // Devnet faucet can be rate-limited or temporarily unavailable
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            System.err.println("Unexpected error during SOL airdrop: " + e.getMessage());
            return null;
        }
    }

    /**
     * Fund a user wallet from faucet
     * This is the main method called during user creation
     */
    public String fundUserWallet(String publicKey) {
        System.out.println("Funding Solana wallet from devnet faucet: " + publicKey);
        return fundWalletFromFaucet(publicKey);
    }

    /**
     * Send SOL from one address to another
     * @param fromPrivateKey Base64 encoded private key of sender
     * @param toPublicKey Public key (address) of recipient
     * @param amount Amount in SOL (not lamports)
     * @return Transaction signature
     */
    public String sendSol(String fromPrivateKey, String toPublicKey, BigDecimal amount) {
        try {
            // Decode the private key from base64
            byte[] secretKey = Base64.getDecoder().decode(fromPrivateKey);

            // Create Account from secret key
            Account fromAccount = new Account(secretKey);

            // Convert recipient string to PublicKey
            PublicKey toPublicKeyObj = new PublicKey(toPublicKey);

            // Convert SOL to lamports (1 SOL = 1,000,000,000 lamports)
            long lamports = amount.multiply(new BigDecimal("1000000000")).longValue();

            System.out.println("Sending " + amount + " SOL (" + lamports + " lamports) from " +
                fromAccount.getPublicKey().toBase58() + " to " + toPublicKey);

            // Create transfer instruction
            Transaction transaction = new Transaction();
            transaction.addInstruction(
                SystemProgram.transfer(
                    fromAccount.getPublicKey(),
                    toPublicKeyObj,
                    lamports
                )
            );

            // Get recent blockhash
            String recentBlockhash = solanaClient.getApi().getRecentBlockhash();
            transaction.setRecentBlockHash(recentBlockhash);

            // Sign transaction
            transaction.sign(fromAccount);

            // Send transaction
            String signature = solanaClient.getApi().sendTransaction(transaction, fromAccount);

            System.out.println("SOL transfer successful. Signature: " + signature);
            return signature;

        } catch (Exception e) {
            System.err.println("Failed to send SOL: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to send SOL: " + e.getMessage(), e);
        }
    }

    /**
     * Simple DTO for Solana wallet credentials
     */
    public static class SolanaWallet {
        private final String publicKey;  // Address
        private final String privateKey; // Secret key (base64 encoded)

        public SolanaWallet(String publicKey, String privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public String getPrivateKey() {
            return privateKey;
        }
    }
}

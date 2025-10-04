package co.grtk.stableips.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String walletAddress;

    @Column(nullable = false)
    private String privateKey;

    @Column
    private String xrpAddress;

    @Column
    private String xrpSecret;

    @Column
    private String solanaPublicKey;

    @Column
    private String solanaPrivateKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected User() {
        // JPA requires a no-arg constructor
    }

    public User(String username, String walletAddress) {
        this.username = username;
        this.walletAddress = walletAddress;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getWalletAddress() {
        return walletAddress;
    }

    public void setWalletAddress(String walletAddress) {
        this.walletAddress = walletAddress;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getXrpAddress() {
        return xrpAddress;
    }

    public void setXrpAddress(String xrpAddress) {
        this.xrpAddress = xrpAddress;
    }

    public String getXrpSecret() {
        return xrpSecret;
    }

    public void setXrpSecret(String xrpSecret) {
        this.xrpSecret = xrpSecret;
    }

    public String getSolanaPublicKey() {
        return solanaPublicKey;
    }

    public void setSolanaPublicKey(String solanaPublicKey) {
        this.solanaPublicKey = solanaPublicKey;
    }

    public String getSolanaPrivateKey() {
        return solanaPrivateKey;
    }

    public void setSolanaPrivateKey(String solanaPrivateKey) {
        this.solanaPrivateKey = solanaPrivateKey;
    }
}

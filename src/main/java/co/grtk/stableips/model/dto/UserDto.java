package co.grtk.stableips.model.dto;

/**
 * Data Transfer Object for user information in dropdowns and lists.
 *
 * <p>This lightweight DTO is used for populating user selection dropdowns
 * in forms (e.g., transfer recipient selection). It contains only the essential
 * user information needed for display purposes, excluding sensitive data like
 * private keys.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 * UserDto dto = new UserDto(
 *     user.getId(),
 *     user.getUsername(),
 *     user.getWalletAddress(),
 *     user.getXrpAddress(),
 *     user.getSolanaPublicKey()
 * );
 * </pre>
 *
 * @param id the user's unique identifier
 * @param username the user's display name
 * @param walletAddress the user's Ethereum wallet address (for USDC/EURC transfers)
 * @param xrpAddress the user's XRP Ledger address (for XRP transfers)
 * @param solanaPublicKey the user's Solana public key (for SOL transfers)
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
public record UserDto(
    Long id,
    String username,
    String walletAddress,
    String xrpAddress,
    String solanaPublicKey
) {
    /**
     * Factory method to create a UserDto from a User entity.
     *
     * @param user the user entity
     * @return a UserDto containing safe, displayable user information for all blockchain networks
     * @throws IllegalArgumentException if user is null
     */
    public static UserDto fromUser(co.grtk.stableips.model.User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        return new UserDto(
            user.getId(),
            user.getUsername(),
            user.getWalletAddress(),
            user.getXrpAddress(),
            user.getSolanaPublicKey()
        );
    }
}

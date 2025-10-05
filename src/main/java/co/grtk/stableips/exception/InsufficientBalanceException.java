package co.grtk.stableips.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when a wallet has insufficient balance for a transaction.
 *
 * <p>This exception is thrown when attempting to transfer more tokens or cryptocurrency
 * than are available in the wallet, or when gas fees cannot be covered.</p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
public class InsufficientBalanceException extends RuntimeException {

    private final BigDecimal available;
    private final BigDecimal required;
    private final String asset;

    /**
     * Constructs a new insufficient balance exception.
     *
     * @param message the detail message
     * @param available the available balance
     * @param required the required balance
     * @param asset the asset type (e.g., "ETH", "USDC", "DAI")
     */
    public InsufficientBalanceException(String message, BigDecimal available, BigDecimal required, String asset) {
        super(message);
        this.available = available;
        this.required = required;
        this.asset = asset;
    }

    /**
     * Constructs a new insufficient balance exception with simple message.
     *
     * @param message the detail message
     */
    public InsufficientBalanceException(String message) {
        super(message);
        this.available = null;
        this.required = null;
        this.asset = null;
    }

    public BigDecimal getAvailable() {
        return available;
    }

    public BigDecimal getRequired() {
        return required;
    }

    public String getAsset() {
        return asset;
    }
}

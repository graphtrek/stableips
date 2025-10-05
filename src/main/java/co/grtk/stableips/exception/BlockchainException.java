package co.grtk.stableips.exception;

/**
 * Exception thrown when blockchain operations fail.
 *
 * <p>This exception wraps blockchain-specific errors such as transaction failures,
 * gas estimation errors, network timeouts, and smart contract issues.</p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
public class BlockchainException extends RuntimeException {

    private final BlockchainErrorType errorType;

    /**
     * Constructs a new blockchain exception with the specified detail message and error type.
     *
     * @param message the detail message
     * @param errorType the type of blockchain error
     */
    public BlockchainException(String message, BlockchainErrorType errorType) {
        super(message);
        this.errorType = errorType;
    }

    /**
     * Constructs a new blockchain exception with message, error type, and cause.
     *
     * @param message the detail message
     * @param errorType the type of blockchain error
     * @param cause the cause of this exception
     */
    public BlockchainException(String message, BlockchainErrorType errorType, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    /**
     * Constructs a new blockchain exception with simple message.
     *
     * @param message the detail message
     */
    public BlockchainException(String message) {
        super(message);
        this.errorType = BlockchainErrorType.UNKNOWN;
    }

    /**
     * Constructs a new blockchain exception with message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public BlockchainException(String message, Throwable cause) {
        super(message, cause);
        this.errorType = BlockchainErrorType.UNKNOWN;
    }

    public BlockchainErrorType getErrorType() {
        return errorType;
    }

    /**
     * Enumeration of blockchain error types.
     */
    public enum BlockchainErrorType {
        GAS_ESTIMATION_FAILED,
        GAS_PRICE_TOO_HIGH,
        TRANSACTION_REVERTED,
        NONCE_ERROR,
        NETWORK_TIMEOUT,
        INVALID_ADDRESS,
        CONTRACT_ERROR,
        UNKNOWN
    }
}

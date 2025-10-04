package co.grtk.stableips.model;

/**
 * Enum representing the possible states of a blockchain transaction
 */
public enum TransactionStatus {
    /**
     * Transaction has been submitted to the blockchain but not yet confirmed
     */
    PENDING,

    /**
     * Transaction has been confirmed on the blockchain with sufficient confirmations
     */
    CONFIRMED,

    /**
     * Transaction failed on-chain (reverted or rejected)
     */
    FAILED,

    /**
     * Transaction was not confirmed within the monitoring timeout period
     * May indicate the transaction was dropped from the mempool
     */
    TIMEOUT,

    /**
     * Transaction was dropped from the mempool without being mined
     */
    DROPPED;

    /**
     * Check if this status represents a final state (no further monitoring needed)
     */
    public boolean isFinal() {
        return this == CONFIRMED || this == FAILED || this == TIMEOUT || this == DROPPED;
    }

    /**
     * Check if this status represents a successful transaction
     */
    public boolean isSuccess() {
        return this == CONFIRMED;
    }

    /**
     * Check if this status represents a pending state (still being monitored)
     */
    public boolean isPending() {
        return this == PENDING;
    }
}

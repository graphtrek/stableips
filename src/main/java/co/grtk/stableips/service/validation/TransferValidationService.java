package co.grtk.stableips.service.validation;

import co.grtk.stableips.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.crypto.WalletUtils;

import java.math.BigDecimal;

/**
 * Service for validating transfer-related operations.
 *
 * <p>This service centralizes all validation logic for token transfers, including
 * recipient address validation, amount validation, and token/network validation.</p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
@Service
public class TransferValidationService {

    private static final Logger log = LoggerFactory.getLogger(TransferValidationService.class);

    /**
     * Validates transfer amount.
     *
     * <p>Ensures the amount is not null, is greater than zero, and has reasonable precision.</p>
     *
     * @param amount the transfer amount to validate
     * @throws ValidationException if the amount is invalid
     */
    public void validateAmount(BigDecimal amount) {
        if (amount == null) {
            log.warn("Transfer validation failed: amount is null");
            throw new ValidationException("Transfer amount is required");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Transfer validation failed: invalid amount {}", amount);
            throw new ValidationException("Transfer amount must be greater than 0");
        }

        // Check for unreasonable precision (more than 18 decimal places)
        if (amount.scale() > 18) {
            log.warn("Transfer validation failed: excessive precision in amount {}", amount);
            throw new ValidationException("Transfer amount has too many decimal places (max 18)");
        }
    }

    /**
     * Validates recipient address.
     *
     * <p>Ensures the recipient address is not null, not empty, and properly trimmed.</p>
     *
     * @param recipient the recipient address to validate
     * @throws ValidationException if the recipient address is invalid
     */
    public void validateRecipient(String recipient) {
        if (recipient == null || recipient.trim().isEmpty()) {
            log.warn("Transfer validation failed: empty recipient address");
            throw new ValidationException("Recipient address is required");
        }
    }

    /**
     * Validates Ethereum address format.
     *
     * <p>Uses Web3j's WalletUtils to validate Ethereum address checksums and format.</p>
     *
     * @param address the Ethereum address to validate
     * @throws ValidationException if the address format is invalid
     */
    public void validateEthereumAddress(String address) {
        validateRecipient(address); // First check for null/empty

        if (!WalletUtils.isValidAddress(address)) {
            log.warn("Transfer validation failed: invalid Ethereum address format: {}", address);
            throw new ValidationException("Invalid Ethereum address format");
        }
    }

    /**
     * Validates token type.
     *
     * <p>Ensures the token is one of the supported types: USDC, DAI, ETH, XRP, or SOL.</p>
     *
     * @param token the token type to validate
     * @throws ValidationException if the token type is not supported
     */
    public void validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.warn("Transfer validation failed: empty token type");
            throw new ValidationException("Token type is required");
        }

        String normalizedToken = token.trim().toUpperCase();
        if (!normalizedToken.equals("USDC") &&
            !normalizedToken.equals("DAI") &&
            !normalizedToken.equals("ETH") &&
            !normalizedToken.equals("XRP") &&
            !normalizedToken.equals("SOL")) {
            log.warn("Transfer validation failed: unsupported token type: {}", token);
            throw new ValidationException("Unsupported token type: " + token);
        }
    }

    /**
     * Validates a complete transfer request.
     *
     * <p>Performs comprehensive validation including amount, recipient, token type,
     * and address format (for Ethereum-based tokens).</p>
     *
     * @param recipient the recipient address
     * @param amount the transfer amount
     * @param token the token type
     * @throws ValidationException if any validation fails
     */
    public void validateTransferRequest(String recipient, BigDecimal amount, String token) {
        validateAmount(amount);
        validateRecipient(recipient);
        validateToken(token);

        // For Ethereum-based tokens, validate address format
        String normalizedToken = token.trim().toUpperCase();
        if (!normalizedToken.equals("XRP") && !normalizedToken.equals("SOL")) {
            validateEthereumAddress(recipient);
        }
    }
}

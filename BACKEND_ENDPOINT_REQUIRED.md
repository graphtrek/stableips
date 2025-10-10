# Backend Endpoint Required for User Dropdown Feature

## Overview
The wallet dashboard now includes a user dropdown in the transfer form. This requires a backend REST API endpoint to fetch all users.

**Note**: The current `/wallet/users` endpoint returns HTML fragments. For the user dropdown feature to work properly with multi-chain addresses (Ethereum, XRP, Solana), we need a JSON REST API endpoint instead.

## Required Endpoint

### GET /api/users

**Description**: Retrieve all users for populating the transfer recipient dropdown

**Request**:
- Method: `GET`
- Path: `/api/users`
- Authentication: Required (session-based)

**Response**:
- Status: `200 OK`
- Content-Type: `application/json`

**Response Body**:
```json
[
  {
    "id": 1,
    "username": "alice",
    "walletAddress": "0x1234567890abcdef1234567890abcdef12345678",
    "xrpAddress": "rN7n7otQDd6FczFgLdlqtyMVrn3HMfXg9r",
    "solanaPublicKey": "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d"
  },
  {
    "id": 2,
    "username": "bob",
    "walletAddress": "0xabcdef1234567890abcdef1234567890abcdef12",
    "xrpAddress": "rH4KEcG9dEwGwpn6AyoWK9cTPa5XT9WgNg",
    "solanaPublicKey": "7ZqjS4aBFE3TkJvDEZjNqRLFjx3xqz7KXvV8Y4dWnN9L"
  }
]
```

**Important Notes**:
- DO NOT return sensitive fields: `privateKey`, `xrpSecret`, `solanaPrivateKey`, `createdAt`
- Only return: `id`, `username`, `walletAddress`, `xrpAddress`, `solanaPublicKey`
- The endpoint should require authentication
- Consider excluding the current logged-in user from the results (optional enhancement)

## Required Changes to UserDto

The existing `UserDto` in `/src/main/java/co/grtk/stableips/model/dto/UserDto.java` needs to be updated to include XRP and Solana addresses:

```java
public record UserDto(
    Long id,
    String username,
    String walletAddress,
    String xrpAddress,
    String solanaPublicKey
) {
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
```

## Implementation Suggestion

Create a new REST controller (e.g., `UserApiController.java`) in `co.grtk.stableips.controller`:

```java
package co.grtk.stableips.controller;

import co.grtk.stableips.model.User;
import co.grtk.stableips.repository.UserRepository;
import co.grtk.stableips.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class UserApiController {

    private final UserRepository userRepository;
    private final AuthService authService;

    public UserApiController(UserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getAllUsers(HttpSession session) {
        // Verify authentication
        if (!authService.isAuthenticated(session)) {
            return ResponseEntity.status(401).build();
        }

        // Fetch all users and map to DTO (excluding sensitive fields)
        List<UserDto> users = userRepository.findAll().stream()
            .map(user -> new UserDto(
                user.getId(),
                user.getUsername(),
                user.getWalletAddress(),
                user.getXrpAddress(),
                user.getSolanaPublicKey()
            ))
            .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }

    // DTO to exclude sensitive fields
    record UserDto(
        Long id,
        String username,
        String walletAddress,
        String xrpAddress,
        String solanaPublicKey
    ) {}
}
```

## Frontend Integration

The frontend makes this call on page load:

```javascript
fetch('/api/users')
    .then(response => response.json())
    .then(users => {
        // Populate dropdown with users
    });
```

When a user is selected, the frontend automatically populates the correct recipient address based on the selected token type:
- **USDC/EURC** → `walletAddress` (Ethereum)
- **XRP** → `xrpAddress` (XRP Ledger)
- **SOL** → `solanaPublicKey` (Solana)

## Files Modified

- `/Users/Imre/IdeaProjects/grtk/stableips/src/main/jte/wallet/dashboard.jte`

## Testing

After implementing the endpoint:

1. Start the application
2. Log in to the wallet dashboard
3. Navigate to the "Send Tokens" section
4. The "Select User" dropdown should populate with all users
5. Select a user from the dropdown
6. The "Recipient Address" field should auto-populate with the appropriate address
7. Verify that changing the token type updates the recipient address accordingly

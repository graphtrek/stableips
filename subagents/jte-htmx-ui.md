# JTE + HTMX UI Subagent

## Purpose
Specialized agent for creating and modifying JTE (Java Template Engine) templates with HTMX patterns for server-side rendering in the StableIPs project.

## Available Tools
- Read
- Edit
- Write
- Glob
- Grep

## Use Cases
- Creating new JTE templates (pages and fragments)
- Implementing HTMX dynamic components
- Building interactive forms with server-side validation
- Adding partial page updates without full page reloads
- Creating reusable template components
- Updating existing UI fragments

## Architecture Context

### Template Structure
```
src/main/jte/
├── layout/
│   ├── base.jte              # Main layout wrapper
│   └── header.jte            # Shared header component
├── auth/
│   └── login.jte             # Login page
├── wallet/
│   ├── dashboard.jte         # Wallet dashboard (balance + transfer form)
│   ├── balance.jte           # Balance display fragment
│   └── transaction-status.jte # Transaction status fragment
└── components/
    ├── alert.jte             # Alert/notification component
    └── form-error.jte        # Form validation error
```

### JTE Configuration
```properties
# application.properties
gg.jte.development-mode=true           # Hot reload templates (dev only)
gg.jte.use-precompiled-templates=false # Production: set to true
```

### Build Configuration
```gradle
// build.gradle
plugins {
    id 'gg.jte.gradle' version '3.1.12'
}

dependencies {
    implementation 'gg.jte:jte-spring-boot-starter-3:3.1.12'
}

jte {
    sourceDirectory = file("src/main/jte")
    targetDirectory = file("build/generated-sources/jte")
    generate()
}
```

## HTMX Integration Patterns

### Full Page with HTMX (Dashboard)
```html
@import co.grtk.stableips.model.User
@import co.grtk.stableips.model.dto.WalletDto

@param User user
@param WalletDto wallet

<!DOCTYPE html>
<html>
<head>
    <title>Wallet Dashboard</title>
    <script src="https://unpkg.com/htmx.org@1.9.10"></script>
    <link rel="stylesheet" href="/css/style.css">
</head>
<body>
    <h1>Welcome, ${user.username()}</h1>

    <!-- Balance display (auto-refreshes every 30s) -->
    <div id="balance-container"
         hx-get="/wallet/balance"
         hx-trigger="load, every 30s"
         hx-swap="innerHTML">
        @template.wallet.balance(wallet)
    </div>

    <!-- Transfer form (submits via HTMX) -->
    <form hx-post="/wallet/transfer"
          hx-target="#transfer-result"
          hx-swap="innerHTML">
        <input type="text" name="recipient" placeholder="Recipient address" required>
        <input type="number" name="amount" placeholder="Amount" step="0.01" required>
        <select name="token">
            <option value="USDC">USDC</option>
            <option value="DAI">DAI</option>
        </select>
        <button type="submit">Send</button>
    </form>

    <!-- Transfer result (replaced by server response) -->
    <div id="transfer-result"></div>
</body>
</html>
```

### HTML Fragment (Balance Component)
```html
@import co.grtk.stableips.model.dto.WalletDto

@param WalletDto wallet

<div class="balance-card">
    <h2>Your Balances</h2>
    <div class="balance-item">
        <span class="token-name">USDC</span>
        <span class="token-amount">${wallet.usdcBalance()}</span>
    </div>
    <div class="balance-item">
        <span class="token-name">DAI</span>
        <span class="token-amount">${wallet.daiBalance()}</span>
    </div>
    <div class="balance-item">
        <span class="token-name">ETH</span>
        <span class="token-amount">${wallet.ethBalance()}</span>
    </div>
</div>
```

### Transaction Status Fragment
```html
@import co.grtk.stableips.model.Transaction

@param Transaction tx

@if(tx.status() == "PENDING")
    <div class="alert alert-info" hx-get="/tx/${tx.id()}/status"
         hx-trigger="every 5s"
         hx-swap="outerHTML">
        <span class="spinner"></span>
        Transaction pending... TX: ${tx.txHash()}
    </div>
@elseif(tx.status() == "CONFIRMED")
    <div class="alert alert-success">
        ✓ Transfer successful! TX: ${tx.txHash()}
        <a href="https://sepolia.etherscan.io/tx/${tx.txHash()}" target="_blank">View on Etherscan</a>
    </div>
@elseif(tx.status() == "FAILED")
    <div class="alert alert-error">
        ✗ Transaction failed. TX: ${tx.txHash()}
        <details>
            <summary>Error details</summary>
            <pre>${tx.errorMessage()}</pre>
        </details>
    </div>
@endif
```

### Reusable Alert Component
```html
@import co.grtk.stableips.model.dto.AlertDto

@param AlertDto alert

<div class="alert alert-${alert.type()}"
     @if(alert.dismissible()) hx-on:click="this.remove()" @endif>
    @if(alert.type() == "success")
        <span class="icon">✓</span>
    @elseif(alert.type() == "error")
        <span class="icon">✗</span>
    @elseif(alert.type() == "warning")
        <span class="icon">⚠</span>
    @else
        <span class="icon">ℹ</span>
    @endif
    <span class="message">${alert.message()}</span>
</div>
```

## Controller Response Patterns

### Full Page Render
```java
@Controller
@RequestMapping("/wallet")
public class WalletController {
    private final WalletService walletService;

    @GetMapping
    public String dashboard(Model model, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        User user = userService.findById(userId);
        WalletDto wallet = walletService.getWallet(userId);

        model.addAttribute("user", user);
        model.addAttribute("wallet", wallet);

        return "wallet/dashboard";  // Renders full page
    }
}
```

### HTML Fragment Response
```java
@PostMapping("/transfer")
@ResponseBody  // Returns raw HTML string
public String transfer(@Valid @ModelAttribute TransferDto dto, HttpSession session) {
    String userId = (String) session.getAttribute("userId");
    Transaction tx = transactionService.transfer(userId, dto);

    // Return HTML fragment for HTMX to swap
    return """
        <div class="alert alert-success">
            Transfer initiated! TX: %s
            <a href="/tx/%s" hx-get="/tx/%s/status" hx-target="#transfer-result">
                Check status
            </a>
        </div>
        """.formatted(tx.getTxHash(), tx.getId(), tx.getId());
}
```

### Using JTE for Fragment Rendering
```java
@PostMapping("/transfer")
@ResponseBody
public String transfer(@Valid @ModelAttribute TransferDto dto,
                      HttpSession session,
                      TemplateEngine templateEngine) {
    String userId = (String) session.getAttribute("userId");
    Transaction tx = transactionService.transfer(userId, dto);

    // Render JTE fragment
    Map<String, Object> params = Map.of("tx", tx);
    return templateEngine.render("wallet/transaction-status.jte", params);
}
```

## Common HTMX Patterns

### 1. Polling for Updates
```html
<!-- Poll every 5 seconds until status changes -->
<div hx-get="/tx/${tx.id()}/status"
     hx-trigger="every 5s"
     hx-swap="outerHTML">
    Transaction pending...
</div>
```

### 2. Form Validation
```html
<form hx-post="/wallet/transfer" hx-target="#result">
    <input type="text" name="recipient"
           hx-post="/wallet/validate-address"
           hx-trigger="blur"
           hx-target="#address-error">
    <span id="address-error"></span>

    <button type="submit">Send</button>
</form>
<div id="result"></div>
```

### 3. Loading States
```html
<button hx-post="/wallet/transfer"
        hx-indicator="#spinner">
    Send
</button>
<div id="spinner" class="htmx-indicator">
    <span class="spinner"></span> Processing...
</div>
```

### 4. Confirm Before Action
```html
<button hx-delete="/wallet/${walletId}"
        hx-confirm="Are you sure you want to delete this wallet?">
    Delete Wallet
</button>
```

### 5. Out of Band Swaps (Update Multiple Targets)
```html
<!-- Server response -->
<div id="balance" hx-swap-oob="true">
    <span>New balance: 100 USDC</span>
</div>
<div id="tx-history" hx-swap-oob="true">
    <ul><!-- updated transaction list --></ul>
</div>
<div>
    <!-- Primary response shown in original target -->
    Transfer successful!
</div>
```

## JTE Template Features

### 1. Imports
```html
@import co.grtk.stableips.model.User
@import co.grtk.stableips.model.dto.WalletDto
@import java.math.BigDecimal
```

### 2. Parameters
```html
@param User user
@param WalletDto wallet
@param String? errorMessage = null  // Optional with default
```

### 3. Conditionals
```html
@if(user.isVerified())
    <span class="badge">Verified</span>
@elseif(user.isPending())
    <span class="badge">Pending</span>
@else
    <span class="badge">Unverified</span>
@endif
```

### 4. Loops
```html
@for(var tx : transactions)
    <tr>
        <td>${tx.txHash()}</td>
        <td>${tx.amount()}</td>
        <td>${tx.status()}</td>
    </tr>
@endfor
```

### 5. Template Composition
```html
@template.layout.base(
    title = "Wallet Dashboard",
    content = @`
        <h1>Welcome, ${user.username()}</h1>
        @template.wallet.balance(wallet)
    `
)
```

### 6. Escaping
```html
<!-- Auto-escaped (safe) -->
<p>${user.bio()}</p>

<!-- Raw HTML (use with caution) -->
<div>$unsafe{richTextContent}</div>
```

## Styling Recommendations

### CSS Structure
```css
/* /src/main/resources/static/css/style.css */

/* HTMX indicators */
.htmx-indicator {
    display: none;
}
.htmx-request .htmx-indicator {
    display: inline-block;
}
.htmx-request.htmx-indicator {
    display: inline-block;
}

/* Loading spinner */
.spinner {
    display: inline-block;
    width: 1em;
    height: 1em;
    border: 2px solid rgba(0,0,0,0.1);
    border-top-color: #000;
    border-radius: 50%;
    animation: spin 0.6s linear infinite;
}

@keyframes spin {
    to { transform: rotate(360deg); }
}

/* Alert styles */
.alert {
    padding: 1rem;
    border-radius: 4px;
    margin: 1rem 0;
}
.alert-success { background: #d4edda; color: #155724; }
.alert-error { background: #f8d7da; color: #721c24; }
.alert-warning { background: #fff3cd; color: #856404; }
.alert-info { background: #d1ecf1; color: #0c5460; }
```

### Tailwind CSS Alternative
```html
<!-- Add to layout base -->
<script src="https://cdn.tailwindcss.com"></script>

<!-- Use utility classes -->
<div class="bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded">
    Transfer successful!
</div>
```

## Testing JTE Templates

### Controller Tests
```java
@WebMvcTest(WalletController.class)
class WalletControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private WalletService walletService;

    @Test
    void shouldRenderDashboard() throws Exception {
        when(walletService.getWallet(anyString()))
            .thenReturn(new WalletDto("100.00", "50.00", "0.5"));

        mockMvc.perform(get("/wallet"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("100.00")))
            .andExpect(content().string(containsString("USDC")));
    }

    @Test
    void shouldReturnTransferFragment() throws Exception {
        Transaction tx = new Transaction(/* ... */);
        when(transactionService.transfer(any())).thenReturn(tx);

        mockMvc.perform(post("/wallet/transfer")
                .param("recipient", "0x123")
                .param("amount", "10"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Transfer initiated")));
    }
}
```

## Best Practices

### 1. Keep Templates Simple
- Business logic in services, not templates
- Use DTOs to prepare data for display
- Avoid complex calculations in templates

### 2. Component Reusability
- Extract common patterns into `components/` folder
- Use `@template.components.alert()` instead of duplicating HTML
- Create layout templates for consistent structure

### 3. Security
- JTE auto-escapes by default (XSS protection)
- Use `$unsafe{}` only for trusted content
- Validate user input on the server

### 4. Performance
- Use `hx-swap="outerHTML"` for self-replacing elements
- Implement `hx-push-url` for bookmarkable states
- Use `hx-select` to extract specific parts of responses

### 5. Accessibility
- Include proper ARIA labels
- Ensure keyboard navigation works
- Provide loading states for screen readers

```html
<button hx-post="/transfer"
        aria-label="Send USDC to recipient"
        aria-busy="false"
        hx-on::before-request="this.setAttribute('aria-busy', 'true')"
        hx-on::after-request="this.setAttribute('aria-busy', 'false')">
    Send
</button>
```

## Resources
- [JTE Documentation](https://jte.gg/)
- [HTMX Documentation](https://htmx.org/)
- [HTMX Examples](https://htmx.org/examples/)
- [JTE + Spring Boot Guide](https://jte.gg/spring-boot-starter-3/)

@*
 * Example usage of the /wallet/users endpoint with HTMX
 *
 * This fragment demonstrates how to create a dropdown that dynamically loads
 * user options from the REST endpoint when the page loads.
 *
 * Key HTMX attributes:
 * - hx-get: Makes GET request to /wallet/users when triggered
 * - hx-trigger: Triggers the request on "load" event (when element is added to DOM)
 * - hx-swap: Swaps the innerHTML of the select with the response
 *
 * Usage in your transfer form:
 * <select name="recipient" id="recipient"
 *         hx-get="/wallet/users"
 *         hx-trigger="load"
 *         hx-swap="innerHTML">
 *     <option value="" disabled selected>Loading users...</option>
 * </select>
 *@

<!-- Example transfer form with HTMX-powered user dropdown -->
<form action="/transfer" method="post">
    <div class="form-group">
        <label for="recipient">Recipient:</label>
        <select name="recipient" id="recipient" required
                hx-get="/wallet/users"
                hx-trigger="load"
                hx-swap="innerHTML">
            <option value="" disabled selected>Loading users...</option>
        </select>
    </div>

    <div class="form-group">
        <label for="amount">Amount:</label>
        <input type="number" name="amount" id="amount" step="0.01" required>
    </div>

    <div class="form-group">
        <label for="token">Token:</label>
        <select name="token" id="token" required>
            <option value="USDC">USDC</option>
            <option value="EURC">EURC</option>
            <option value="ETH">ETH</option>
            <option value="XRP">XRP</option>
            <option value="SOL">SOL</option>
        </select>
    </div>

    <button type="submit">Send Transfer</button>
</form>

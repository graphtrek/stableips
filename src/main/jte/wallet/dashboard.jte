@import co.grtk.stableips.model.User
@import co.grtk.stableips.model.Transaction
@import java.math.BigDecimal
@import java.util.List

@param User user
@param BigDecimal ethBalance
@param BigDecimal usdcBalance
@param BigDecimal daiBalance
@param BigDecimal xrpBalance
@param BigDecimal solBalance
@param List<Transaction> transactions

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>StableIPs - Wallet Dashboard</title>
    <script src="https://unpkg.com/htmx.org@1.9.10"></script>
    <style>
        body { font-family: Arial, sans-serif; max-width: 1200px; margin: 0 auto; padding: 20px; }
        h1 { color: #2563eb; }
        .wallet-info { background: #f3f4f6; padding: 15px; border-radius: 8px; margin: 20px 0; }
        .balances { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; }
        .balance-card { background: white; padding: 15px; border-radius: 8px; border: 1px solid #e5e7eb; }
        .transfer-form { background: #f9fafb; padding: 20px; border-radius: 8px; margin: 20px 0; }
        input, select { width: 100%; padding: 8px; margin: 5px 0 15px; border: 1px solid #d1d5db; border-radius: 4px; }
        button { background: #2563eb; color: white; padding: 10px 20px; border: none; border-radius: 4px; cursor: pointer; }
        button:hover { background: #1d4ed8; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th, td { text-align: left; padding: 12px; border-bottom: 1px solid #e5e7eb; }
        th { background: #f3f4f6; }
        .status-pending { color: #f59e0b; }
        .status-confirmed { color: #10b981; }
        .status-failed { color: #ef4444; }
        .alert { padding: 12px; border-radius: 4px; margin: 15px 0; }
        .alert-success { background: #d1fae5; color: #065f46; }
        .alert-error { background: #fee2e2; color: #991b1b; }
    </style>
</head>
<body>
    <h1>StableIPs Wallet</h1>
    <p>Welcome, <strong>${user.getUsername()}</strong>!</p>

    <h2>Your Wallets</h2>
    <div class="wallet-info">
        <p><strong>Ethereum:</strong> ${user.getWalletAddress()}</p>
        <p><strong>XRP Ledger:</strong> ${user.getXrpAddress()}</p>
        <p><strong>Solana:</strong> ${user.getSolanaPublicKey()}</p>
    </div>

    <h3>Balances</h3>
    <div class="balances">
        <div class="balance-card">
            <h4>ETH</h4>
            <p>${ethBalance} ETH</p>
        </div>
        <div class="balance-card">
            <h4>XRP</h4>
            <p>${xrpBalance} XRP</p>
        </div>
        <div class="balance-card">
            <h4>SOL</h4>
            <p>${solBalance} SOL</p>
        </div>
        <div class="balance-card">
            <h4>USDC</h4>
            <p>${usdcBalance} USDC</p>
        </div>
        <div class="balance-card">
            <h4>DAI</h4>
            <p>${daiBalance} DAI</p>
        </div>
    </div>

    <div style="margin: 20px 0;">
        <h3>Get Test Tokens</h3>
        <div style="display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 10px;">
            <button
                hx-post="/wallet/fund"
                hx-target="#fund-result"
                hx-swap="innerHTML"
                style="background: #059669;">
                💰 Fund Test USDC/DAI
            </button>
            <button
                onclick="window.open('https://cloud.google.com/application/web3/faucet/ethereum/sepolia', '_blank')"
                style="background: #6366f1;">
                🔷 Fund ETH
            </button>
            <button
                onclick="window.open('https://faucet.circle.com/', '_blank')"
                style="background: #2563eb;">
                💵 Fund USDC (Circle)
            </button>
            <button
                onclick="window.open('https://faucet.solana.com/', '_blank')"
                style="background: #9945ff;">
                ☀️ Fund SOL
            </button>
            <button
                onclick="window.open('https://faucet.altnet.rippletest.net/', '_blank')"
                style="background: #0085ff;">
                💧 Fund XRP
            </button>
        </div>
        <div id="fund-result" style="margin-top: 10px;"></div>
    </div>

    <h3>Send Tokens</h3>
    <div class="transfer-form">
        <form method="post" action="/transfer">
            <label for="recipient">Recipient Address:</label>
            <input type="text" id="recipient" name="recipient" placeholder="0x..." required>

            <label for="amount">Amount:</label>
            <input type="number" id="amount" name="amount" step="0.01" min="0.01" required>

            <label for="token">Token:</label>
            <select id="token" name="token" required>
                <option value="SOL">SOL (Solana)</option>
                <option value="XRP">XRP</option>
                <option value="USDC">USDC</option>
                <option value="DAI">DAI</option>
            </select>

            <button type="submit">Send Transfer</button>
        </form>
    </div>

    <h3>Recent Transactions</h3>
    @if(transactions.isEmpty())
        <p>No transactions yet.</p>
    @else
        <table>
            <thead>
                <tr>
                    <th>Network</th>
                    <th>Token</th>
                    <th>Recipient</th>
                    <th>Amount</th>
                    <th>Status</th>
                    <th>TX Hash</th>
                </tr>
            </thead>
            <tbody>
                @for(Transaction tx : transactions)
                    <tr>
                        <td>${tx.getNetwork()}</td>
                        <td>${tx.getToken()}</td>
                        <td>${tx.getRecipient()}</td>
                        <td>${tx.getAmount()}</td>
                        <td>${tx.getStatus()}</td>
                        <td>${tx.getTxHash().length() > 10 ? tx.getTxHash().substring(0, 10) + "..." : tx.getTxHash()}</td>
                    </tr>
                @endfor
            </tbody>
        </table>
    @endif

    <form method="post" action="/logout">
        <button type="submit">Logout</button>
    </form>
</body>
</html>

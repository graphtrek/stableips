@import co.grtk.stableips.model.User
@import co.grtk.stableips.model.Transaction
@import java.math.BigDecimal
@import java.util.List
@import java.time.format.DateTimeFormatter

@param User user
@param BigDecimal ethBalance
@param BigDecimal usdcBalance
@param BigDecimal daiBalance
@param BigDecimal xrpBalance
@param BigDecimal solBalance
@param List<Transaction> sentTransactions
@param List<Transaction> receivedTransactions
@param List<Transaction> fundingTransactions

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
        .balance-card { padding: 15px; border-radius: 8px; border: 1px solid #e5e7eb; }
        .balance-card-eth { background: #627eea; color: white; }
        .balance-card-xrp { background: #23292f; color: white; }
        .balance-card-sol { background: #9945ff; color: white; }
        .balance-card-usdc { background: #2775ca; color: white; }
        .balance-card-dai { background: #f5ac37; color: white; }
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
        .tx-received { background: #ecfdf5; }
        .tx-sent { background: #fef3f2; }
        .tx-type-badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }
        .badge-received { background: #10b981; color: white; }
        .badge-sent { background: #ef4444; color: white; }
        .badge-funding { background: #8b5cf6; color: white; }
        .badge-minting { background: #06b6d4; color: white; }
        .badge-faucet { background: #f59e0b; color: white; }
        .tx-funding { background: #f5f3ff; }
        .synthetic-hash { color: #9ca3af; font-style: italic; font-size: 11px; }
        .tabs { display: flex; gap: 10px; margin: 20px 0; border-bottom: 2px solid #e5e7eb; }
        .tab { padding: 10px 20px; cursor: pointer; border: none; background: none; font-size: 16px; }
        .tab.active { border-bottom: 3px solid #2563eb; color: #2563eb; font-weight: bold; }
        .tab-content { display: none; }
        .tab-content.active { display: block; }
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
        <div class="balance-card balance-card-eth">
            <h4>ETH</h4>
            <p>${ethBalance} ETH</p>
        </div>
        <div class="balance-card balance-card-xrp">
            <h4>XRP</h4>
            <p>${xrpBalance} XRP</p>
        </div>
        <div class="balance-card balance-card-sol">
            <h4>SOL</h4>
            <p>${solBalance} SOL</p>
        </div>
        <div class="balance-card balance-card-usdc">
            <h4>USDC</h4>
            <p>${usdcBalance} USDC</p>
        </div>
        <div class="balance-card balance-card-dai">
            <h4>DAI</h4>
            <p>${daiBalance} DAI</p>
        </div>
    </div>

    <div style="margin: 20px 0;">
        <h3>Get Test Tokens</h3>
        <div style="display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 10px;">
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

    <h3>Transactions</h3>

    <div class="tabs">
        <button class="tab active" onclick="showTab('all')">All</button>
        <button class="tab" onclick="showTab('received')">Received (${receivedTransactions.size()})</button>
        <button class="tab" onclick="showTab('sent')">Sent (${sentTransactions.size()})</button>
        <button class="tab" onclick="showTab('funding')">Funding (${fundingTransactions.size()})</button>
    </div>

    <div id="tab-all" class="tab-content active">
        @if(sentTransactions.isEmpty() && receivedTransactions.isEmpty() && fundingTransactions.isEmpty())
            <p>No transactions yet.</p>
        @else
            <table>
                <thead>
                    <tr>
                        <th>Type</th>
                        <th>Network</th>
                        <th>Token</th>
                        <th>Address</th>
                        <th>Amount</th>
                        <th>Status</th>
                        <th>TX Hash</th>
                        <th>Time</th>
                    </tr>
                </thead>
                <tbody>
                    @for(Transaction tx : receivedTransactions)
                        <tr class="tx-received">
                            <td><span class="tx-type-badge badge-received">↓ IN</span></td>
                            <td>${tx.getNetwork()}</td>
                            <td>${tx.getToken()}</td>
                            <td>
                                <a href="${tx.getRecipient()}">
                                    @if(tx.getRecipient().length() > 10)${tx.getRecipient().substring(0, 6)}...${tx.getRecipient().substring(tx.getRecipient().length() - 4)}@else${tx.getRecipient()}@endif
                                </a>
                            </td>
                            <td>+${tx.getAmount()}</td>
                            <td class="status-${tx.getStatus().toLowerCase()}">${tx.getStatus()}</td>
                            <td>
                                @if(tx.getTxHash() != null && !tx.getTxHash().isEmpty())
                                    @if(tx.getNetwork().equalsIgnoreCase("ETHEREUM") || tx.getNetwork().equalsIgnoreCase("SEPOLIA"))
                                        <a href="https://sepolia.etherscan.io/tx/${tx.getTxHash()}" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @elseif(tx.getNetwork().equalsIgnoreCase("XRP") || tx.getNetwork().equalsIgnoreCase("XRPL"))
                                        <a href="https://testnet.xrpl.org/transactions/${tx.getTxHash()}" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @elseif(tx.getNetwork().equalsIgnoreCase("SOLANA") || tx.getNetwork().equalsIgnoreCase("SOL"))
                                        <a href="https://explorer.solana.com/tx/${tx.getTxHash()}?cluster=devnet" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @else
                                        ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                    @endif
                                @else
                                    Pending...
                                @endif
                            </td>
                            <td>${tx.getTimestamp().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))}</td>
                        </tr>
                    @endfor
                    @for(Transaction tx : sentTransactions)
                        <tr class="tx-sent">
                            <td><span class="tx-type-badge badge-sent">↑ OUT</span></td>
                            <td>${tx.getNetwork()}</td>
                            <td>${tx.getToken()}</td>
                            <td>
                                <a href="${tx.getRecipient()}">
                                    @if(tx.getRecipient().length() > 10)${tx.getRecipient().substring(0, 6)}...${tx.getRecipient().substring(tx.getRecipient().length() - 4)}@else${tx.getRecipient()}@endif
                                </a>
                            </td>
                            <td>-${tx.getAmount()}</td>
                            <td class="status-${tx.getStatus().toLowerCase()}">${tx.getStatus()}</td>
                            <td>
                                @if(tx.getTxHash() != null && !tx.getTxHash().isEmpty())
                                    @if(tx.getNetwork().equalsIgnoreCase("ETHEREUM") || tx.getNetwork().equalsIgnoreCase("SEPOLIA"))
                                        <a href="https://sepolia.etherscan.io/tx/${tx.getTxHash()}" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @elseif(tx.getNetwork().equalsIgnoreCase("XRP") || tx.getNetwork().equalsIgnoreCase("XRPL"))
                                        <a href="https://testnet.xrpl.org/transactions/${tx.getTxHash()}" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @elseif(tx.getNetwork().equalsIgnoreCase("SOLANA") || tx.getNetwork().equalsIgnoreCase("SOL"))
                                        <a href="https://explorer.solana.com/tx/${tx.getTxHash()}?cluster=devnet" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @else
                                        ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                    @endif
                                @else
                                    Pending...
                                @endif
                            </td>
                            <td>${tx.getTimestamp().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))}</td>
                        </tr>
                    @endfor
                    @for(Transaction tx : fundingTransactions)
                        <tr class="tx-funding">
                            <td>
                                @if(tx.getType().equals("FUNDING"))
                                    <span class="tx-type-badge badge-funding">FUNDING</span>
                                @elseif(tx.getType().equals("MINTING"))
                                    <span class="tx-type-badge badge-minting">MINTING</span>
                                @elseif(tx.getType().equals("FAUCET_FUNDING"))
                                    <span class="tx-type-badge badge-faucet">FAUCET</span>
                                @else
                                    <span class="tx-type-badge badge-funding">${tx.getType()}</span>
                                @endif
                            </td>
                            <td>${tx.getNetwork()}</td>
                            <td>${tx.getToken()}</td>
                            <td>
                                @if(tx.getRecipient().length() > 10)${tx.getRecipient().substring(0, 6)}...${tx.getRecipient().substring(tx.getRecipient().length() - 4)}@else${tx.getRecipient()}@endif
                            </td>
                            <td>+${tx.getAmount()}</td>
                            <td class="status-${tx.getStatus().toLowerCase()}">${tx.getStatus()}</td>
                            <td>
                                @if(tx.getTxHash() != null && !tx.getTxHash().isEmpty())
                                    @if(tx.getTxHash().startsWith("XRP_FAUCET_"))
                                        <span class="synthetic-hash">Faucet Funding</span>
                                    @elseif(tx.getNetwork().equalsIgnoreCase("ETHEREUM") || tx.getNetwork().equalsIgnoreCase("SEPOLIA"))
                                        <a href="https://sepolia.etherscan.io/tx/${tx.getTxHash()}" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @elseif(tx.getNetwork().equalsIgnoreCase("XRP") || tx.getNetwork().equalsIgnoreCase("XRPL"))
                                        <a href="https://testnet.xrpl.org/transactions/${tx.getTxHash()}" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @elseif(tx.getNetwork().equalsIgnoreCase("SOLANA") || tx.getNetwork().equalsIgnoreCase("SOL"))
                                        <a href="https://explorer.solana.com/tx/${tx.getTxHash()}?cluster=devnet" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @else
                                        ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                    @endif
                                @else
                                    Pending...
                                @endif
                            </td>
                            <td>${tx.getTimestamp().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))}</td>
                        </tr>
                    @endfor
                </tbody>
            </table>
        @endif
    </div>

    <div id="tab-received" class="tab-content">
        @if(receivedTransactions.isEmpty())
            <p>No received transactions yet.</p>
        @else
            <table>
                <thead>
                    <tr>
                        <th>Network</th>
                        <th>Token</th>
                        <th>From Address</th>
                        <th>Amount</th>
                        <th>Status</th>
                        <th>TX Hash</th>
                        <th>Time</th>
                    </tr>
                </thead>
                <tbody>
                    @for(Transaction tx : receivedTransactions)
                        <tr class="tx-received">
                            <td>${tx.getNetwork()}</td>
                            <td>${tx.getToken()}</td>
                            <td>
                                <a href="${tx.getRecipient()}">
                                    @if(tx.getRecipient().length() > 10)${tx.getRecipient().substring(0, 6)}...${tx.getRecipient().substring(tx.getRecipient().length() - 4)}@else${tx.getRecipient()}@endif
                                </a>
                            </td>
                            <td><strong>+${tx.getAmount()}</strong></td>
                            <td class="status-${tx.getStatus().toLowerCase()}">${tx.getStatus()}</td>
                            <td>
                                @if(tx.getTxHash() != null && !tx.getTxHash().isEmpty())
                                    @if(tx.getNetwork().equalsIgnoreCase("ETHEREUM") || tx.getNetwork().equalsIgnoreCase("SEPOLIA"))
                                        <a href="https://sepolia.etherscan.io/tx/${tx.getTxHash()}" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @elseif(tx.getNetwork().equalsIgnoreCase("XRP") || tx.getNetwork().equalsIgnoreCase("XRPL"))
                                        <a href="https://testnet.xrpl.org/transactions/${tx.getTxHash()}" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @elseif(tx.getNetwork().equalsIgnoreCase("SOLANA") || tx.getNetwork().equalsIgnoreCase("SOL"))
                                        <a href="https://explorer.solana.com/tx/${tx.getTxHash()}?cluster=devnet" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @else
                                        ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                    @endif
                                @else
                                    Pending...
                                @endif
                            </td>
                            <td>${tx.getTimestamp().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))}</td>
                        </tr>
                    @endfor
                </tbody>
            </table>
        @endif
    </div>

    <div id="tab-sent" class="tab-content">
        @if(sentTransactions.isEmpty())
            <p>No sent transactions yet.</p>
        @else
            <table>
                <thead>
                    <tr>
                        <th>Network</th>
                        <th>Token</th>
                        <th>To Address</th>
                        <th>Amount</th>
                        <th>Status</th>
                        <th>TX Hash</th>
                        <th>Time</th>
                    </tr>
                </thead>
                <tbody>
                    @for(Transaction tx : sentTransactions)
                        <tr class="tx-sent">
                            <td>${tx.getNetwork()}</td>
                            <td>${tx.getToken()}</td>
                            <td>
                                <a href="${tx.getRecipient()}">
                                    @if(tx.getRecipient().length() > 10)${tx.getRecipient().substring(0, 6)}...${tx.getRecipient().substring(tx.getRecipient().length() - 4)}@else${tx.getRecipient()}@endif
                                </a>
                            </td>
                            <td><strong>-${tx.getAmount()}</strong></td>
                            <td class="status-${tx.getStatus().toLowerCase()}">${tx.getStatus()}</td>
                            <td>
                                @if(tx.getTxHash() != null && !tx.getTxHash().isEmpty())
                                    @if(tx.getNetwork().equalsIgnoreCase("ETHEREUM") || tx.getNetwork().equalsIgnoreCase("SEPOLIA"))
                                        <a href="https://sepolia.etherscan.io/tx/${tx.getTxHash()}" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @elseif(tx.getNetwork().equalsIgnoreCase("XRP") || tx.getNetwork().equalsIgnoreCase("XRPL"))
                                        <a href="https://testnet.xrpl.org/transactions/${tx.getTxHash()}" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @elseif(tx.getNetwork().equalsIgnoreCase("SOLANA") || tx.getNetwork().equalsIgnoreCase("SOL"))
                                        <a href="https://explorer.solana.com/tx/${tx.getTxHash()}?cluster=devnet" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @else
                                        ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                    @endif
                                @else
                                    Pending...
                                @endif
                            </td>
                            <td>${tx.getTimestamp().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))}</td>
                        </tr>
                    @endfor
                </tbody>
            </table>
        @endif
    </div>

    <div id="tab-funding" class="tab-content">
        @if(fundingTransactions.isEmpty())
            <p>No funding transactions yet.</p>
        @else
            <table>
                <thead>
                    <tr>
                        <th>Type</th>
                        <th>Network</th>
                        <th>Token</th>
                        <th>Wallet Address</th>
                        <th>Amount</th>
                        <th>Status</th>
                        <th>TX Hash</th>
                        <th>Time</th>
                    </tr>
                </thead>
                <tbody>
                    @for(Transaction tx : fundingTransactions)
                        <tr class="tx-funding">
                            <td>
                                @if(tx.getType().equals("FUNDING"))
                                    <span class="tx-type-badge badge-funding">FUNDING</span>
                                @elseif(tx.getType().equals("MINTING"))
                                    <span class="tx-type-badge badge-minting">MINTING</span>
                                @elseif(tx.getType().equals("FAUCET_FUNDING"))
                                    <span class="tx-type-badge badge-faucet">FAUCET</span>
                                @else
                                    <span class="tx-type-badge badge-funding">${tx.getType()}</span>
                                @endif
                            </td>
                            <td>${tx.getNetwork()}</td>
                            <td><strong>${tx.getToken()}</strong></td>
                            <td>
                                @if(tx.getRecipient().length() > 10)${tx.getRecipient().substring(0, 6)}...${tx.getRecipient().substring(tx.getRecipient().length() - 4)}@else${tx.getRecipient()}@endif
                            </td>
                            <td><strong>+${tx.getAmount()}</strong></td>
                            <td class="status-${tx.getStatus().toLowerCase()}">${tx.getStatus()}</td>
                            <td>
                                @if(tx.getTxHash() != null && !tx.getTxHash().isEmpty())
                                    @if(tx.getTxHash().startsWith("XRP_FAUCET_"))
                                        <span class="synthetic-hash">Faucet Funding</span>
                                    @elseif(tx.getNetwork().equalsIgnoreCase("ETHEREUM") || tx.getNetwork().equalsIgnoreCase("SEPOLIA"))
                                        <a href="https://sepolia.etherscan.io/tx/${tx.getTxHash()}" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @elseif(tx.getNetwork().equalsIgnoreCase("XRP") || tx.getNetwork().equalsIgnoreCase("XRPL"))
                                        <a href="https://testnet.xrpl.org/transactions/${tx.getTxHash()}" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @elseif(tx.getNetwork().equalsIgnoreCase("SOLANA") || tx.getNetwork().equalsIgnoreCase("SOL"))
                                        <a href="https://explorer.solana.com/tx/${tx.getTxHash()}?cluster=devnet" target="_blank">
                                            ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                        </a>
                                    @else
                                        ${tx.getTxHash().substring(0, Math.min(8, tx.getTxHash().length()))}...
                                    @endif
                                @else
                                    Pending...
                                @endif
                            </td>
                            <td>${tx.getTimestamp().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))}</td>
                        </tr>
                    @endfor
                </tbody>
            </table>
        @endif
    </div>

    <script>
        function showTab(tabName) {
            // Hide all tabs
            document.querySelectorAll('.tab-content').forEach(tab => tab.classList.remove('active'));
            document.querySelectorAll('.tab').forEach(tab => tab.classList.remove('active'));

            // Show selected tab
            document.getElementById('tab-' + tabName).classList.add('active');
            event.target.classList.add('active');
        }
    </script>

    <form method="post" action="/logout">
        <button type="submit">Logout</button>
    </form>
</body>
</html>

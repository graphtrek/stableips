@import co.grtk.stableips.model.User
@import co.grtk.stableips.model.Transaction
@import java.math.BigDecimal
@import java.util.List
@import java.time.format.DateTimeFormatter

@param User user
@param BigDecimal ethBalance
@param BigDecimal usdcBalance
@param BigDecimal eurcBalance
@param BigDecimal xrpBalance
@param BigDecimal solBalance
@param List<Transaction> allTransactions
@param List<Transaction> sentTransactions
@param List<Transaction> receivedTransactions
@param List<Transaction> fundingTransactions

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>StableIPS - Wallet Dashboard</title>
    <script src="https://unpkg.com/htmx.org@1.9.10"></script>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px;
            color: #333;
        }

        .dashboard-container {
            max-width: 1400px;
            margin: 0 auto;
            animation: fadeIn 0.6s ease-in-out;
        }

        @keyframes fadeIn {
            from {
                opacity: 0;
                transform: translateY(20px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }

        /* Header Section */
        .header {
            background: white;
            border-radius: 20px;
            padding: 30px 40px;
            margin-bottom: 30px;
            box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 20px;
        }

        .header-left h1 {
            font-size: 2rem;
            font-weight: 800;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
            margin-bottom: 8px;
        }

        .welcome-text {
            color: #6c757d;
            font-size: 1.1rem;
        }

        .welcome-text strong {
            color: #495057;
            font-weight: 600;
        }

        .logout-btn {
            padding: 12px 30px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            border-radius: 10px;
            font-size: 1rem;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.3s ease;
            box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
        }

        .logout-btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
        }

        /* Wallet Addresses Section */
        .wallet-addresses {
            background: white;
            border-radius: 16px;
            padding: 25px 30px;
            margin-bottom: 30px;
            box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
        }

        .wallet-addresses h3 {
            font-size: 1.2rem;
            color: #495057;
            margin-bottom: 15px;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .wallet-addresses h3::before {
            content: '🔐';
            font-size: 1.3rem;
        }

        .address-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 15px;
        }

        .address-item {
            background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
            padding: 12px 16px;
            border-radius: 10px;
            border-left: 4px solid #667eea;
        }

        .address-label {
            font-size: 0.85rem;
            font-weight: 600;
            color: #667eea;
            margin-bottom: 4px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .address-value {
            font-family: 'Courier New', monospace;
            font-size: 0.9rem;
            color: #495057;
            word-break: break-all;
        }

        /* Balance Cards Section */
        .balances-section {
            margin-bottom: 30px;
        }

        .section-title {
            color: white;
            font-size: 1.5rem;
            font-weight: 700;
            margin-bottom: 20px;
            text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
        }

        .balance-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }

        .balance-card {
            background: white;
            border-radius: 16px;
            padding: 25px;
            box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
            transition: all 0.3s ease;
            position: relative;
            overflow: hidden;
        }

        .balance-card::before {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            height: 4px;
            background: linear-gradient(90deg, var(--card-color-1), var(--card-color-2));
        }

        .balance-card:hover {
            transform: translateY(-5px);
            box-shadow: 0 15px 50px rgba(0, 0, 0, 0.3);
        }

        .balance-card-eth {
            --card-color-1: #627eea;
            --card-color-2: #4d63d4;
        }

        .balance-card-usdc {
            --card-color-1: #2775ca;
            --card-color-2: #1e5fa5;
        }

        .balance-card-eurc {
            --card-color-1: #0085ff;
            --card-color-2: #0066cc;
        }

        .balance-card-xrp {
            --card-color-1: #23292f;
            --card-color-2: #000000;
        }

        .balance-card-sol {
            --card-color-1: #9945ff;
            --card-color-2: #7d38d6;
        }

        .balance-header {
            display: flex;
            align-items: center;
            gap: 12px;
            margin-bottom: 20px;
        }

        .balance-icon {
            width: 40px;
            height: 40px;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.3rem;
            background: linear-gradient(135deg, var(--card-color-1), var(--card-color-2));
            color: white;
            font-weight: bold;
        }

        .balance-info h4 {
            font-size: 0.85rem;
            color: #6c757d;
            font-weight: 500;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .balance-network {
            font-size: 0.75rem;
            color: #adb5bd;
        }

        .balance-amount {
            font-size: 2rem;
            font-weight: 700;
            color: #212529;
            margin-bottom: 5px;
        }

        .balance-symbol {
            font-size: 1rem;
            color: #6c757d;
            font-weight: 600;
        }

        /* Action Buttons Section */
        .actions-section {
            background: white;
            border-radius: 16px;
            padding: 25px 30px;
            margin-bottom: 30px;
            box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
        }

        .actions-section h3 {
            font-size: 1.2rem;
            color: #495057;
            margin-bottom: 15px;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .actions-section h3::before {
            content: '⚡';
            font-size: 1.3rem;
        }

        .action-buttons {
            display: flex;
            gap: 15px;
            flex-wrap: wrap;
        }

        .action-btn {
            padding: 12px 24px;
            border: none;
            border-radius: 10px;
            font-size: 0.95rem;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.3s ease;
            box-shadow: 0 4px 10px rgba(0, 0, 0, 0.1);
        }

        .action-btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 15px rgba(0, 0, 0, 0.2);
        }

        .action-btn-primary {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
        }

        .action-btn-secondary {
            background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
            color: white;
        }

        .action-btn-tertiary {
            background: linear-gradient(135deg, #2775ca 0%, #2563eb 100%);
            color: white;
        }

        .action-btn-quaternary {
            background: linear-gradient(135deg, #9945ff 0%, #a855f7 100%);
            color: white;
        }

        .action-btn-quinary {
            background: linear-gradient(135deg, #0085ff 0%, #0066cc 100%);
            color: white;
        }

        .action-btn-eurc {
            background: linear-gradient(135deg, #0085ff 0%, #0066cc 100%);
            color: white;
        }

        /* Transfer Form Section */
        .transfer-section {
            background: white;
            border-radius: 16px;
            padding: 30px 40px;
            margin-bottom: 30px;
            box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
        }

        .transfer-section h3 {
            font-size: 1.3rem;
            color: #495057;
            margin-bottom: 25px;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .transfer-section h3::before {
            content: '💸';
            font-size: 1.4rem;
        }

        .transfer-form {
            display: grid;
            gap: 20px;
        }

        .form-group {
            display: flex;
            flex-direction: column;
            gap: 8px;
        }

        .form-group label {
            font-weight: 600;
            color: #495057;
            font-size: 0.9rem;
        }

        .form-group input,
        .form-group select {
            width: 100%;
            padding: 14px 16px;
            border: 2px solid #e9ecef;
            border-radius: 10px;
            font-size: 1rem;
            transition: all 0.3s ease;
            background: #f8f9fa;
        }

        .form-group input:focus,
        .form-group select:focus {
            outline: none;
            border-color: #667eea;
            background: white;
            box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
        }

        .form-row {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 20px;
        }

        .submit-btn {
            padding: 16px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            border-radius: 10px;
            font-size: 1.1rem;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.3s ease;
            box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
        }

        .submit-btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
        }

        /* Transactions Section */
        .transactions-section {
            background: white;
            border-radius: 16px;
            padding: 30px 40px;
            box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
            margin-bottom: 30px;
        }

        .transactions-section h3 {
            font-size: 1.3rem;
            color: #495057;
            margin-bottom: 25px;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .transactions-section h3::before {
            content: '📊';
            font-size: 1.4rem;
        }

        /* Tabs */
        .tabs {
            display: flex;
            gap: 10px;
            margin-bottom: 25px;
            border-bottom: 2px solid #e9ecef;
            flex-wrap: wrap;
        }

        .tab {
            padding: 12px 24px;
            cursor: pointer;
            border: none;
            background: none;
            font-size: 0.95rem;
            font-weight: 500;
            color: #6c757d;
            border-bottom: 3px solid transparent;
            transition: all 0.3s ease;
        }

        .tab:hover {
            color: #495057;
            background: #f8f9fa;
            border-radius: 10px 10px 0 0;
        }

        .tab.active {
            border-bottom-color: #667eea;
            color: #667eea;
            font-weight: 700;
        }

        .tab-content {
            display: none;
        }

        .tab-content.active {
            display: block;
        }

        /* Transaction Table */
        .transaction-table {
            width: 100%;
            border-collapse: separate;
            border-spacing: 0;
        }

        .transaction-table thead tr {
            background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
        }

        .transaction-table th {
            text-align: left;
            padding: 16px 12px;
            font-weight: 700;
            color: #495057;
            font-size: 0.85rem;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .transaction-table th:first-child {
            border-radius: 10px 0 0 0;
        }

        .transaction-table th:last-child {
            border-radius: 0 10px 0 0;
        }

        .transaction-table td {
            padding: 16px 12px;
            border-bottom: 1px solid #f1f3f5;
            font-size: 0.9rem;
        }

        .transaction-table tbody tr {
            transition: all 0.2s ease;
        }

        .transaction-table tbody tr:hover {
            background: #f8f9fa;
            transform: scale(1.01);
        }

        .tx-received {
            background: linear-gradient(90deg, #d3f9d8 0%, #f8f9fa 100%);
        }

        .tx-sent {
            background: linear-gradient(90deg, #ffe3e3 0%, #f8f9fa 100%);
        }

        .tx-funding {
            background: linear-gradient(90deg, #e7f5ff 0%, #f8f9fa 100%);
        }

        /* Transaction Badges */
        .tx-type-badge {
            display: inline-block;
            padding: 4px 10px;
            border-radius: 6px;
            font-size: 0.75rem;
            font-weight: 700;
            letter-spacing: 0.5px;
            text-transform: uppercase;
        }

        .badge-received {
            background: linear-gradient(135deg, #51cf66 0%, #40c057 100%);
            color: white;
        }

        .badge-sent {
            background: linear-gradient(135deg, #ff6b6b 0%, #fa5252 100%);
            color: white;
        }

        .badge-funding {
            background: linear-gradient(135deg, #845ef7 0%, #7950f2 100%);
            color: white;
        }

        .badge-minting {
            background: linear-gradient(135deg, #22b8cf 0%, #15aabf 100%);
            color: white;
        }

        .badge-faucet {
            background: linear-gradient(135deg, #ff922b 0%, #fd7e14 100%);
            color: white;
        }

        /* Status Colors */
        .status-confirmed {
            color: #40c057;
            font-weight: 700;
        }

        .status-pending {
            color: #fab005;
            font-weight: 700;
        }

        .status-failed {
            color: #fa5252;
            font-weight: 700;
        }

        /* Links */
        .transaction-table a {
            color: #667eea;
            text-decoration: none;
            font-weight: 600;
            transition: all 0.2s ease;
        }

        .transaction-table a:hover {
            color: #764ba2;
            text-decoration: underline;
        }

        /* Empty State */
        .empty-state {
            text-align: center;
            padding: 60px 20px;
            color: #adb5bd;
        }

        .empty-state::before {
            content: '📭';
            display: block;
            font-size: 4rem;
            margin-bottom: 15px;
        }

        .empty-state p {
            font-size: 1.1rem;
            color: #6c757d;
        }

        /* Synthetic Hash */
        .synthetic-hash {
            color: #9ca3af;
            font-style: italic;
            font-size: 0.85rem;
        }

        /* Responsive Design */
        @media (max-width: 1200px) {
            .balance-grid {
                grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            }
        }

        @media (max-width: 968px) {
            .header {
                padding: 25px 30px;
            }

            .header-left h1 {
                font-size: 1.6rem;
            }

            .form-row {
                grid-template-columns: 1fr;
            }

            .balance-grid {
                grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            }

            .transaction-table {
                font-size: 0.85rem;
            }

            .transaction-table th,
            .transaction-table td {
                padding: 12px 8px;
            }
        }

        @media (max-width: 768px) {
            body {
                padding: 10px;
            }

            .header {
                padding: 20px;
            }

            .wallet-addresses,
            .actions-section,
            .transfer-section,
            .transactions-section {
                padding: 20px;
            }

            .balance-grid {
                grid-template-columns: 1fr;
            }

            .address-grid {
                grid-template-columns: 1fr;
            }

            .action-buttons {
                flex-direction: column;
            }

            .action-btn {
                width: 100%;
            }

            /* Make table scrollable on mobile */
            .table-wrapper {
                overflow-x: auto;
            }

            .transaction-table {
                min-width: 600px;
            }
        }

        @media (max-width: 480px) {
            .header-left h1 {
                font-size: 1.4rem;
            }

            .section-title {
                font-size: 1.2rem;
            }

            .balance-amount {
                font-size: 1.6rem;
            }
        }
    </style>
</head>
<body>
    <div class="dashboard-container">
        <!-- Header -->
        <div class="header">
            <div class="header-left">
                <h1>StableIPS Wallet</h1>
                <p class="welcome-text">Welcome back, <strong>${user.getUsername()}</strong>!</p>
            </div>
            <form method="post" action="/logout" style="margin: 0;">
                <button type="submit" class="logout-btn">Logout</button>
            </form>
        </div>

        <!-- Wallet Addresses -->
        <div class="wallet-addresses">
            <h3>Your Wallet Addresses</h3>
            <div class="address-grid">
                <div class="address-item">
                    <div class="address-label">Ethereum (Sepolia)</div>
                    <div class="address-value">${user.getWalletAddress()}</div>
                </div>
                <div class="address-item">
                    <div class="address-label">XRP Ledger (Testnet)</div>
                    <div class="address-value">${user.getXrpAddress()}</div>
                </div>
                <div class="address-item">
                    <div class="address-label">Solana (Devnet)</div>
                    <div class="address-value">${user.getSolanaPublicKey()}</div>
                </div>
            </div>
        </div>

        <!-- Balance Cards -->
        <div class="balances-section">
            <h2 class="section-title">Your Balances</h2>
            <div class="balance-grid">
                <div class="balance-card balance-card-eth">
                    <div class="balance-header">
                        <div class="balance-icon">Ξ</div>
                        <div class="balance-info">
                            <h4>Ethereum</h4>
                            <div class="balance-network">Sepolia Testnet</div>
                        </div>
                    </div>
                    <div class="balance-amount">${ethBalance}</div>
                    <div class="balance-symbol">ETH</div>
                </div>

                <div class="balance-card balance-card-usdc">
                    <div class="balance-header">
                        <div class="balance-icon">$</div>
                        <div class="balance-info">
                            <h4>USD Coin</h4>
                            <div class="balance-network">Sepolia Testnet</div>
                        </div>
                    </div>
                    <div class="balance-amount">${usdcBalance}</div>
                    <div class="balance-symbol">USDC</div>
                </div>

                <div class="balance-card balance-card-eurc">
                    <div class="balance-header">
                        <div class="balance-icon">€</div>
                        <div class="balance-info">
                            <h4>Euro Coin</h4>
                            <div class="balance-network">Sepolia Testnet</div>
                        </div>
                    </div>
                    <div class="balance-amount">${eurcBalance}</div>
                    <div class="balance-symbol">EURC</div>
                </div>

                <div class="balance-card balance-card-xrp">
                    <div class="balance-header">
                        <div class="balance-icon">✦</div>
                        <div class="balance-info">
                            <h4>XRP</h4>
                            <div class="balance-network">XRP Ledger Testnet</div>
                        </div>
                    </div>
                    <div class="balance-amount">${xrpBalance}</div>
                    <div class="balance-symbol">XRP</div>
                </div>

                <div class="balance-card balance-card-sol">
                    <div class="balance-header">
                        <div class="balance-icon">◎</div>
                        <div class="balance-info">
                            <h4>Solana</h4>
                            <div class="balance-network">Devnet</div>
                        </div>
                    </div>
                    <div class="balance-amount">${solBalance}</div>
                    <div class="balance-symbol">SOL</div>
                </div>
            </div>
        </div>

        <!-- Action Buttons -->
        <div class="actions-section">
            <h3>Get Test Tokens</h3>
            <div class="action-buttons">
                <button
                    onclick="window.open('https://cloud.google.com/application/web3/faucet/ethereum/sepolia', '_blank')"
                    class="action-btn action-btn-secondary">
                    🔷 Fund ETH
                </button>
                <button
                    onclick="window.open('https://faucet.circle.com/', '_blank')"
                    class="action-btn action-btn-tertiary">
                    💵 Fund USDC
                </button>
                <button
                    onclick="window.open('https://faucet.circle.com/', '_blank')"
                    class="action-btn action-btn-eurc">
                    € Fund EURC
                </button>
                <button
                    onclick="window.open('https://faucet.solana.com/', '_blank')"
                    class="action-btn action-btn-quaternary">
                    ☀️ Fund SOL
                </button>
                <button
                    onclick="window.open('https://faucet.altnet.rippletest.net/', '_blank')"
                    class="action-btn action-btn-quinary">
                    💧 Fund XRP
                </button>
            </div>
        </div>

        <!-- Transfer Form -->
        <div class="transfer-section">
            <h3>Send Tokens</h3>
            <form method="post" action="/transfer" class="transfer-form">
                <div class="form-row">
                    <div class="form-group">
                        <label for="user-select">Select User (Optional)</label>
                        <select id="user-select" class="user-select" aria-label="Select recipient user">
                            <option value="">-- Select a user or enter address manually --</option>
                        </select>
                    </div>

                    <div class="form-group">
                        <label for="recipient">Recipient Address</label>
                        <input type="text" id="recipient" name="recipient" placeholder="0x... or wallet address" required aria-describedby="recipient-help">
                    </div>
                </div>

                <small id="recipient-help" style="color: #6c757d; font-size: 0.85rem; margin-bottom: 10px; display: block;">
                    Select a user from the dropdown above or enter an address manually
                </small>

                <div class="form-row">
                    <div class="form-group">
                        <label for="amount">Amount</label>
                        <input type="number" id="amount" name="amount" step="0.01" min="0.01" placeholder="0.00" required>
                    </div>

                    <div class="form-group">
                        <label for="token">Token</label>
                        <select id="token" name="token" required>
                            <option value="SOL">SOL (Solana)</option>
                            <option value="XRP">XRP (XRP Ledger)</option>
                            <option value="USDC">USDC (Ethereum)</option>
                            <option value="EURC">EURC (Ethereum)</option>
                        </select>
                    </div>
                </div>

                <button type="submit" class="submit-btn">Send Transfer</button>
            </form>
        </div>

        <!-- Transactions -->
        <div class="transactions-section">
            <h3>Transaction History</h3>

            <div class="tabs">
                <button class="tab active" onclick="showTab('all')">All Transactions</button>
                <button class="tab" onclick="showTab('received')">Received (${receivedTransactions.size()})</button>
                <button class="tab" onclick="showTab('sent')">Sent (${sentTransactions.size()})</button>
                <button class="tab" onclick="showTab('funding')">Funding (${fundingTransactions.size()})</button>
            </div>

            <!-- All Transactions Tab -->
            <div id="tab-all" class="tab-content active">
                @if(allTransactions.isEmpty())
                    <div class="empty-state">
                        <p>No transactions yet. Start by funding your wallet or sending tokens!</p>
                    </div>
                @else
                    <div class="table-wrapper">
                        <table class="transaction-table">
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
                                @for(Transaction tx : allTransactions)
                                    @if(fundingTransactions.contains(tx))
                                        <tr class="tx-funding">
                                            <td>
                                                @if(tx.getType().equals("FUNDING"))
                                                    <span class="tx-type-badge badge-funding">FUNDING</span>
                                                @elseif(tx.getType().equals("MINTING"))
                                                    <span class="tx-type-badge badge-minting">MINTING</span>
                                                @elseif(tx.getType().equals("FAUCET_FUNDING"))
                                                    <span class="tx-type-badge badge-faucet">FAUCET</span>
                                                @elseif(tx.getType().equals("EXTERNAL_FUNDING"))
                                                    <span class="tx-type-badge badge-funding">EXTERNAL</span>
                                                @else
                                                    <span class="tx-type-badge badge-funding">${tx.getType()}</span>
                                                @endif
                                            </td>
                                            <td>${tx.getNetwork()}</td>
                                            <td>${tx.getToken()}</td>
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
                                    @elseif(sentTransactions.contains(tx))
                                        <tr class="tx-sent">
                                            <td><span class="tx-type-badge badge-sent">↑ OUT</span></td>
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
                                    @else
                                        <tr class="tx-received">
                                            <td><span class="tx-type-badge badge-received">↓ IN</span></td>
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
                                    @endif
                                @endfor
                            </tbody>
                        </table>
                    </div>
                @endif
            </div>

            <!-- Received Tab -->
            <div id="tab-received" class="tab-content">
                @if(receivedTransactions.isEmpty())
                    <div class="empty-state">
                        <p>No received transactions yet.</p>
                    </div>
                @else
                    <div class="table-wrapper">
                        <table class="transaction-table">
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
                    </div>
                @endif
            </div>

            <!-- Sent Tab -->
            <div id="tab-sent" class="tab-content">
                @if(sentTransactions.isEmpty())
                    <div class="empty-state">
                        <p>No sent transactions yet.</p>
                    </div>
                @else
                    <div class="table-wrapper">
                        <table class="transaction-table">
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
                    </div>
                @endif
            </div>

            <!-- Funding Tab -->
            <div id="tab-funding" class="tab-content">
                @if(fundingTransactions.isEmpty())
                    <div class="empty-state">
                        <p>No funding transactions yet.</p>
                    </div>
                @else
                    <div class="table-wrapper">
                        <table class="transaction-table">
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
                    </div>
                @endif
            </div>
        </div>
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

        // Load users for the dropdown on page load
        document.addEventListener('DOMContentLoaded', function() {
            loadUsers();
            setupUserSelection();
        });

        // Fetch users from backend and populate the dropdown
        function loadUsers() {
            fetch('/api/users')
                .then(response => {
                    if (!response.ok) {
                        throw new Error('Failed to fetch users');
                    }
                    return response.json();
                })
                .then(users => {
                    const userSelect = document.getElementById('user-select');

                    // Clear existing options except the first placeholder
                    while (userSelect.options.length > 1) {
                        userSelect.remove(1);
                    }

                    // Add user options
                    users.forEach(user => {
                        const option = document.createElement('option');
                        option.value = JSON.stringify({
                            username: user.username,
                            walletAddress: user.walletAddress,
                            xrpAddress: user.xrpAddress,
                            solanaPublicKey: user.solanaPublicKey
                        });
                        option.textContent = user.username + ' (' + truncateAddress(user.walletAddress) + ')';
                        userSelect.appendChild(option);
                    });
                })
                .catch(error => {
                    console.error('Error loading users:', error);
                    // Silently fail - user can still enter address manually
                });
        }

        // Truncate wallet address for display
        function truncateAddress(address) {
            if (!address || address.length <= 10) return address;
            return address.substring(0, 6) + '...' + address.substring(address.length - 4);
        }

        // Setup user selection handler
        function setupUserSelection() {
            const userSelect = document.getElementById('user-select');
            const recipientInput = document.getElementById('recipient');
            const tokenSelect = document.getElementById('token');

            userSelect.addEventListener('change', function() {
                if (this.value) {
                    const userData = JSON.parse(this.value);
                    const selectedToken = tokenSelect.value;

                    // Set the appropriate address based on selected token
                    if (selectedToken === 'SOL') {
                        recipientInput.value = userData.solanaPublicKey || '';
                    } else if (selectedToken === 'XRP') {
                        recipientInput.value = userData.xrpAddress || '';
                    } else {
                        // USDC and EURC use Ethereum address
                        recipientInput.value = userData.walletAddress || '';
                    }
                } else {
                    // Clear recipient if no user selected
                    recipientInput.value = '';
                }
            });

            // Update recipient address when token changes and a user is selected
            tokenSelect.addEventListener('change', function() {
                const selectedUserValue = userSelect.value;
                if (selectedUserValue) {
                    const userData = JSON.parse(selectedUserValue);
                    const selectedToken = this.value;

                    if (selectedToken === 'SOL') {
                        recipientInput.value = userData.solanaPublicKey || '';
                    } else if (selectedToken === 'XRP') {
                        recipientInput.value = userData.xrpAddress || '';
                    } else {
                        recipientInput.value = userData.walletAddress || '';
                    }
                }
            });
        }
    </script>
</body>
</html>

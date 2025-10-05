<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>StableIPS - Multi-Chain Wallet Demo</title>
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
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
            color: #333;
        }

        .container {
            max-width: 1200px;
            width: 100%;
            background: white;
            border-radius: 20px;
            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
            overflow: hidden;
            display: grid;
            grid-template-columns: 1fr 1fr;
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

        /* Login Section */
        .login-section {
            padding: 60px 50px;
            display: flex;
            flex-direction: column;
            justify-content: center;
            background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
        }

        .brand {
            margin-bottom: 40px;
        }

        .brand h1 {
            font-size: 2.5rem;
            font-weight: 800;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
            margin-bottom: 8px;
        }

        .brand .tagline {
            font-size: 1rem;
            color: #6c757d;
            font-weight: 500;
        }

        .login-form {
            background: white;
            padding: 40px;
            border-radius: 16px;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
        }

        .login-form h2 {
            font-size: 1.8rem;
            margin-bottom: 12px;
            color: #212529;
        }

        .login-form .subtitle {
            color: #6c757d;
            margin-bottom: 30px;
            font-size: 0.95rem;
        }

        .form-group {
            margin-bottom: 24px;
        }

        .form-group label {
            display: block;
            margin-bottom: 8px;
            font-weight: 600;
            color: #495057;
            font-size: 0.9rem;
        }

        .form-group input {
            width: 100%;
            padding: 14px 16px;
            border: 2px solid #e9ecef;
            border-radius: 10px;
            font-size: 1rem;
            transition: all 0.3s ease;
            background: #f8f9fa;
        }

        .form-group input:focus {
            outline: none;
            border-color: #667eea;
            background: white;
            box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
        }

        .demo-hint {
            background: #e7f1ff;
            border-left: 4px solid #667eea;
            padding: 12px 16px;
            margin-bottom: 24px;
            border-radius: 8px;
            font-size: 0.9rem;
            color: #495057;
        }

        .demo-hint strong {
            color: #667eea;
        }

        .btn-login {
            width: 100%;
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

        .btn-login:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
        }

        .btn-login:active {
            transform: translateY(0);
        }

        /* Info Section */
        .info-section {
            padding: 60px 50px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            overflow-y: auto;
            max-height: 100vh;
        }

        .info-section h3 {
            font-size: 1.5rem;
            margin-bottom: 20px;
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .info-section h3::before {
            content: '';
            width: 40px;
            height: 4px;
            background: rgba(255, 255, 255, 0.5);
            border-radius: 2px;
        }

        .info-section p {
            line-height: 1.7;
            margin-bottom: 16px;
            opacity: 0.95;
        }

        .features {
            margin: 30px 0;
        }

        .feature-item {
            background: rgba(255, 255, 255, 0.1);
            padding: 16px 20px;
            border-radius: 12px;
            margin-bottom: 12px;
            backdrop-filter: blur(10px);
            border: 1px solid rgba(255, 255, 255, 0.2);
            transition: all 0.3s ease;
        }

        .feature-item:hover {
            background: rgba(255, 255, 255, 0.15);
            transform: translateX(5px);
        }

        .feature-item strong {
            display: block;
            margin-bottom: 6px;
            font-size: 1.05rem;
        }

        .feature-item span {
            opacity: 0.9;
            font-size: 0.95rem;
        }

        .agents-section {
            margin-top: 40px;
            padding-top: 40px;
            border-top: 2px solid rgba(255, 255, 255, 0.2);
        }

        .agent-grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 12px;
            margin-top: 20px;
        }

        .agent-card {
            background: rgba(255, 255, 255, 0.1);
            padding: 16px;
            border-radius: 10px;
            backdrop-filter: blur(10px);
            border: 1px solid rgba(255, 255, 255, 0.2);
            transition: all 0.3s ease;
        }

        .agent-card:hover {
            background: rgba(255, 255, 255, 0.15);
            transform: scale(1.05);
        }

        .agent-card .agent-icon {
            font-size: 1.5rem;
            margin-bottom: 8px;
        }

        .agent-card .agent-name {
            font-weight: 700;
            font-size: 0.9rem;
            margin-bottom: 6px;
            display: block;
        }

        .agent-card .agent-role {
            font-size: 0.85rem;
            opacity: 0.9;
        }

        .workflow-note {
            margin-top: 20px;
            padding: 16px;
            background: rgba(255, 255, 255, 0.15);
            border-radius: 10px;
            font-size: 0.9rem;
            font-style: italic;
            border-left: 4px solid rgba(255, 255, 255, 0.5);
        }

        /* Responsive Design */
        @media (max-width: 968px) {
            .container {
                grid-template-columns: 1fr;
                max-width: 600px;
            }

            .info-section {
                max-height: none;
            }

            .agent-grid {
                grid-template-columns: 1fr;
            }

            .login-section, .info-section {
                padding: 40px 30px;
            }

            .brand h1 {
                font-size: 2rem;
            }
        }

        @media (max-width: 480px) {
            body {
                padding: 10px;
            }

            .login-section, .info-section {
                padding: 30px 20px;
            }

            .login-form {
                padding: 30px 20px;
            }

            .brand h1 {
                font-size: 1.8rem;
            }
        }
    </style>
</head>
<body>
    <div class="container">
        <!-- Login Section -->
        <div class="login-section">
            <div class="brand">
                <h1>StableIPS</h1>
                <p class="tagline">Multi-Chain Wallet Demo</p>
            </div>

            <div class="login-form">
                <h2>Welcome Back</h2>
                <p class="subtitle">Sign in to explore multi-blockchain transfers</p>

                <form method="post" action="/login">
                    <div class="demo-hint">
                        <strong>Demo Access:</strong> Try <code>stableips1</code> or <code>stableips2</code> to get started
                    </div>

                    <div class="form-group">
                        <label for="username">Username</label>
                        <input
                            type="text"
                            id="username"
                            name="username"
                            placeholder="Enter your username"
                            required
                            autofocus
                        >
                    </div>

                    <button type="submit" class="btn-login">
                        Sign In
                    </button>
                </form>
            </div>
        </div>

        <!-- Info Section -->
        <div class="info-section">
            <h3>About StableIPS</h3>
            <p>
                StableIPS is a comprehensive multi-blockchain wallet demo application designed for developers
                and blockchain enthusiasts exploring cross-chain stablecoin transfers.
            </p>
            <p>
                Transfer stablecoins (USDC, DAI) and native tokens seamlessly across multiple blockchain networks
                including Ethereum Sepolia, XRP Ledger Testnet, and Solana Devnet.
            </p>

            <div class="features">
                <div class="feature-item">
                    <strong>Multi-Chain Support</strong>
                    <span>Ethereum, XRP Ledger, and Solana integration</span>
                </div>
                <div class="feature-item">
                    <strong>Test Token Funding</strong>
                    <span>Automatic test token distribution for demos</span>
                </div>
                <div class="feature-item">
                    <strong>Transaction History</strong>
                    <span>Real-time monitoring and comprehensive logging</span>
                </div>
                <div class="feature-item">
                    <strong>Developer-Friendly</strong>
                    <span>Built with Spring Boot, JTE, and HTMX</span>
                </div>
            </div>

            <div class="agents-section">
                <h3>Built by Claude Code Team</h3>
                <p>
                    This application was developed using a specialized multi-agent workflow, where each Claude Code
                    agent focuses on their area of expertise to deliver production-quality code.
                </p>

                <div class="agent-grid">
                    <div class="agent-card">
                        <div class="agent-icon">🧪</div>
                        <span class="agent-name">test-coverage-enforcer</span>
                        <div class="agent-role">Writes tests first (TDD approach)</div>
                    </div>
                    <div class="agent-card">
                        <div class="agent-icon">⛓️</div>
                        <span class="agent-name">web3j-blockchain-specialist</span>
                        <div class="agent-role">Blockchain integration & Web3j</div>
                    </div>
                    <div class="agent-card">
                        <div class="agent-icon">⚙️</div>
                        <span class="agent-name">spring-backend-expert</span>
                        <div class="agent-role">Spring Boot backend services</div>
                    </div>
                    <div class="agent-card">
                        <div class="agent-icon">🎨</div>
                        <span class="agent-name">frontend-ui-specialist</span>
                        <div class="agent-role">UI/UX & JTE templates</div>
                    </div>
                    <div class="agent-card">
                        <div class="agent-icon">🔧</div>
                        <span class="agent-name">ci-test-fixer</span>
                        <div class="agent-role">Automated test failure resolution</div>
                    </div>
                    <div class="agent-card">
                        <div class="agent-icon">✨</div>
                        <span class="agent-name">clean-code-enforcer</span>
                        <div class="agent-role">Code quality & JavaDoc coverage</div>
                    </div>
                </div>

                <div class="workflow-note">
                    Each agent collaborates seamlessly, ensuring comprehensive test coverage, clean architecture,
                    and robust blockchain integrations. This team-based approach delivers enterprise-grade code
                    while maintaining development velocity.
                </div>
            </div>
        </div>
    </div>
</body>
</html>

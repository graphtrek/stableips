#!/bin/bash

# Automated Test Token Deployment Script for Sepolia Testnet
# Uses Hardhat for one-command deployment

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================"
echo "StableIPs Test Token Deployment"
echo "========================================"
echo ""

# Check prerequisites
if ! command -v npx &> /dev/null; then
    echo "❌ Error: npx is not installed."
    echo "Please install Node.js from: https://nodejs.org/"
    exit 1
fi

# Check environment variables
if [ -z "$INFURA_PROJECT_ID" ]; then
    echo "❌ Error: INFURA_PROJECT_ID is not set."
    echo "Get your API key from: https://infura.io/"
    echo "Then run: export INFURA_PROJECT_ID=your_project_id"
    exit 1
fi

if [ -z "$FUNDED_SEPOLIA_WALLET_PRIVATE_KEY" ]; then
    echo "❌ Error: FUNDED_SEPOLIA_WALLET_PRIVATE_KEY is not set."
    echo "This wallet will be the contract owner and needs Sepolia ETH for gas."
    echo "Get Sepolia ETH from: https://sepoliafaucet.com/"
    echo "Then run: export FUNDED_SEPOLIA_WALLET_PRIVATE_KEY=your_private_key"
    exit 1
fi

echo "✅ Prerequisites check passed"
echo "📦 Installing dependencies..."
echo ""

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    npm install
else
    echo "Dependencies already installed"
fi

echo ""
echo "🚀 Deploying test tokens to Sepolia..."
echo ""

# Run deployment
npx hardhat run deploy-tokens.js --network sepolia

echo ""
echo "========================================"
echo "Next Steps:"
echo "========================================"
echo ""
echo "1. Copy the contract addresses from above"
echo "2. Add them to src/main/resources/application.properties:"
echo "   contract.test-usdc.address=YOUR_USDC_ADDRESS"
echo "   contract.test-dai.address=YOUR_DAI_ADDRESS"
echo ""
echo "3. Restart your Spring Boot application"
echo "4. Click 'Fund Wallet with Test Tokens' in the dashboard"
echo ""
echo "Deployment info saved to: $SCRIPT_DIR/deployed-addresses.json"
echo ""

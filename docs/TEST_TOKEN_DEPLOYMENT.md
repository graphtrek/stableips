# Test Token Deployment Guide

This guide explains how to deploy TestUSDC and TestDAI contracts to Sepolia testnet for the StableIPs application.

## Why Deploy Test Tokens?

- **Full Control**: Unlimited minting of USDC/DAI for testing
- **No Faucet Dependency**: Don't rely on external faucets
- **Automatic Funding**: Users get tokens automatically on signup
- **Real ERC-20**: Identical interface to real USDC/DAI

## Prerequisites

- MetaMask wallet with Sepolia ETH
- Access to Remix IDE (https://remix.ethereum.org)
- Your Sepolia funding wallet address (from `.env` or application.properties)

## Deployment Steps (Remix IDE - Recommended)

### Step 1: Open Remix IDE

Navigate to https://remix.ethereum.org in your browser.

### Step 2: Create Contract File

1. In the File Explorer (left sidebar), create a new file: `TestToken.sol`
2. Copy the contents from `contracts/TestToken.sol` in this repository
3. Paste into Remix

### Step 3: Compile Contract

1. Click the **"Solidity Compiler"** icon (left sidebar)
2. Select compiler version: **0.8.20** or higher
3. Click **"Compile TestToken.sol"**
4. Ensure compilation succeeds (green checkmark)

### Step 4: Connect MetaMask

1. Click the **"Deploy & Run Transactions"** icon (left sidebar)
2. Environment: Select **"Injected Provider - MetaMask"**
3. MetaMask popup will appear - click **"Connect"**
4. Ensure MetaMask is on **Sepolia Test Network**
5. Ensure you have some Sepolia ETH for gas

### Step 5: Deploy TestUSDC

1. In the "Deploy" section, you'll see constructor parameters
2. Fill in:
   - **_name**: `Test USDC`
   - **_symbol**: `USDC`
   - **_decimals**: `6`

3. Click **"transact"** (orange Deploy button)
4. MetaMask will popup - confirm the transaction
5. Wait for deployment (watch console for confirmation)
6. **COPY THE CONTRACT ADDRESS** - you'll see it under "Deployed Contracts"

Example address: `0x1234567890123456789012345678901234567890`

### Step 6: Deploy TestDAI

1. Clear the previous deployment (or keep it)
2. Fill in constructor parameters:
   - **_name**: `Test DAI`
   - **_symbol**: `DAI`
   - **_decimals**: `18`

3. Click **"transact"**
4. Confirm in MetaMask
5. **COPY THIS CONTRACT ADDRESS TOO**

### Step 7: Verify Contracts (Optional but Recommended)

1. Go to https://sepolia.etherscan.io/
2. Search for your contract address
3. Click "Contract" → "Verify and Publish"
4. Or use Remix plugin: "Etherscan - Contract Verification"

### Step 8: Configure StableIPs Application

Add these lines to `src/main/resources/application.properties`:

```properties
# Test Token Contracts (deployed on Sepolia)
contract.test-usdc.address=0xYOUR_TESTUSDC_ADDRESS
contract.test-dai.address=0xYOUR_TESTDAI_ADDRESS

# Initial token amounts for new users
token.funding.initial-usdc=1000
token.funding.initial-dai=1000
```

Replace `0xYOUR_TESTUSDC_ADDRESS` and `0xYOUR_TESTDAI_ADDRESS` with the addresses from steps 5 and 6.

## Testing Your Deployment

### Method 1: Mint via Remix

1. In Remix, under "Deployed Contracts", expand your TestUSDC contract
2. Find the `mint` function
3. Parameters:
   - **_to**: Your test wallet address
   - **_value**: `1000000000` (= 1,000 USDC with 6 decimals)
4. Click **"transact"**
5. Check balance using `balanceOf` function

### Method 2: Mint via StableIPs Application

Once configured, StableIPs will automatically mint tokens when creating new users.

Check the application logs for:
```
Minting 1000 USDC to 0xUserAddress
Minting 1000 DAI to 0xUserAddress
```

### Method 3: Verify on Etherscan

1. Go to https://sepolia.etherscan.io/address/YOUR_CONTRACT_ADDRESS
2. Click "Contract" → "Read Contract"
3. Use `balanceOf` with your address
4. Should show: 1000000000 (for USDC) or 1000000000000000000000 (for DAI)

## Contract Functions

Your deployed TestToken contract has these functions:

### Public Functions
- `balanceOf(address)` - Check token balance
- `transfer(address to, uint256 value)` - Transfer tokens
- `approve(address spender, uint256 value)` - Approve spending
- `transferFrom(address from, address to, uint256 value)` - Transfer on behalf

### Owner-Only Functions
- `mint(address to, uint256 value)` - Mint tokens to any address
- `batchMint(address[] recipients, uint256 value)` - Mint to multiple addresses

## Security Notes

⚠️ **IMPORTANT**: These are TEST tokens for DEMO purposes only!

- Only deploy on Sepolia testnet (NOT mainnet!)
- The owner (deployer) can mint unlimited tokens
- No built-in access controls beyond owner check
- Not audited - do NOT use in production
- Private keys are stored unencrypted in demo app

## Troubleshooting

### "Out of Gas" Error
- Increase gas limit in MetaMask
- Ensure you have enough Sepolia ETH

### "Contract not deployed"
- Check transaction succeeded on Etherscan
- Verify you copied the correct address
- Ensure address starts with `0x`

### "Only owner can call this"
- Only the deploying wallet can mint tokens
- Ensure `wallet.funding.private-key` matches the deployer wallet

### Tokens not showing in wallet
- Add custom token to MetaMask:
  - Token Address: Your deployed contract address
  - Symbol: USDC or DAI
  - Decimals: 6 (USDC) or 18 (DAI)

## Alternative: Using Hardhat (Advanced)

If you prefer CLI deployment:

```bash
# Coming soon - Hardhat deployment script
npm install --save-dev hardhat
npx hardhat run scripts/deploy.js --network sepolia
```

## Next Steps

After deployment:

1. ✅ Contracts deployed on Sepolia
2. ✅ Addresses added to application.properties
3. ✅ Test minting works in Remix
4. → Restart StableIPs application
5. → Create new user - should receive USDC/DAI automatically
6. → Verify balances on wallet dashboard

## Support

If you encounter issues:
- Check Sepolia Etherscan for transaction details
- Verify contract is verified on Etherscan
- Check application logs for minting errors
- Ensure funding wallet has Sepolia ETH for gas

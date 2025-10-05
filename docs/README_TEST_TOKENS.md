# Test Token Implementation (TestUSDC & TestDAI)

## Overview

StableIPS now supports **automatic funding** of USDC and DAI test tokens for new users through custom ERC-20 token contracts deployed on Sepolia testnet.

## Features

✅ **Unlimited Minting** - Deploy your own USDC/DAI contracts with full control
✅ **Automatic Funding** - New users receive tokens automatically on signup
✅ **ERC-20 Compatible** - Identical interface to real USDC/DAI
✅ **Owner Control** - Only deployer can mint tokens
✅ **Batch Minting** - Fund multiple addresses at once

## Quick Start

### 1. Deploy Contracts

Follow the deployment guide:
```bash
./scripts/deploy-test-tokens.sh
```

Or read the full guide: [docs/TEST_TOKEN_DEPLOYMENT.md](docs/TEST_TOKEN_DEPLOYMENT.md)

**Recommended:** Use Remix IDE (no local setup needed)
- Deploy `contracts/TestToken.sol` twice:
  - TestUSDC: name="Test USDC", symbol="USDC", decimals=6
  - TestDAI: name="Test DAI", symbol="DAI", decimals=18

### 2. Configure Application

Add the deployed contract addresses to `application.properties`:

```properties
# Test Token Contracts
contract.test-usdc.address=0xYOUR_TESTUSDC_ADDRESS
contract.test-dai.address=0xYOUR_TESTDAI_ADDRESS

# Initial amounts (1000 USDC and 1000 DAI per user)
token.funding.initial-usdc=1000
token.funding.initial-dai=1000
```

### 3. Restart Application

```bash
./gradlew bootRun
```

New users will now receive:
- ✅ 0.01 ETH (Sepolia testnet)
- ✅ 1000 USDC (your deployed TestUSDC)
- ✅ 1000 DAI (your deployed TestDAI)
- ✅ 10 XRP (XRP Ledger testnet)

## Architecture

### Contract: `TestToken.sol`

Simple, secure ERC-20 implementation with minting capability:

```solidity
contract TestToken {
    // Standard ERC-20 functions
    function transfer(address to, uint256 value) public returns (bool);
    function approve(address spender, uint256 value) public returns (bool);
    function transferFrom(address from, address to, uint256 value) public returns (bool);

    // Minting functions (owner only)
    function mint(address to, uint256 value) public onlyOwner returns (bool);
    function batchMint(address[] memory recipients, uint256 value) public onlyOwner;
}
```

### Service: `TestTokenService.java`

Handles token deployment and minting:

```java
@Service
public class TestTokenService {
    // Mint USDC to user (6 decimals)
    public String mintUSDC(String toAddress, BigDecimal amount);

    // Mint DAI to user (18 decimals)
    public String mintDAI(String toAddress, BigDecimal amount);

    // Fund user with both tokens
    public void fundUserWithTestTokens(String userAddress);
}
```

### Integration

User creation flow (`WalletService.createUserWithWalletAndFunding`):

```java
1. Generate Ethereum wallet
2. Generate XRP wallet
3. Fund with ETH → fundWallet()
4. Fund with USDC/DAI → testTokenService.fundUserWithTestTokens() ← NEW!
5. Fund with XRP → xrpWalletService.fundUserWallet()
```

## File Structure

```
stableips/
├── contracts/
│   └── TestToken.sol              # ERC-20 token contract
├── scripts/
│   └── deploy-test-tokens.sh      # Deployment helper script
├── docs/
│   └── TEST_TOKEN_DEPLOYMENT.md   # Detailed deployment guide
├── src/main/java/co/grtk/stableips/
│   └── service/
│       └── TestTokenService.java  # Token minting service
└── src/main/resources/
    └── application.properties      # Contract configuration
```

## Configuration Options

```properties
# Contract addresses (required - get from deployment)
contract.test-usdc.address=0x...
contract.test-dai.address=0x...

# Initial funding amounts (optional - defaults shown)
token.funding.initial-usdc=1000    # 1,000 USDC per new user
token.funding.initial-dai=1000     # 1,000 DAI per new user

# Funding wallet (required - must be contract owner)
wallet.funding.private-key=0x...   # Same wallet that deployed contracts
```

## Token Details

### TestUSDC
- **Name**: Test USDC
- **Symbol**: USDC
- **Decimals**: 6 (same as real USDC)
- **1 USDC** = 1,000,000 (smallest unit)
- **Example**: Minting 100 USDC = 100,000,000 units

### TestDAI
- **Name**: Test DAI
- **Symbol**: DAI
- **Decimals**: 18 (same as real DAI)
- **1 DAI** = 1,000,000,000,000,000,000 (smallest unit)
- **Example**: Minting 100 DAI = 100,000,000,000,000,000,000 units

## Usage Examples

### Manual Minting (via Remix)

1. Open your deployed contract in Remix
2. Find the `mint` function
3. Parameters:
   - `_to`: User wallet address
   - `_value`: Amount in smallest units
     - For 1000 USDC: `1000000000` (1000 × 10^6)
     - For 1000 DAI: `1000000000000000000000` (1000 × 10^18)
4. Click "transact"

### Batch Minting Multiple Users

```solidity
// Mint 100 USDC to 3 users at once
batchMint(
    [
        "0xUser1Address",
        "0xUser2Address",
        "0xUser3Address"
    ],
    100000000  // 100 USDC
)
```

### Check Balance (Remix)

1. Call `balanceOf` function
2. Input user address
3. Result shows balance in smallest units
4. Divide by decimals to get token amount:
   - USDC: balance / 1,000,000
   - DAI: balance / 1,000,000,000,000,000,000

## Troubleshooting

### "Only owner can call this"
- The funding wallet (`wallet.funding.private-key`) must be the same wallet that deployed the contracts
- Only the deployer can mint tokens

### Tokens not appearing in application
- Check contract addresses in `application.properties` are correct
- Verify contracts deployed successfully on Etherscan
- Check application logs for minting errors

### "Contract not deployed" message
- Leave `contract.test-usdc.address` empty until you deploy
- Application will skip token minting if addresses are empty
- No errors - just logs saying "skipping token minting"

### Gas errors during minting
- Ensure funding wallet has Sepolia ETH
- Each mint transaction requires gas (~50,000 gas)

## Security Notes

⚠️ **TESTNET ONLY** - Do NOT deploy on mainnet!

- These are DEMO tokens with NO value
- Owner can mint unlimited tokens
- No access controls beyond owner check
- Not audited - for testing only
- Private keys stored unencrypted in demo

## Benefits vs Real USDC/DAI

| Feature | Real Tokens | Test Tokens |
|---------|-------------|-------------|
| Availability | Limited faucets | Unlimited minting |
| Control | Third-party | Full owner control |
| Setup | Find faucets, wait | Deploy once, use forever |
| Reliability | Faucet may be down | Always available |
| Cost | Free (testnet) | Free (testnet) |

## Next Steps

1. ✅ Deploy TestUSDC and TestDAI using Remix
2. ✅ Add contract addresses to `application.properties`
3. ✅ Restart application
4. ✅ Create new user - verify they receive tokens
5. ✅ Check balances on wallet dashboard
6. ✅ Test transfers between users

## Additional Resources

- [Remix IDE](https://remix.ethereum.org)
- [Sepolia Etherscan](https://sepolia.etherscan.io)
- [ERC-20 Standard](https://eips.ethereum.org/EIPS/eip-20)
- [Web3j Documentation](https://docs.web3j.io)

## Support

If you encounter issues:
1. Check [docs/TEST_TOKEN_DEPLOYMENT.md](docs/TEST_TOKEN_DEPLOYMENT.md) for detailed guide
2. Verify contracts on Sepolia Etherscan
3. Check application logs for minting errors
4. Ensure funding wallet matches deployer wallet

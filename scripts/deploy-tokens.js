const hre = require("hardhat");
const fs = require("fs");
const path = require("path");

async function main() {
  console.log("========================================");
  console.log("Deploying Test Tokens to Sepolia...");
  console.log("========================================\n");

  const [deployer] = await hre.ethers.getSigners();
  console.log("Deploying with account:", deployer.address);

  const balance = await hre.ethers.provider.getBalance(deployer.address);
  console.log("Account balance:", hre.ethers.formatEther(balance), "ETH\n");

  if (balance === 0n) {
    console.error("❌ Error: Account has no ETH for gas fees!");
    console.error("Please fund your wallet with Sepolia ETH first.");
    console.error("Use: https://sepoliafaucet.com\n");
    process.exit(1);
  }

  // Deploy TestUSDC
  console.log("📝 Deploying TestUSDC...");
  const TestToken = await hre.ethers.getContractFactory("TestToken");
  const testUSDC = await TestToken.deploy("Test USDC", "USDC", 6);
  await testUSDC.waitForDeployment();
  const usdcAddress = await testUSDC.getAddress();
  console.log("✅ TestUSDC deployed to:", usdcAddress);

  // Deploy TestDAI
  console.log("\n📝 Deploying TestDAI...");
  const testDAI = await TestToken.deploy("Test DAI", "DAI", 18);
  await testDAI.waitForDeployment();
  const daiAddress = await testDAI.getAddress();
  console.log("✅ TestDAI deployed to:", daiAddress);

  // Save addresses to file
  const deploymentInfo = {
    network: "sepolia",
    deployer: deployer.address,
    testUSDC: usdcAddress,
    testDAI: daiAddress,
    timestamp: new Date().toISOString()
  };

  const outputPath = path.join(__dirname, "deployed-addresses.json");
  fs.writeFileSync(outputPath, JSON.stringify(deploymentInfo, null, 2));
  console.log("\n📄 Deployment info saved to:", outputPath);

  // Display configuration instructions
  console.log("\n========================================");
  console.log("✅ Deployment Complete!");
  console.log("========================================\n");
  console.log("Add these to your application.properties or environment:\n");
  console.log(`contract.test-usdc.address=${usdcAddress}`);
  console.log(`contract.test-dai.address=${daiAddress}`);
  console.log("\nOr export as environment variables:\n");
  console.log(`export contract.test-usdc.address=${usdcAddress}`);
  console.log(`export contract.test-dai.address=${daiAddress}`);
  console.log("\n========================================");
  console.log("View on Etherscan:");
  console.log(`TestUSDC: https://sepolia.etherscan.io/address/${usdcAddress}`);
  console.log(`TestDAI: https://sepolia.etherscan.io/address/${daiAddress}`);
  console.log("========================================\n");
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });

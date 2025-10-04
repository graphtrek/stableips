require("@nomicfoundation/hardhat-toolbox");

/** @type import('hardhat/config').HardhatUserConfig */
module.exports = {
  solidity: "0.8.20",
  networks: {
    sepolia: {
      url: process.env.INFURA_URL || `https://sepolia.infura.io/v3/${process.env.INFURA_PROJECT_ID}`,
      accounts: process.env.FUNDED_SEPOLIA_WALLET_PRIVATE_KEY
        ? [process.env.FUNDED_SEPOLIA_WALLET_PRIVATE_KEY]
        : [],
      chainId: 11155111
    }
  },
  paths: {
    sources: "../contracts",
    tests: "./test",
    cache: "./cache",
    artifacts: "./artifacts"
  }
};

# Git Workflow Setup Instructions

> **Status**: Ready to push to GitHub

## Current Status

✅ **Completed locally:**
- Created `dev` branch with all changes
- 3 commits ready to push:
  1. `03de2a6` - Initial project setup and documentation
  2. `6439dec` - GitHub Actions CI/CD pipeline
  3. `60889e6` - H2 test configuration
- Tests passing locally (`./gradlew test` ✅)
- CI/CD workflow configured

## Next Steps

### Option 1: Using GitHub CLI (Recommended)

If you have `gh` CLI installed:

```bash
# 1. Create GitHub repository
gh repo create stableips --public --description "Demo stablecoin wallet for Ethereum Sepolia testnet"

# 2. Add remote
git remote add origin https://github.com/YOUR_USERNAME/stableips.git

# 3. Push dev branch
git push -u origin dev

# 4. Create pull request
gh pr create --title "Initial project setup" --body "$(cat <<'EOF'
## Summary
- ✅ Spring Boot 3.5.6 + Java 21 setup
- ✅ JTE template engine configured
- ✅ PostgreSQL database support
- ✅ Comprehensive documentation (CLAUDE.md, ARCHITECTURE.md)
- ✅ GitHub Actions CI/CD pipeline
- ✅ H2 test database configuration

## Test Plan
- [x] Unit tests passing locally
- [ ] CI tests passing (will verify after merge)

## Review Evidence
- Tests: ✅ All passing (`./gradlew test`)
- Build: ✅ Successful (`./gradlew build`)
- CI: ⏳ Will run on push

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"

# 5. Wait for CI to pass, then merge
gh pr merge --auto --squash
```

---

### Option 2: Using GitHub Web UI

#### Step 1: Create GitHub Repository

1. Go to https://github.com/new
2. Repository name: `stableips`
3. Description: `Demo stablecoin wallet for Ethereum Sepolia testnet`
4. Visibility: Public (or Private)
5. **Do NOT initialize** with README, .gitignore, or license
6. Click "Create repository"

#### Step 2: Add Remote and Push

```bash
# Replace YOUR_USERNAME with your GitHub username
git remote add origin https://github.com/YOUR_USERNAME/stableips.git

# Push dev branch
git push -u origin dev

# Also push main/master (empty for now)
git checkout master
git push -u origin master
git checkout dev
```

#### Step 3: Create Pull Request (Web UI)

1. Go to your repository on GitHub
2. Click "Compare & pull request" button (appears after push)
3. Base: `master` (or `main`)
4. Compare: `dev`
5. Title: `Initial project setup`
6. Description:
   ```markdown
   ## Summary
   - ✅ Spring Boot 3.5.6 + Java 21 setup
   - ✅ JTE template engine configured
   - ✅ PostgreSQL database support
   - ✅ Comprehensive documentation (CLAUDE.md, ARCHITECTURE.md)
   - ✅ GitHub Actions CI/CD pipeline
   - ✅ H2 test database configuration

   ## Test Plan
   - [x] Unit tests passing locally
   - [ ] CI tests passing (will verify after merge)

   ## Review Evidence
   - Tests: ✅ All passing
   - Build: ✅ Successful
   - CI: ⏳ Will run on push
   ```
7. Click "Create pull request"

#### Step 4: Wait for CI and Merge

1. Wait for GitHub Actions to run (check "Actions" tab)
2. Verify all checks pass ✅
3. Click "Merge pull request" → "Squash and merge"
4. Delete `dev` branch (optional)

---

### Option 3: Direct Push to Main (Quick Start)

⚠️ **Not recommended** for team projects, but OK for solo/demo:

```bash
# 1. Create GitHub repository (see Step 1 above)

# 2. Add remote
git remote add origin https://github.com/YOUR_USERNAME/stableips.git

# 3. Checkout master and merge dev
git checkout master
git merge dev

# 4. Push to main
git push -u origin master
```

---

## Verify CI is Working

After pushing, check:

1. **GitHub Actions**: Go to your repo → "Actions" tab
2. You should see a workflow run for your push
3. Click on the workflow to see:
   - ✅ Build step
   - ✅ Test step
   - ✅ Code quality checks

**Expected output:**
```
✅ test / Set up JDK 21
✅ test / Build with Gradle
✅ test / Run tests
✅ code-quality / Check code style
```

---

## Troubleshooting

### Issue: No remote repository
```bash
# Check remotes
git remote -v

# If empty, add origin (replace URL)
git remote add origin https://github.com/YOUR_USERNAME/stableips.git
```

### Issue: CI tests fail
```bash
# Run tests locally first
./gradlew clean test

# Check test output
cat build/reports/tests/test/index.html
```

### Issue: Permission denied (publickey)
```bash
# Use HTTPS instead of SSH
git remote set-url origin https://github.com/YOUR_USERNAME/stableips.git

# Or set up SSH key: https://docs.github.com/en/authentication/connecting-to-github-with-ssh
```

---

## Current Branch Status

```bash
# Check current branch
git branch
# * dev
#   master

# Show commits on dev
git log --oneline dev
# 60889e6 test: configure H2 in-memory database for testing
# 6439dec ci: add GitHub Actions CI/CD pipeline
# 03de2a6 docs: initial project setup and comprehensive documentation

# Show commits on master (empty)
git log --oneline master
# (empty)
```

---

## After Merging to Main

Once merged, sync your dev branch:

```bash
# Checkout dev
git checkout dev

# Pull latest from main
git pull origin master

# Push updated dev
git push origin dev
```

Or delete dev and recreate for next feature:

```bash
# Delete remote dev branch
git push origin --delete dev

# Delete local dev branch
git branch -D dev

# Create new dev from latest main
git checkout master
git pull origin master
git checkout -b dev
```

---

## Files Ready to Push

All files have been committed to `dev` branch:

- ✅ `.github/workflows/ci.yml` - CI/CD pipeline
- ✅ `CLAUDE.md` - Development guidelines
- ✅ `docs/ARCHITECTURE.md` - System architecture
- ✅ `docs/PROJECT_FOUNDATION.md` - Development principles
- ✅ `docs/DAILY_PROGRESS.md` - Progress tracking
- ✅ `build.gradle` - Build configuration with H2 test dependency
- ✅ `src/test/resources/application-test.properties` - Test config
- ✅ All source code

**Total:** 3 commits, ~2800 lines of code/docs

---

## Quick Start Command

```bash
# Replace YOUR_USERNAME with your GitHub username
GITHUB_USER="YOUR_USERNAME"

# Create repo and push (requires gh CLI)
gh repo create stableips --public && \
git remote add origin https://github.com/$GITHUB_USER/stableips.git && \
git push -u origin dev && \
gh pr create --fill
```

---

## Next Steps After Setup

1. ✅ Verify CI passes
2. ✅ Merge PR to main
3. Add Web3J dependencies
4. Implement domain models
5. Build services and controllers

See `docs/DAILY_PROGRESS.md` for detailed roadmap.

# Project Foundation - Java 24 + Spring Boot + HTMX

> **Purpose**: Concrete implementation of project foundation for Java 24 + Spring Boot + Gradle + HTMX stack. This document provides specific commands, tools, and configurations for this technology combination.

**Last Updated**: 2025-10-03
**Status**: Living Document

---

## Table of Contents
1. [Stack Overview](#stack-overview)
2. [Project Setup](#project-setup)
3. [Development Workflow](#development-workflow)
4. [Quality Gates Implementation](#quality-gates-implementation)
5. [Testing Strategy](#testing-strategy)
6. [Container Configuration](#container-configuration)
7. [Makefile Commands](#makefile-commands)
8. [IDE Configuration](#ide-configuration)

---

## Stack Overview

### Technology Stack

**Backend**:
- **Java 24** (LTS, latest features)
- **Spring Boot 3.3.x** (framework)
- **Gradle 8.x** (build tool, Kotlin DSL preferred)
- **JUnit 5** (testing framework)
- **Mockito** (mocking framework)
- **Spring Boot Test** (integration testing)

**Frontend**:
- **HTMX 2.x** (dynamic HTML)
- **HTML5** + **CSS3** (structure & style)
- **JavaScript ES6+** (client-side logic)
- **jQuery 3.7.x** (DOM manipulation)
- **Bootstrap 5.3.8** (UI components)

**Infrastructure**:
- **Docker** (containerization)
- **PostgreSQL 16** or **H2** (database)
- **Gradle Wrapper** (gradlew, version-locked builds)

**Quality Tools**:
- **Checkstyle** (code style)
- **SpotBugs** (static analysis)
- **PMD** (code quality)
- **JaCoCo** (code coverage)
- **OWASP Dependency-Check** (security)

---

## Project Setup

### Directory Structure (Monorepo)

```
project/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/
│   │   │   │   ├── config/           # Spring configuration
│   │   │   │   ├── controller/       # REST + HTMX controllers
│   │   │   │   ├── service/          # Business logic
│   │   │   │   ├── repository/       # Data access (JPA)
│   │   │   │   ├── model/            # Domain models
│   │   │   │   └── Application.java  # Spring Boot entry point
│   │   │   └── resources/
│   │   │       ├── static/           # CSS, JS, images
│   │   │       ├── templates/        # Thymeleaf templates
│   │   │       └── application.yml   # Configuration
│   │   └── test/
│   │       ├── java/com/example/
│   │       │   ├── controller/       # Controller tests
│   │       │   ├── service/          # Service tests (unit)
│   │       │   ├── repository/       # Repository tests (integration)
│   │       │   └── integration/      # End-to-end tests
│   │       └── resources/
│   │           └── application-test.yml
│   ├── build.gradle.kts              # Gradle build config
│   ├── settings.gradle.kts
│   ├── gradlew                       # Gradle wrapper (Unix)
│   └── gradlew.bat                   # Gradle wrapper (Windows)
├── frontend/
│   ├── css/
│   │   ├── main.css                  # Custom styles
│   │   └── bootstrap.min.css         # Bootstrap (if not CDN)
│   ├── js/
│   │   ├── app.js                    # Main application JS
│   │   └── components/               # Reusable JS modules
│   └── tests/
│       └── app.test.js               # Frontend tests (optional)
├── docker/
│   ├── Dockerfile.backend
│   ├── Dockerfile.frontend           # Nginx (if separate)
│   └── docker-compose.yml
├── .github/
│   └── workflows/
│       └── ci.yml                    # GitHub Actions CI/CD
├── docs/
│   ├── API.md                        # API documentation
│   ├── ARCHITECTURE.md
│   └── DAILY_PROGRESS.md             # Daily progress tracker
├── .pre-commit-config.yaml           # Pre-commit hooks
├── Makefile                          # Unified commands
├── README.md
└── .gitignore
```

---

## Development Workflow

### Initial Setup

```bash
# Clone repository
git clone <repo-url>
cd <project>

# Run initial setup (installs dependencies, hooks)
make setup

# Start development server (hot reload)
make dev
```

### Day-to-Day Development

```bash
# 1. Pull latest changes
git pull origin main

# 2. Create changes (edit code)

# 3. Run tests locally (fast feedback)
make test-fast

# 4. Commit (pre-commit hook runs automatically)
git add .
git commit -m "feat: add user registration endpoint"

# 5. Run full review before pushing
make review

# 6. If review passes, push
git push origin dev

# 7. Create PR when ready
make pr
```

---

## Quality Gates Implementation

### Gate 1: Pre-Commit Hook (Automatic)

**File**: `.pre-commit-config.yaml`

```yaml
repos:
  - repo: local
    hooks:
      # Java formatting (google-java-format)
      - id: java-format
        name: Format Java code
        entry: ./gradlew spotlessApply
        language: system
        types: [java]
        pass_filenames: false

      # Check for secrets
      - id: detect-secrets
        name: Detect secrets
        entry: bash -c 'if grep -r "password\|secret\|api_key" src/; then echo "Potential secret found!"; exit 1; fi'
        language: system
        types: [java, yaml, properties]

      # Prevent commits to main
      - id: no-commit-to-main
        name: Block commits to main
        entry: bash -c 'if [ "$(git branch --show-current)" = "main" ]; then echo "Cannot commit to main!"; exit 1; fi'
        language: system
        always_run: true
        pass_filenames: false

      # Run quick unit tests (business logic only)
      - id: quick-tests
        name: Run quick unit tests
        entry: ./gradlew test --tests "*Service*Test" --tests "*Util*Test"
        language: system
        pass_filenames: false
```

**Installation** (in `make setup`):
```bash
pip install pre-commit
pre-commit install
```

**Test the hook**:
```bash
# Trigger by committing
git commit -m "test"
# Hook runs automatically: format → secrets → tests (~10-30s)
```

---

### Gate 2: Pre-Push Review (Manual)

**Command**: `make review`

**Implementation** (Makefile):
```makefile
review: ## Full code review (required before PR)
    @echo "🔍 Running full review..."
    @echo "Step 1/6: Formatting..."
    cd backend && ./gradlew spotlessCheck
    @echo "Step 2/6: Linting (Checkstyle + PMD + SpotBugs)..."
    cd backend && ./gradlew check -x test
    @echo "Step 3/6: Running all tests..."
    cd backend && ./gradlew test integrationTest
    @echo "Step 4/6: Generating coverage report..."
    cd backend && ./gradlew jacocoTestReport
    @echo "Step 5/6: Checking coverage thresholds..."
    cd backend && ./gradlew jacocoTestCoverageVerification
    @echo "Step 6/6: Security scan (OWASP)..."
    cd backend && ./gradlew dependencyCheckAnalyze
    @echo "✅ Review passed! Safe to create PR."
    @echo "Next: make pr"
```

**Coverage Thresholds** (`build.gradle.kts`):
```kotlin
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()  // 80% overall
            }
        }
        rule {
            element = "CLASS"
            includes = listOf("com.example.service.*")
            limit {
                minimum = "0.85".toBigDecimal()  // 85% for services
            }
        }
        rule {
            element = "CLASS"
            includes = listOf("com.example.controller.*")
            limit {
                minimum = "0.70".toBigDecimal()  // 70% for controllers
            }
        }
    }
}
```

**On Failure**:
```bash
# Fix issues
# Commit fixes
git add .
git commit -m "fix: increase test coverage for UserService"

# Re-run review
make review
```

---

### Gate 3: Pull Request Creation (Manual)

**Command**: `make pr`

**Implementation** (Makefile):
```makefile
pr: ## Create pull request (requires review to pass first)
    @echo "🚀 Creating pull request..."
    @if [ "$(git diff origin/main...HEAD --name-only | wc -l)" -eq 0 ]; then \
        echo "❌ No changes to create PR"; exit 1; \
    fi
    @echo "Generating PR body..."
    @gh pr create --title "$(git log -1 --pretty=%s)" --body "$(cat <<'EOF'\n\
## Summary\n\
$(git log origin/main..HEAD --oneline)\n\
\n\
## Test Plan\n\
- [ ] Unit tests passing ($(shell cd backend && ./gradlew test --dry-run | grep -c "Task :test"))\n\
- [ ] Integration tests passing\n\
- [ ] Coverage: $(cd backend && ./gradlew jacocoTestReport -q | grep -oP '\d+%' | head -1)\n\
- [ ] Security scan: PASSED\n\
\n\
## Review Evidence\n\
- Tests: ✅ All passing\n\
- Coverage: ✅ Above 80%\n\
- Security: ✅ No HIGH/CRITICAL vulnerabilities\n\
\n\
🤖 Generated with Claude Code\n\
EOF\n\
)"
    @echo "✅ PR created! Review and merge when ready."
```

**Manual alternative** (if `gh` CLI not available):
```bash
# Push to remote
git push origin dev

# Go to GitHub UI and create PR manually
# Include test results and coverage in PR description
```

---

### Gate 4: CI/CD Pipeline (Automatic)

**File**: `.github/workflows/ci.yml`

```yaml
name: CI Pipeline

on:
  pull_request:
    branches: [main]
  push:
    branches: [dev]

jobs:
  test:
    runs-on: ubuntu-latest

    strategy:
      matrix:
        java: [21, 24]  # Test on multiple Java versions

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK ${{ matrix.java }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ matrix.java }}
          distribution: 'temurin'

      - name: Cache Gradle packages
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}

      - name: Grant execute permission for gradlew
        run: chmod +x backend/gradlew

      - name: Build
        run: cd backend && ./gradlew build -x test

      - name: Run tests
        run: cd backend && ./gradlew test integrationTest

      - name: Generate coverage report
        run: cd backend && ./gradlew jacocoTestReport

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          files: backend/build/reports/jacoco/test/jacocoTestReport.xml

      - name: Check coverage thresholds
        run: cd backend && ./gradlew jacocoTestCoverageVerification

      - name: Security scan
        run: cd backend && ./gradlew dependencyCheckAnalyze

      - name: Upload security report
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: dependency-check-report
          path: backend/build/reports/dependency-check-report.html

  build-container:
    runs-on: ubuntu-latest
    needs: test

    steps:
      - uses: actions/checkout@v4

      - name: Build Docker image
        run: docker build -f docker/Dockerfile.backend -t app:${{ github.sha }} .

      - name: Scan container for vulnerabilities
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: app:${{ github.sha }}
          severity: 'HIGH,CRITICAL'
          exit-code: '1'
```

**On Failure**: PR is blocked. Fix issues and push updates.

**On Success**: PR is mergeable.

---

### Gate 5: Post-Merge Sync (Manual)

**Command**: `make sync`

**Implementation** (Makefile):
```makefile
sync: ## Sync development branch with main after merge
    @echo "🔄 Syncing with main..."
    git checkout dev
    git pull origin main
    git push origin dev
    @echo "✅ Development branch is up to date with main"
```

---

## Testing Strategy

### Test Categories

**1. Unit Tests** (80-90% coverage target)
- **Location**: `src/test/java/com/example/service/`
- **Purpose**: Test business logic in isolation
- **Tools**: JUnit 5 + Mockito
- **Speed**: Fast (<1s per test)

**Example** (`UserServiceTest.java`):
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("Should create user with valid data")
    void shouldCreateUser() {
        // Given
        User user = new User("john@example.com", "password123");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        User result = userService.createUser(user);

        // Then
        assertThat(result.getEmail()).isEqualTo("john@example.com");
        verify(userRepository, times(1)).save(user);
    }
}
```

**Run command**:
```bash
./gradlew test
```

---

**2. Integration Tests** (70% coverage target)
- **Location**: `src/test/java/com/example/integration/`
- **Purpose**: Test database, HTTP, external services
- **Tools**: Spring Boot Test + Testcontainers
- **Speed**: Slower (~2-5s per test)

**Example** (`UserControllerIntegrationTest.java`):
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("POST /api/users should create user and return 201")
    void shouldCreateUser() {
        // Given
        UserDto userDto = new UserDto("john@example.com", "password123");

        // When
        ResponseEntity<UserDto> response = restTemplate.postForEntity(
            "/api/users", userDto, UserDto.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getEmail()).isEqualTo("john@example.com");
    }
}
```

**Run command**:
```bash
./gradlew integrationTest
```

**Configuration** (`build.gradle.kts`):
```kotlin
// Separate integration tests from unit tests
tasks.register<Test>("integrationTest") {
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter(tasks.test)
}
```

---

**3. Frontend Tests** (40-60% coverage target, optional)
- **Location**: `frontend/tests/`
- **Purpose**: Test JavaScript event handlers, HTMX interactions
- **Tools**: Jest + jsdom (or Playwright for E2E)
- **Speed**: Fast for unit tests, slow for E2E

**Example** (`app.test.js`):
```javascript
describe('User Form Validation', () => {
    test('should show error on invalid email', () => {
        // Given
        document.body.innerHTML = `
            <input id="email" type="email" />
            <span id="error"></span>
        `;

        // When
        validateEmail('invalid-email');

        // Then
        expect(document.getElementById('error').textContent)
            .toBe('Invalid email address');
    });
});
```

**Run command** (if using npm):
```bash
npm test
```

**Note**: For HTMX-heavy apps, frontend testing is often minimal (HTMX is server-rendered). Focus on backend tests.

---

### Coverage Reporting

**Generate HTML report**:
```bash
./gradlew jacocoTestReport
open backend/build/reports/jacoco/test/html/index.html
```

**Example output**:
```
Class Coverage: 85%
Method Coverage: 82%
Line Coverage: 80%

Packages:
- com.example.service: 90% ✅
- com.example.controller: 75% ✅
- com.example.repository: 68% ⚠️  (below 70%)
- com.example.config: 45% (acceptable for config)
```

**Fail build if coverage drops**:
```bash
./gradlew jacocoTestCoverageVerification
# Fails if any rule violated (see Gate 2 config)
```

---

## Container Configuration

### Dockerfile (Multi-Stage Build)

**File**: `docker/Dockerfile.backend`

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:24-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper and dependencies (for caching)
COPY backend/gradle/ gradle/
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./

# Download dependencies (cached if no changes)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY backend/src/ src/

# Build application
RUN ./gradlew bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:24-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy JAR from builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Change ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Build**:
```bash
docker build -f docker/Dockerfile.backend -t myapp:latest .
```

**Run**:
```bash
docker run -p 8080:8080 myapp:latest
```

---

### Docker Compose (Local Development)

**File**: `docker/docker-compose.yml`

```yaml
version: '3.8'

services:
  backend:
    build:
      context: ..
      dockerfile: docker/Dockerfile.backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/myapp
      - SPRING_DATASOURCE_USERNAME=postgres
      - SPRING_DATASOURCE_PASSWORD=postgres
    depends_on:
      db:
        condition: service_healthy
    networks:
      - app-network

  db:
    image: postgres:16-alpine
    environment:
      - POSTGRES_DB=myapp
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - app-network

volumes:
  postgres-data:

networks:
  app-network:
    driver: bridge
```

**Start**:
```bash
docker-compose -f docker/docker-compose.yml up
```

---

### Container Security Scan

**Install Trivy**:
```bash
brew install trivy  # macOS
# or
wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | sudo apt-key add -
echo "deb https://aquasecurity.github.io/trivy-repo/deb $(lsb_release -sc) main" | sudo tee -a /etc/apt/sources.list.d/trivy.list
sudo apt-get update && sudo apt-get install trivy
```

**Scan image**:
```bash
trivy image myapp:latest
```

**Example output**:
```
myapp:latest (alpine 3.19)
==========================
Total: 5 (HIGH: 2, CRITICAL: 0)

┌───────────────┬──────────────┬──────────┬────────┬───────────────┐
│   Library     │ Vulnerability│ Severity │ Status │ Installed Ver │
├───────────────┼──────────────┼──────────┼────────┼───────────────┤
│ libcrypto3    │ CVE-2024-1234│ HIGH     │ fixed  │ 3.1.0-r1      │
└───────────────┴──────────────┴──────────┴────────┴───────────────┘
```

**Fail build on HIGH/CRITICAL**:
```bash
trivy image --severity HIGH,CRITICAL --exit-code 1 myapp:latest
```

---

## Makefile Commands

**File**: `Makefile`

```makefile
.PHONY: help setup dev build test test-fast coverage review pr sync clean format lint security status

help: ## Show this help message
    @grep -E '^[a-zA-Z_-]+:.*?## .*$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-15s\033[0m %s\n", $1, $2}'

setup: ## Install dependencies and configure environment
    @echo "📦 Installing dependencies..."
    cd backend && ./gradlew build
    @echo "🔗 Installing pre-commit hooks..."
    pip install pre-commit
    pre-commit install
    @echo "✅ Setup complete! Run 'make dev' to start."

dev: ## Run development server (hot reload)
    @echo "🚀 Starting development server..."
    cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'

build: ## Build production artifacts
    @echo "🔨 Building application..."
    cd backend && ./gradlew bootJar
    @echo "✅ Build complete: backend/build/libs/*.jar"

test: ## Run full test suite
    @echo "🧪 Running all tests..."
    cd backend && ./gradlew test integrationTest

test-fast: ## Run quick unit tests only (<30s)
    @echo "⚡ Running quick tests..."
    cd backend && ./gradlew test --tests "*Service*Test" --tests "*Util*Test"

coverage: ## Generate coverage report
    @echo "📊 Generating coverage report..."
    cd backend && ./gradlew jacocoTestReport
    @echo "✅ Report: backend/build/reports/jacoco/test/html/index.html"

review: ## Full code review (required before PR)
    @echo "🔍 Running full review..."
    @echo "Step 1/6: Formatting..."
    cd backend && ./gradlew spotlessCheck
    @echo "Step 2/6: Linting (Checkstyle + PMD + SpotBugs)..."
    cd backend && ./gradlew check -x test
    @echo "Step 3/6: Running all tests..."
    cd backend && ./gradlew test integrationTest
    @echo "Step 4/6: Generating coverage report..."
    cd backend && ./gradlew jacocoTestReport
    @echo "Step 5/6: Checking coverage thresholds..."
    cd backend && ./gradlew jacocoTestCoverageVerification
    @echo "Step 6/6: Security scan (OWASP)..."
    cd backend && ./gradlew dependencyCheckAnalyze
    @echo "✅ Review passed! Safe to create PR."
    @echo "Next: make pr"

pr: ## Create pull request (requires review to pass first)
    @echo "🚀 Creating pull request..."
    @gh pr create --title "$(git log -1 --pretty=%s)" --body "See commits for details. Review passed: tests ✅ coverage ✅ security ✅"

sync: ## Sync development branch with main after merge
    @echo "🔄 Syncing with main..."
    git checkout dev
    git pull origin main
    git push origin dev
    @echo "✅ Development branch is up to date with main"

clean: ## Remove build artifacts
    @echo "🧹 Cleaning build artifacts..."
    cd backend && ./gradlew clean
    rm -rf backend/build/

format: ## Auto-format all code
    @echo "✨ Formatting code..."
    cd backend && ./gradlew spotlessApply

lint: ## Run linters (report only)
    @echo "🔎 Running linters..."
    cd backend && ./gradlew checkstyleMain checkstyleTest pmdMain pmdTest spotbugsMain spotbugsTest

security: ## Run security scans
    @echo "🔒 Running security scan..."
    cd backend && ./gradlew dependencyCheckAnalyze
    @echo "📄 Report: backend/build/reports/dependency-check-report.html"

status: ## Show project status
    @echo "📊 Project Status"
    @echo "================"
    @echo "Branch: $(git branch --show-current)"
    @echo "Commits ahead: $(git rev-list --count origin/main..HEAD)"
    @echo "Uncommitted changes: $(git status --porcelain | wc -l)"
    @echo ""
    @echo "Last test run:"
    @cd backend && ./gradlew test --dry-run 2>/dev/null | grep "Task :test" || echo "No tests found"
    @echo ""
    @echo "Coverage: (run 'make coverage' to generate)"
```

**Usage**:
```bash
make help       # Show all commands
make setup      # Initial setup
make dev        # Start dev server
make review     # Full review before PR
make pr         # Create PR
```

---

## IDE Configuration

### IntelliJ IDEA (Recommended)

**1. Import Project**:
- File → Open → Select `backend/build.gradle.kts`
- Trust Gradle project
- Wait for indexing

**2. Configure Java 24**:
- File → Project Structure → Project SDK → Add JDK → Download JDK 24 (Temurin)

**3. Enable Auto-Format on Save**:
- Settings → Tools → Actions on Save
  - ✅ Reformat code
  - ✅ Optimize imports
  - ✅ Run code cleanup

**4. Install Plugins**:
- **Checkstyle-IDEA** (run Checkstyle in IDE)
- **SonarLint** (real-time code quality)
- **HTMX Support** (syntax highlighting)

**5. Configure Gradle**:
- Settings → Build, Execution, Deployment → Build Tools → Gradle
  - Build and run using: **Gradle**
  - Run tests using: **Gradle**

**6. Run Configurations**:
- Edit Configurations → Add New → Gradle
  - Name: "Run App"
  - Gradle project: backend
  - Tasks: bootRun
  - Arguments: --args='--spring.profiles.active=dev'

---

### VS Code (Alternative)

**1. Install Extensions**:
- **Extension Pack for Java** (Microsoft)
- **Spring Boot Extension Pack** (VMware)
- **Gradle for Java** (Microsoft)
- **Checkstyle for Java** (ShengChen)

**2. Settings** (`.vscode/settings.json`):
```json
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.compile.nullAnalysis.mode": "automatic",
  "java.jdt.ls.vmargs": "-XX:+UseParallelGC -XX:GCTimeRatio=4 -XX:AdaptiveSizePolicyWeight=90 -Dsun.zip.disableMemoryMapping=true -Xmx2G -Xms100m",
  "java.format.settings.url": "https://raw.githubusercontent.com/google/styleguide/gh-pages/eclipse-java-google-style.xml",
  "editor.formatOnSave": true,
  "editor.codeActionsOnSave": {
    "source.organizeImports": true
  }
}
```

**3. Tasks** (`.vscode/tasks.json`):
```json
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "Run Spring Boot",
      "type": "shell",
      "command": "./gradlew bootRun",
      "options": {
        "cwd": "${workspaceFolder}/backend"
      },
      "problemMatcher": []
    },
    {
      "label": "Run Tests",
      "type": "shell",
      "command": "./gradlew test",
      "options": {
        "cwd": "${workspaceFolder}/backend"
      },
      "group": {
        "kind": "test",
        "isDefault": true
      }
    }
  ]
}
```

---

## Common Issues & Solutions

### Issue 1: Pre-commit hook fails with "gradlew: Permission denied"

**Solution**:
```bash
chmod +x backend/gradlew
git add backend/gradlew
git commit -m "fix: make gradlew executable"
```

---

### Issue 2: JaCoCo coverage report shows 0%

**Cause**: Tests not running or JaCoCo plugin misconfigured

**Solution** (ensure in `build.gradle.kts`):
```kotlin
plugins {
    jacoco
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
```

---

### Issue 3: OWASP Dependency-Check takes 10+ minutes

**Cause**: First run downloads CVE database (~1GB)

**Solution**:
- First run: Be patient
- Subsequent runs: ~30s (cached database)
- CI: Cache `~/.gradle/dependency-check-data/`

---

### Issue 4: Docker build fails with "unable to find JAR"

**Cause**: JAR not built or wrong path

**Solution**:
```bash
# Build JAR first
cd backend && ./gradlew bootJar

# Verify JAR exists
ls backend/build/libs/*.jar

# Then build Docker image
docker build -f docker/Dockerfile.backend -t myapp:latest .
```

---

## Quick Reference

### Gradle Commands

```bash
# Build
./gradlew build                    # Full build (compile + test + jar)
./gradlew bootJar                  # Build executable JAR only
./gradlew bootRun                  # Run application

# Testing
./gradlew test                     # Run unit tests
./gradlew integrationTest          # Run integration tests
./gradlew test --tests UserServiceTest  # Run specific test

# Code Quality
./gradlew spotlessApply            # Auto-format code
./gradlew spotlessCheck            # Check formatting
./gradlew checkstyleMain           # Run Checkstyle
./gradlew spotbugsMain             # Run SpotBugs
./gradlew pmdMain                  # Run PMD

# Coverage
./gradlew jacocoTestReport         # Generate coverage report
./gradlew jacocoTestCoverageVerification  # Verify thresholds

# Security
./gradlew dependencyCheckAnalyze   # OWASP dependency scan

# Utilities
./gradlew clean                    # Delete build/
./gradlew dependencies             # Show dependency tree
./gradlew tasks                    # List all tasks
```

---

### HTMX Patterns

**Example Controller** (returning HTML fragments):
```java
@Controller
@RequestMapping("/users")
public class UserController {

    @GetMapping
    public String listUsers(Model model) {
        model.addAttribute("users", userService.findAll());
        return "users/list";  // Full page
    }

    @PostMapping
    @ResponseBody
    public String createUser(@ModelAttribute UserDto userDto) {
        User user = userService.create(userDto);
        // Return HTML fragment for HTMX to swap
        return String.format(
            "<tr><td>%s</td><td>%s</td></tr>",
            user.getId(), user.getEmail()
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) {
        userService.delete(id);
        // HTMX will remove the row client-side
    }
}
```

**Example Template** (Thymeleaf + HTMX):
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Users</title>
    <script src="https://unpkg.com/htmx.org@2.0.0"></script>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body>
    <div class="container">
        <h1>Users</h1>

        <!-- Form submits via HTMX -->
        <form hx-post="/users" hx-target="#user-table tbody" hx-swap="beforeend">
            <input type="email" name="email" required>
            <button type="submit" class="btn btn-primary">Add User</button>
        </form>

        <!-- Table updates dynamically -->
        <table id="user-table" class="table">
            <thead>
                <tr><th>ID</th><th>Email</th><th>Actions</th></tr>
            </thead>
            <tbody>
                <tr th:each="user : ${users}">
                    <td th:text="${user.id}"></td>
                    <td th:text="${user.email}"></td>
                    <td>
                        <button hx-delete="${'/users/' + user.id}"
                                hx-target="closest tr"
                                hx-swap="outerHTML swap:1s"
                                class="btn btn-danger btn-sm">
                            Delete
                        </button>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>
</body>
</html>
```

---

## Next Steps

1. **Set up new project**:
   ```bash
   mkdir myproject && cd myproject
   make setup
   ```

2. **Review parent document**: Read `PROJECT_FOUNDATION.md` for philosophy and patterns

3. **Start development**: `make dev` and begin coding

4. **Before first commit**: Ensure pre-commit hooks are working

5. **Before first PR**: Run `make review` to validate

---

## Version History

| Date | Version | Changes |
|------|---------|---------|
| 2025-10-03 | 1.0.0 | Initial Java 24 + Spring Boot + HTMX foundation |

---

## References

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [HTMX Documentation](https://htmx.org/docs/)
- [Gradle User Guide](https://docs.gradle.org/)
- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [Bootstrap 5 Documentation](https://getbootstrap.com/docs/5.3/)

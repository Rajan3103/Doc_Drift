# DocDrift: AI-Assisted Documentation Drift Detector

**DocDrift** is an automated, AI-assisted system designed to detect and remediate documentation-code drift in software repositories. Whenever developers push code changes, DocDrift analyzes code diffs against existing prose documentation (READMEs, `/docs` directory, Wiki pages) using Google Gemini's semantic reasoning, generates specific patch suggestions, and presents them in a review dashboard for human approval before opening a fix pull request.

---

## 🚀 Features

- 📡 **API Contract & Schema Drift Detection**: Continuously validates backend Controller routes, request/response DTOs, and validation rules against OpenAPI/Swagger YAML/JSON files and Markdown API tables, proactively flagging **Breaking Changes** with HTTP method badges and endpoint tags.
- 🧠 **Semantic Drift Detection**: Performs AST/symbol-aware reasoning to detect breaking API signatures, removed config keys, outdated CLI parameters, and broken sample code.
- ⚡ **Classification & Severity Scoring**: Categorizes drift events into distinct types (`API_CONTRACT_BREAKING_CHANGE`, `SCHEMA_DRIFT`, `API_SIGNATURE_MISMATCH`, `CONFIG_PROPERTY_CHANGED`, `CLI_USAGE_CHANGED`, `BEHAVIORAL_LOGIC_DRIFT`, `SAMPLE_CODE_BROKEN`) with confidence metrics (0–100%) and severity levels (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`).
- 📁 **Multi-Document & Spec Scanning**: Proactively checks multiple documentation targets (`README.md`, `openapi.yaml`, `swagger.json`, `docs/api.md`, `CONTRIBUTING.md`, `ARCHITECTURE.md`) against commit diffs.
- 💡 **Actionable Patch Generation**: Uses Google's Gemini LLM to generate precise, structured text replacements (`oldText` → `suggestedText`) along with AI reasoning.
- 🔍 **Pull Request Checks**: Analyses pull requests into watched branches before they merge, keeps one up-to-date DocDrift comment on the PR, and sets a `docdrift/docs` commit status (failing for breaking or critical drift).
- 📏 **Rule-Based API Contract Check**: Compares the Spring endpoints in changed controllers with your OpenAPI/Swagger spec without any AI, and hands the exact differences to the AI to explain and fix.
- 🤖 **Choice of AI**: Gemini or Claude, with automatic fallback to the other when one fails.
- ✍️ **Edit & Batch Approve**: Edit a suggested fix before approving it, and approve several suggestions as a single pull request.
- ⚙️ **Per-Repository Settings**: Doc paths, watched branches, minimum severity and confidence, PR checks on/off, and Slack/email alerts for breaking or critical drift.
- 📈 **Activity & History**: A live activity feed of every push, PR check and scan, and a 30-day drift chart per repository.
- 🛡️ **Human-in-the-Loop Oversight**: No documentation is mutated automatically. Suggestions enter a review queue requiring explicit approval.
- 🎨 **Modern Review Dashboard**: Premium glassmorphic React dashboard with breaking change alert banners, HTTP method badges, severity tags, side-by-side diffs, and one-click approve/reject actions.
- 🗄️ **Auditable Persistence**: Tracks all drift reports, suggested fixes, and reviewer actions in PostgreSQL for full historical auditability.

---

## 🛠️ Technology Stack

### Backend
- **Framework**: Spring Boot (Java 21)
- **Data Access**: Spring Data JPA, Hibernate, Flyway migrations
- **Database**: PostgreSQL (pgAdmin 4 integration) / H2 in-memory
- **Networking**: Spring WebFlux (`WebClient` for non-blocking GitHub & Gemini API calls)
- **Security**: Spring Security (CORS enabled for local frontend integration)

### Frontend
- **Framework**: React (Vite)
- **Icons**: Lucide React
- **Styling**: Vanilla CSS (Custom dark-mode glassmorphic design system)

### AI & Integrations
- **LLM Engine**: Google Gemini API (`gemini-flash-latest` by default) or Anthropic Claude (`claude-opus-5-5` by default, via the official Anthropic Java SDK), selected with `LLM_PROVIDER`
- **Version Control**: GitHub REST API & Webhook events

---

## 📐 Project Structure

```
Docdrift/
├── Docdrift/                 # Spring Boot Backend Project
│   ├── src/main/java/com/docdrift/Docdrift/
│   │   ├── config/           # Security & WebClient Configurations
│   │   ├── controller/       # REST Controllers (Webhooks, Repositories, Reviews)
│   │   ├── dto/              # Request/Response DTOs & Gemini payload models
│   │   ├── model/            # JPA Entities (DriftReport, DriftSuggestion, etc.)
│   │   ├── repository/       # Spring Data JPA Repositories
│   │   └── service/          # GitHub, Gemini LLM, & Diff Analysis logic
│   ├── src/main/resources/
│   │   ├── db/migration/     # Flyway SQL migrations (database schema)
│   │   └── application.properties
│   ├── Dockerfile
│   └── pom.xml
│
├── frontend/                 # Vite + React Dashboard Application
│   ├── src/
│   │   ├── components/       # Header, ReportList, SuggestionCard, filters & pagination
│   │   ├── api.js            # Axios/Fetch integration with Spring Boot API
│   │   ├── App.jsx           # Main Dashboard Layout & Repo Tracking Modal
│   │   └── index.css         # Glassmorphism Design System
│   ├── Dockerfile
│   └── package.json
│
├── .github/workflows/ci.yml  # Automated build, tests, lint and Docker checks
├── docker-compose.yml        # Runs database + backend + dashboard together
└── README.md
```

---

## ⚙️ Setup and Installation

### Quick start with Docker

The easiest way to run everything (PostgreSQL, backend and dashboard) is Docker Compose:

```bash
cp .env.example .env      # then fill in the values (see the table below)
docker compose up --build
```

Open **http://localhost:5173** and enter your `DOCDRIFT_API_KEY` when asked. Database data is kept in a Docker volume between restarts (`docker compose down -v` deletes it).

To run the pieces yourself instead, follow the steps below.

### Prerequisites
- **Java 21** or later
- **Node.js** (v18+) & **npm**
- **PostgreSQL** running locally (or via pgAdmin 4)

---

### 1. Database Setup (PostgreSQL)
1. Open pgAdmin 4 or `psql` command line.
2. Create a new database named `docdrift`:
   ```sql
   CREATE DATABASE docdrift;
   ```
   The tables are created automatically on startup by the Flyway migrations in `Docdrift/src/main/resources/db/migration`. Databases created by older DocDrift versions are upgraded in place. To change the schema, add a new `V<number>__description.sql` file there; never edit a migration that has already run.
3. Set your configuration as **environment variables** (never put real secrets in `application.properties`):

   | Variable | Required | What it is |
   |---|---|---|
   | `DB_URL` | no | JDBC URL, default `jdbc:postgresql://localhost:5432/docdrift` |
   | `DB_USERNAME` | no | Database user, default `postgres` |
   | `DB_PASSWORD` | yes | Database password |
   | `GITHUB_PAT` | yes | GitHub token with **Contents: read & write**, **Pull requests: read & write** and **Commit statuses: read & write** on tracked repos (to open fix PRs, comment on PRs and set statuses) |
   | `GEMINI_API_KEY` | yes | Google Gemini API key |
   | `GEMINI_MODEL` | no | Gemini model name, default `gemini-flash-latest` |
   | `DOCDRIFT_API_KEY` | yes | Any long random string; the dashboard asks for it and sends it as the `X-API-Key` header |
   | `GITHUB_WEBHOOK_SECRET` | yes | Random string; must match the secret set on the GitHub webhook |
   | `CORS_ALLOWED_ORIGINS` | no | Comma-separated dashboard origins, default `http://localhost:5173` (add your Vercel URL in production) |
   | `LLM_PROVIDER` | no | `gemini` (default) or `claude` |
   | `LLM_FALLBACK` | no | Provider to try when the main one fails: `claude`, `gemini` or `none` (default) |
   | `ANTHROPIC_API_KEY` | for Claude | Anthropic API key |
   | `CLAUDE_MODEL` | no | Claude model, default `claude-opus-5-5` |
   | `CLAUDE_EFFORT` | no | `low`, `medium` (default), `high`, `xhigh` or `max`. Higher is more thorough and costs more |
   | `DASHBOARD_URL` | no | Your dashboard's address, linked from PR comments, statuses and alerts |
   | `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | for email alerts | Your SMTP mail server |
   | `ALERT_EMAIL_FROM` | no | Sender address for email alerts |

   Generate random values with `openssl rand -hex 32`. The API and webhook reject all requests until `DOCDRIFT_API_KEY` and `GITHUB_WEBHOOK_SECRET` are set.

4. On GitHub, add a webhook to each tracked repository (**Settings → Webhooks → Add webhook**):
   - Payload URL: `https://<your-backend>/api/webhooks/github`
   - Content type: `application/json`
   - Secret: the same value as `GITHUB_WEBHOOK_SECRET`
   - Events: choose **Let me select individual events**, then tick **Pushes** and **Pull requests**

   Events from repositories that are not tracked in DocDrift are ignored, and so are pushes to branches the repository does not watch (by default only the default branch; change it in the repository's ⚙️ settings).

### Repository settings

Each tracked repository has a ⚙️ **Settings** button on the dashboard:

| Setting | What it does |
|---|---|
| Documentation to check | Files (`README.md`), folders (`docs/`) or patterns (`api/**.yaml`). Empty = find docs automatically |
| Branches to watch | Branch names or patterns (`release/*`). Empty = the default branch only. PRs into these branches are checked too |
| Minimum severity / confidence | Suggestions below these are dropped, to cut noise |
| Check pull requests | Turn PR comments and statuses on or off |
| Slack webhook / email | Alerts for breaking or critical drift. Slack URLs must be `https://hooks.slack.com/services/...`, and the URL is never shown again after saving |

### How a scan works

1. **One diff per push** – a push is analysed as a single combined diff (`before...after`), not one analysis per commit. A manual scan analyses the latest commit on the default branch.
2. **Diff cleanup** – lockfiles, generated output (`dist/`, `build/`, `target/`, …) and binaries are removed, and the diff is capped in size. Pushes that only change docs are skipped.
3. **Doc discovery** – DocDrift lists the repository's files and picks Markdown docs and OpenAPI/Swagger specs (not changelogs or licences). API specs and the root README are always checked; other docs only if they mention something the diff removed or changed.
4. **Contract check** – when an OpenAPI/Swagger spec is among the docs, DocDrift compares the Spring endpoints (`@GetMapping`, `@RequestMapping`, …) in changed controllers before and after the change with the spec. Endpoints removed from code but still documented, or added but undocumented, are exact facts: they're given to the AI to fix, and any the AI misses are listed as **rule-based** suggestions (approve = acknowledge, since there's no exact text to replace).
5. **Analysis** – the AI (Gemini or Claude) returns structured JSON. Temporary failures (rate limits, 5xx, network) are retried, then the fallback provider is used if one is configured.
6. **Settings** – suggestions below the repository's minimum severity/confidence are dropped; breaking or critical drift triggers the configured alerts. For pull requests, DocDrift comments and sets the commit status instead of changing the repository's status.
7. **Status** – the repository shows `SCANNING` while this runs, then `DRIFTED` (suggestions awaiting review), `HEALTHY`, or `ERROR` with the reason. The dashboard refreshes automatically while a scan runs.

Repeated webhook deliveries (same `X-GitHub-Delivery`) are ignored, and a manual scan of an already-analysed commit does not create a duplicate report. Limits are adjustable under "Drift Analysis Limits" in `application.properties`.

---

### 2. Running the Backend (Spring Boot)

Navigate to the backend directory and launch the server:

```powershell
cd Docdrift
.\mvnw.cmd spring-boot:run
```
The backend server will start on `http://localhost:8080`.

---

### 3. Running the Frontend Dashboard (React + Vite)

Open a new terminal window, navigate to the `frontend` folder, and start the Vite dev server:

```powershell
cd frontend
npm install
npm run dev
```
The dashboard will be available at **`http://localhost:5173`**.

---

## 🧪 Testing the APIs via Terminal (`curl`)

### 1. Track a Repository
All dashboard endpoints require the `X-API-Key` header.

```bash
curl -X POST http://localhost:8080/api/repos \
  -H "X-API-Key: $DOCDRIFT_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://github.com/Rajan3103/Doc_Drift", "name":"Rajan3103/Doc_Drift"}'
```

### 2. Simulate a GitHub Webhook Push Event
Webhook requests must be signed with `GITHUB_WEBHOOK_SECRET`, exactly like GitHub does:
```bash
BODY='{"ref":"refs/heads/main","repository":{"full_name":"Rajan3103/Doc_Drift"},"commits":[{"id":"sample-sha-12345","message":"Updated function signatures"}]}'
SIG="sha256=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$GITHUB_WEBHOOK_SECRET" | sed 's/^.* //')"
curl -X POST http://localhost:8080/api/webhooks/github \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: push" \
  -H "X-GitHub-Delivery: test-delivery-1" \
  -H "X-Hub-Signature-256: $SIG" \
  -d "$BODY"
```

### 3. Fetch Drift Reports
Results are paged (newest first) and can be filtered by `repository`, `severity` (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`) and `status` (`PENDING`, `APPROVED`, `REJECTED`):
```bash
curl -H "X-API-Key: $DOCDRIFT_API_KEY" "http://localhost:8080/api/reports?page=0&size=20&severity=CRITICAL&status=PENDING"
curl -H "X-API-Key: $DOCDRIFT_API_KEY" http://localhost:8080/api/reports/summary   # pending / critical / breaking counts
curl -H "X-API-Key: $DOCDRIFT_API_KEY" "http://localhost:8080/api/repos?page=0&size=20"
```
Paged responses look like `{"content": [...], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3}` (maximum `size` is 100).

### 4. Review Suggestions
```bash
# Approve one suggestion (opens a PR, or commits to the PR branch for suggestions from a PR check)
curl -X POST -H "X-API-Key: $DOCDRIFT_API_KEY" http://localhost:8080/api/suggestions/1/approve

# Edit the suggested text before approving
curl -X PATCH -H "X-API-Key: $DOCDRIFT_API_KEY" -H "Content-Type: application/json" \
  -d '{"suggestedText":"New wording"}' http://localhost:8080/api/suggestions/1

# Approve several suggestions from one repository as a single pull request
curl -X POST -H "X-API-Key: $DOCDRIFT_API_KEY" -H "Content-Type: application/json" \
  -d '{"ids":[1,2,3]}' http://localhost:8080/api/suggestions/approve
```

### 5. Settings, History and Activity
```bash
curl -X PUT -H "X-API-Key: $DOCDRIFT_API_KEY" -H "Content-Type: application/json" \
  -d '{"docPaths":["README.md","docs/"],"watchedBranches":["main"],"minSeverity":"MEDIUM","minConfidence":0.6,"analyzePullRequests":true,"slackWebhookUrl":null,"alertEmail":null}' \
  http://localhost:8080/api/repos/1/settings
curl -H "X-API-Key: $DOCDRIFT_API_KEY" "http://localhost:8080/api/repos/1/history?days=30"   # daily counts by severity
curl -H "X-API-Key: $DOCDRIFT_API_KEY" "http://localhost:8080/api/events?page=0&size=20"     # pushes, PR checks and scans
```

---


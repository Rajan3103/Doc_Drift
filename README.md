# DocDrift: AI-Assisted Documentation Drift Detector

**DocDrift** is an automated, AI-assisted system designed to detect and remediate documentation-code drift in software repositories. Whenever developers push code changes, DocDrift analyzes code diffs against existing prose documentation (READMEs, `/docs` directory, Wiki pages) using Google Gemini's semantic reasoning, generates specific patch suggestions, and presents them in a review dashboard for human approval before opening a fix pull request.

---

## 🚀 Features

- 📡 **API Contract & Schema Drift Detection**: Continuously validates backend Controller routes, request/response DTOs, and validation rules against OpenAPI/Swagger YAML/JSON files and Markdown API tables, proactively flagging **Breaking Changes** with HTTP method badges and endpoint tags.
- 🧠 **Semantic Drift Detection**: Performs AST/symbol-aware reasoning to detect breaking API signatures, removed config keys, outdated CLI parameters, and broken sample code.
- ⚡ **Classification & Severity Scoring**: Categorizes drift events into distinct types (`API_CONTRACT_BREAKING_CHANGE`, `SCHEMA_DRIFT`, `API_SIGNATURE_MISMATCH`, `CONFIG_PROPERTY_CHANGED`, `CLI_USAGE_CHANGED`, `BEHAVIORAL_LOGIC_DRIFT`, `SAMPLE_CODE_BROKEN`) with confidence metrics (0–100%) and severity levels (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`).
- 📁 **Multi-Document & Spec Scanning**: Proactively checks multiple documentation targets (`README.md`, `openapi.yaml`, `swagger.json`, `docs/api.md`, `CONTRIBUTING.md`, `ARCHITECTURE.md`) against commit diffs.
- 💡 **Actionable Patch Generation**: Uses Google's Gemini LLM to generate precise, structured text replacements (`oldText` → `suggestedText`) along with AI reasoning.
- 🛡️ **Human-in-the-Loop Oversight**: No documentation is mutated automatically. Suggestions enter a review queue requiring explicit approval.
- 🎨 **Modern Review Dashboard**: Premium glassmorphic React dashboard with breaking change alert banners, HTTP method badges, severity tags, side-by-side diffs, and one-click approve/reject actions.
- 🗄️ **Auditable Persistence**: Tracks all drift reports, suggested fixes, and reviewer actions in PostgreSQL for full historical auditability.

---

## 🛠️ Technology Stack

### Backend
- **Framework**: Spring Boot (Java 21)
- **Data Access**: Spring Data JPA, Hibernate
- **Database**: PostgreSQL (pgAdmin 4 integration) / H2 in-memory
- **Networking**: Spring WebFlux (`WebClient` for non-blocking GitHub & Gemini API calls)
- **Security**: Spring Security (CORS enabled for local frontend integration)

### Frontend
- **Framework**: React (Vite)
- **Icons**: Lucide React
- **Styling**: Vanilla CSS (Custom dark-mode glassmorphic design system)

### AI & Integrations
- **LLM Engine**: Google Gemini API (`gemini-flash-latest` by default, configurable via `GEMINI_MODEL`)
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
│   │   └── application.properties
│   └── pom.xml
│
├── frontend/                 # Vite + React Dashboard Application
│   ├── src/
│   │   ├── components/       # Header, ReportList, SuggestionCard
│   │   ├── api.js            # Axios/Fetch integration with Spring Boot API
│   │   ├── App.jsx           # Main Dashboard Layout & Repo Tracking Modal
│   │   └── index.css         # Glassmorphism Design System
│   └── package.json
│
└── README.md
```

---

## ⚙️ Setup and Installation

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
3. Set your configuration as **environment variables** (never put real secrets in `application.properties`):

   | Variable | Required | What it is |
   |---|---|---|
   | `DB_URL` | no | JDBC URL, default `jdbc:postgresql://localhost:5432/docdrift` |
   | `DB_USERNAME` | no | Database user, default `postgres` |
   | `DB_PASSWORD` | yes | Database password |
   | `GITHUB_PAT` | yes | GitHub token with **Contents: read & write** and **Pull requests: read & write** on tracked repos (needed to open fix PRs) |
   | `GEMINI_API_KEY` | yes | Google Gemini API key |
   | `GEMINI_MODEL` | no | Gemini model name, default `gemini-flash-latest` |
   | `DOCDRIFT_API_KEY` | yes | Any long random string; the dashboard asks for it and sends it as the `X-API-Key` header |
   | `GITHUB_WEBHOOK_SECRET` | yes | Random string; must match the secret set on the GitHub webhook |
   | `CORS_ALLOWED_ORIGINS` | no | Comma-separated dashboard origins, default `http://localhost:5173` (add your Vercel URL in production) |

   Generate random values with `openssl rand -hex 32`. The API and webhook reject all requests until `DOCDRIFT_API_KEY` and `GITHUB_WEBHOOK_SECRET` are set.

4. On GitHub, add a webhook to each tracked repository (**Settings → Webhooks → Add webhook**):
   - Payload URL: `https://<your-backend>/api/webhooks/github`
   - Content type: `application/json`
   - Secret: the same value as `GITHUB_WEBHOOK_SECRET`
   - Events: **Just the push event**

   Pushes from repositories that are not tracked in DocDrift are ignored.

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
  -H "X-Hub-Signature-256: $SIG" \
  -d "$BODY"
```

### 3. Fetch Drift Reports
```bash
curl -H "X-API-Key: $DOCDRIFT_API_KEY" http://localhost:8080/api/reports
```

### 4. Approve a Suggestion
```bash
curl -X POST -H "X-API-Key: $DOCDRIFT_API_KEY" http://localhost:8080/api/suggestions/1/approve
```

---


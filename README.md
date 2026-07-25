# DocDrift: AI-Assisted Documentation Drift Detector

**DocDrift** is an automated, AI-assisted system designed to detect and remediate documentation-code drift in software repositories. Whenever developers push code changes, DocDrift analyzes code diffs against existing prose documentation (READMEs, `/docs` directory, Wiki pages) using Google Gemini's semantic reasoning, generates specific patch suggestions, and presents them in a review dashboard for human approval before opening a fix pull request.

---

## 🚀 Features

- 🔍 **Automated Drift Detection**: Continuously monitors code changes via GitHub webhooks or REST triggers to spot outdated, inaccurate, or incomplete documentation.
- 💡 **Actionable Patch Generation**: Uses Google's Gemini LLM to generate precise, structured text replacements (`oldText` → `suggestedText`) along with AI reasoning.
- 🛡️ **Human-in-the-Loop Oversight**: No documentation is mutated automatically. Suggestions enter a review queue requiring explicit approval.
- 🎨 **Modern Review Dashboard**: Premium glassmorphic React dashboard for tech leads and reviewers to inspect side-by-side diffs and approve/reject suggestions with one click.
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
- **LLM Engine**: Google Gemini API (`gemini-1.5-flash`)
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
3. Update `Docdrift/src/main/resources/application.properties` with your PostgreSQL username and password:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/docdrift
   spring.datasource.username=postgres
   spring.datasource.password=YOUR_POSTGRES_PASSWORD
   ```
4. Set your API credentials in `application.properties` or environment variables:
   ```properties
   github.api.token=YOUR_GITHUB_PAT
   gemini.api.key=YOUR_GEMINI_API_KEY
   ```

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
```bash
curl -X POST http://localhost:8080/api/repos \
  -H "Content-Type: application/json" \
  -d '{"url":"https://github.com/Rajan3103/Doc_Drift", "name":"Rajan3103/Doc_Drift"}'
```

### 2. Simulate a GitHub Webhook Push Event
```bash
curl -X POST http://localhost:8080/api/webhooks/github \
  -H "Content-Type: application/json" \
  -d '{
    "ref": "refs/heads/main",
    "repository": {"full_name": "Rajan3103/Doc_Drift"},
    "commits": [{"id": "sample-sha-12345", "message": "Updated function signatures"}]
  }'
```

### 3. Fetch Drift Reports
```bash
curl http://localhost:8080/api/reports
```

### 4. Approve a Suggestion
```bash
curl -X POST http://localhost:8080/api/suggestions/1/approve
```

---


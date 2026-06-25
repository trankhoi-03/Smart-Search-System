# Smart System for Advanced Book Searching

A hybrid book search engine that combines **local database retrieval** (keyword + semantic vector search) with **external API aggregation** (Google Books, Open Library, Amazon) and **generative AI** (Gemini) to deliver fast, accurate, and context-aware book search results — including support for natural-language queries, voice input, and image-based (vision) search.

This project was developed as a university thesis: _"Smart System for Advanced Book Searching."_

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [System Architecture](#system-architecture)
- [Tech Stack](#tech-stack)
- [Evaluation Results](#evaluation-results)
- [Prerequisites](#prerequisites)
- [Installation & Setup](#installation--setup)
  - [1. Clone the Repository](#1-clone-the-repository)
  - [2. Backend Setup (Spring Boot)](#2-backend-setup-spring-boot)
  - [3. Frontend Setup (React)](#3-frontend-setup-react)
- [Running the Application](#running-the-application)
- [Project Structure](#project-structure)
- [Known Limitations](#known-limitations)
- [Future Work](#future-work)

---

## Overview

Traditional book search platforms typically rely on simple keyword (`LIKE`-style) matching, which often fails to understand the real intent behind a user's query — especially when the query is informal, contains typos, or is phrased as natural language (e.g., _"what's the second Harry Potter book?"_).

**Smart System for Advanced Book Searching** addresses this by combining:

1. **AI-powered query understanding** — cleaning, correcting, and rewriting raw user input before it's searched.
2. **Dual-engine local retrieval** — keyword search (PostgreSQL Full-Text Search) and semantic search (vector embeddings + cosine similarity) running together.
3. **Asynchronous external aggregation** — querying Google Books, Open Library, and Amazon in parallel as a fallback/extension when local results are insufficient.
4. **Hybrid re-ranking** — merging and scoring results from all sources using a weighted combination algorithm.

The result is a search experience that is both fast (thanks to short-circuiting and caching) and accurate (thanks to hybrid lexical + semantic scoring).

---

## Key Features

- 🔍 **Smart Query Pre-processing** — Uses Gemini (via Few-Shot Prompting) to clean, expand, and rewrite complex or informal queries. Falls back to Regex-based cleaning for short queries to save latency and API cost.
- 🧠 **Hybrid Search Engine** — Combines PostgreSQL Full-Text Search (keyword) with vector similarity search (semantic) using a weighted scoring formula: `(α × Keyword Score) + ((1 − α) × Vector Score)`.
- ⚡ **Asynchronous External API Aggregation** — Calls Google Books, Open Library, and Amazon (via Selenium-based scraping) in parallel using Java `CompletableFuture`, with per-source timeouts and fail-safe fallback.
- 🚀 **Short-Circuit Optimization** — Skips external API calls entirely when local results are already strong (score ≥ 0.85 and ≥ 10 results), reducing unnecessary latency.
- 📷 **Vision Search** — Upload a photo of a book cover; Gemini 2.5 Flash extracts the title/author so it can be used as a search query.
- 📝 **AI-Powered Book Summarization** — Generates short, structured summaries for books using Gemini, with caching (`@Cacheable`) to avoid repeated API calls and a cover-image fallback when author metadata is missing.
- 🔐 **Authentication & Security** — JWT-based stateless authentication, BCrypt password hashing, role-based access control (USER/ADMIN), and CORS/CSRF protections.
- 💻 **Responsive React Frontend** — Handles multimodal input (text, voice, image) and displays results progressively as each external source responds.

---

## System Architecture

The backend follows a **Controller–Service–Repository** pattern.

**Search flow (simplified):**

```
User Query
   │
   ▼
[1] Query Pre-processing  → Gemini (Few-Shot Prompting) or Regex fallback
   │
   ▼
[2] Local Retrieval (parallel)
   ├── Full-Text Search (tsvector / tsquery / ts_rank)
   └── Semantic Search (vector embeddings / cosine similarity)
   │
   ▼
[3] Short-Circuit Check
   ├── Strong local results? → Return immediately
   └── Weak local results?   → Continue to external search
   │
   ▼
[4] Asynchronous External Search (parallel, with timeouts)
   ├── Google Books API
   ├── Open Library API
   └── Amazon (Selenium scraping)
   │
   ▼
[5] Hybrid Re-Ranking
   └── Weighted scoring + author boosting + lexical filters
   │
   ▼
Ranked Results → Frontend (React)
```

**Core entities:** `User`, `Book` (includes vector `embedding` field), `SearchHistory`.

---

## Tech Stack

**Backend**

- Java + Spring Boot (v3.x)
- PostgreSQL (with vector embedding storage)
- Langchain4j (LLM/embedding integration)
- Gemini API (query rewriting, vision search, summarization)
- Selenium WebDriver (Amazon scraping)
- JWT (authentication) + BCrypt (password hashing)
- Maven (dependency management)

**Frontend**

- React.js
- Component-based architecture (`Login.jsx`, `Register.jsx`, `Home.jsx`, `BookDetail.jsx`, protected routes)

---

## Evaluation Results

The system was evaluated on a 45-query ground truth dataset spanning six query categories (special characters, ambiguous terms, standard topics, author+topic, version-specific, and edge cases), measured at cutoff K = 10:

| Metric            | Score  |
| ----------------- | ------ |
| Mean Precision@10 | 0.6567 |
| Mean Recall@10    | 0.5225 |
| Mean NDCG@10      | 0.7027 |
| MRR               | 0.7556 |

These results place the system in the "Good" performance range for standard Information Retrieval benchmarks — an MRR of 0.7556 means a relevant result typically appears within the top 1–2 positions.

---

## Prerequisites

Before setting up the project, make sure you have the following installed:

| Requirement                        | Notes                                                                                                                                  |
| ---------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| **JDK**                            | Java 17 or newer (required by Spring Boot 3.x)                                                                                         |
| **Maven**                          | For backend dependency management and build                                                                                            |
| **Node.js & npm**                  | For running the React frontend                                                                                                         |
| **PostgreSQL**                     | With the **pgvector** extension enabled (required to store and query vector embeddings)                                                |
| **Gemini API Key**                 | Required for query rewriting, vision search, and summarization features. Get one from [Google AI Studio](https://aistudio.google.com/) |
| **IDE (recommended)**              | IntelliJ IDEA for both backend and frontend                                                                                            |
| **Chrome/Chromium + ChromeDriver** | Required for Selenium-based Amazon scraping                                                                                            |

> ⚠️ **Note:** This guide describes the general setup process. Exact configuration values (database name, ports, environment variable names) depend on your local `application.properties` / `application.yml` and frontend `.env` files — adjust the placeholders below to match your own configuration.

---

## Installation & Setup

### 1. Clone the Repository

```bash
git clone https://github.com/trankhoi-03/Smart-Search-System.git
cd Smart-Search-System
```

### 2. Backend Setup (Spring Boot)

**Step 1 — Navigate to the backend folder:**

```bash
cd backend
```

**Step 2 — Set up the PostgreSQL database:**

1. Create a new PostgreSQL database.
2. Enable the `pgvector` extension so the `Book.embedding` column can store vector data:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

**Step 3 — Configure application properties:**

Open `src/main/resources/application.properties` (or `application.yml`) and set your local configuration, for example:

```properties
spring.datasource.url=jdbc:postgresql://localhost:<YOUR_DB_PORT>/<YOUR_DB_NAME>
spring.datasource.username=<YOUR_DB_USERNAME>
spring.datasource.password=<YOUR_DB_PASSWORD>

# Gemini API key for query rewriting, vision search, and summarization
gemini.api.key=<YOUR_GEMINI_API_KEY>

# JWT secret used to sign authentication tokens
jwt.secret=<YOUR_JWT_SECRET>
```

> You can also externalize these as environment variables and reference them with `${VARIABLE_NAME}` syntax in `application.properties`, which is recommended if you plan to deploy the project.

**Step 4 — Install dependencies and build the project:**

```bash
mvn clean install
```

### 3. Frontend Setup (React)

**Step 1 — Navigate to the frontend folder:**

```bash
cd ../frontend
```

**Step 2 — Install dependencies:**

```bash
npm install
```

**Step 3 — Configure the API base URL:**

Create a `.env` file in the frontend root directory and point it to your running backend:

```env
VITE_API_BASE_URL=http://localhost:<YOUR_BACKEND_PORT>
```

(Adjust the variable name/prefix depending on whether the frontend was bootstrapped with Vite or Create React App.)

---

## Running the Application

**Run the backend:**

```bash
cd backend
mvn spring-boot:run
```

The backend should now be running (default Spring Boot port is `8080` unless configured otherwise).

**Run the frontend:**

```bash
cd frontend
npm run dev
```

Open the printed local URL (typically `http://localhost:5173` for Vite or `http://localhost:3000` for Create React App) in your browser to access the application.

---

## Project Structure

```
Smart-Search-System/
├── backend/                  # Spring Boot application
│   ├── src/main/java/...
│   │   ├── controller/       # REST API endpoints
│   │   ├── service/          # Business logic (search, ranking, AI integration)
│   │   ├── repository/       # JPA repositories (PostgreSQL access)
│   │   ├── model/ (entity)   # User, Book, SearchHistory
│   │   └── dto/              # Data Transfer Objects for external API responses
│   ├── src/main/resources/
│   │   └── application.properties
│   └── pom.xml
│
├── frontend/                  # React application
│   ├── src/
│   │   ├── components/
│   │   │   ├── Login.jsx
│   │   │   ├── Register.jsx
│   │   │   ├── Home.jsx
│   │   │   └── BookDetail.jsx
│   │   └── services/         # API call logic
│   └── package.json
│
└── README.md
```

> Folder names above reflect the architecture described in the thesis report. Adjust to match your actual repository layout if it differs.

---

## Known Limitations

- **Amazon scraping fragility** — Since Amazon has no public search API, data is collected via Selenium WebDriver scraping. If Amazon changes its page structure, this source may temporarily fail until the scraper is updated.
- **AI processing latency** — Complex queries that require Gemini-based rewriting may add roughly 1–2 seconds of latency compared to simple keyword queries.
- **Vector computation cost** — Generating embeddings is computationally expensive; without GPU acceleration, large-scale ingestion may be slow.

---

## Future Work

- Multi-language support for non-English queries and book metadata.
- Further optimization of vector embedding generation for larger datasets.

---

_This README is based on the project's thesis report ("Smart System for Advanced Book Searching"). Configuration values marked as placeholders should be replaced with your own environment-specific settings._

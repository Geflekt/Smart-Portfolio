# Smart Portfolio Backend 🚀

The core backend infrastructure powering a highly interactive, AI-driven developer portfolio. Built with **Spring Boot 3** and **Java 21**, this system serves dynamic content, securely processes payments via Razorpay, handles real-time Discord notifications, and powers a RAG (Retrieval-Augmented Generation) AI agent capable of answering questions about my resume and experience.

## ⚙️ Tech Stack & Architecture

* **Core Framework:** Java 21, Spring Boot 3
* **Database & Vector Store:** PostgreSQL (Neon DB) with `pgvector` extension
* **AI Integration:** Spring AI, Groq (Llama-3.3-70b-versatile), Jina AI Embeddings
* **Payment Gateway:** Razorpay API & Webhooks
* **Caching:** Caffeine (Local Cache)
* **Containerization:** Docker (Multi-stage Alpine), Docker Compose
* **API Documentation:** Springdoc OpenAPI / Swagger UI
* **Alerting:** Discord Webhooks

## ✨ Key Features

* **RAG-Powered AI Agent:** Vectorizes PDF resumes upon upload, storing embeddings in pgvector, and uses Groq's LLM to dynamically answer recruiter questions.
* **Secure Command Center:** Protected admin routes using custom `X-Admin-Secret` header validation to manage live projects and AI memory.
* **Payment Processing:** E-to-E integration with Razorpay for handling sponsorship payments with cryptographic webhook signature verification.
* **Real-time Event Logging:** Server events and contact form submissions are routed directly to a private Discord server via webhooks.
* **Optimized Performance:** Configured with HikariCP connection pooling and Caffeine caching to ensure sub-millisecond data retrieval.

---

## 🛠️ Local Development Setup

### 1. Prerequisites
* Java 21 or higher
* Maven 3.8+
* Docker & Docker Compose

### 2. Environment Variables
A template environment file is provided in the repository. Copy the `.env.example` file to `.env` and populate it with your secure API keys and database credentials (Neon DB, Groq, Jina AI, Razorpay, Discord, and your Admin Secret).

```bash
cp .env.example .env
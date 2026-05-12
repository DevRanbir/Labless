<!-- PROJECT IMAGE / BANNER -->
<p align="center">
  <img width="800" alt="Labless CLI" src="templates/index.html" /> <!-- Placeholder for design reference -->
</p>

# 🚀 Labless (Command-Based App)

> Flexible Python-based CLI and Web interface for the Labless ecosystem, optimized for power users and automation pipelines.

---

## 📖 Description

The Labless Command-Based App is a versatile Python implementation designed for flexibility and ease of integration. Whether you prefer a terminal-based workflow or a lightweight web dashboard, this module provides the tools to orchestrate complex email labeling tasks using local or cloud-based AI.

What makes it unique:
- **Ollama Integration** – Full support for running local models (like `gpt-oss`) for 100% private processing.
- **Flask Web UI** – A minimalist web interface to monitor and trigger pipelines.
- **Pipeline Orchestrator** – A modular stage-based system (Extract → Transform → Load).
- **Docker Ready** – Containerized support for easy deployment on servers or NAS.
- **CLI Power** – Granular control over processing batches and model parameters.

---

## ✨ Features

- **Local LLM Support** – Connect seamlessly with Ollama for zero-cost, private labeling.
- **Modular Pipeline** – Customizable stages for syncing, transforming, and loading email data.
- **Web Dashboard** – Monitor metrics and trigger syncs from any browser.
- **Docker Compose** – Spin up the entire stack (App + DB) with a single command.
- **SQLAlchemy Backend** – Robust database management for millions of email records.
- **Extensible Factory** – Easily add new LLM providers or email services.

---

## 🧠 Tech Stack

**Backend & CLI**
- Python 3.10+
- SQLAlchemy (ORM)
- Pandas (Data Processing)
- Click (CLI Framework)

**Web Interface**
- Flask
- Jinja2 Templates
- Bootstrap 5

**DevOps**
- Docker & Docker Compose
- Pytest (Testing)

---

## 🏗️ Architecture / Workflow

```text
CLI/Web → Orchestrator → Sync Stage → Transform (AI) Stage → Load (DB) Stage → Metrics
```

---

## ⚙️ Installation & Setup

```bash
# Navigate to the Python project
cd command-based-app

# Create a virtual environment
python -m venv venv
source venv/bin/activate  # Or venv\Scripts\activate on Windows

# Install dependencies
pip install -e .

# Run the web server
./start_web_server.bat
```

---

## 🔐 Environment Variables

Create a `.env` file in the `command-based-app` directory:

```env
LLM_SERVICE=Ollama  # Options: Ollama, OpenAI, Gemini, Groq
DATABASE_PATH=email_pipeline.db
OLLAMA_BASE_URL=http://localhost:11434/v1
OLLAMA_MODEL=llama3
```

---

## 🧪 Usage

* **Step 1:** Configure your `.env` file with your preferred LLM service.
* **Step 2:** Run `python label_transactions.py` to start the CLI pipeline.
* **Step 3:** (Optional) Launch the web UI using `python web_app.py`.
* **Step 4:** Visit `http://localhost:5000` to view processing metrics.
* **Step 5:** Use the CLI to schedule recurring sync jobs.

---

## 📂 Project Structure

```text
command-based-app/
├── email_labeler/       # Core package
│   ├── pipeline/        # Stage implementations (Extract/Transform/Load)
│   └── database.py      # SQLAlchemy models
├── templates/           # Flask Web UI
├── examples/            # Sample configurations
├── Dockerfile           # Container definition
└── web_app.py           # Flask entry point
```

---

## 🚧 Future Improvements

- [ ] Add support for LangChain for complex multi-step reasoning
- [ ] Implement a REST API for remote control
- [ ] Add more sophisticated data visualization in the Web UI
- [ ] Support for multiple email backends (IMAP/POP3)

---

## 👥 Team / Author

* **Name:** DevRanbir
* **GitHub:** [https://github.com/DevRanbir](https://github.com/DevRanbir)

---

## 📜 License

This project is licensed under the MIT License.

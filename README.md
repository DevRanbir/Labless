<!-- PROJECT IMAGE / BANNER -->
<p align="center">
  <img width="100%" alt="Labless Welcome Screen" src="Images/WelcomeScreen.png" />
</p>

<p align="center">
  <br>
  <a href="https://github.com/DevRanbir/Labless/releases/tag/v2-beta">
    <img width="80%" src="https://img.shields.io/badge/Download_v2--beta-2ea44f?style=for-the-badge&logo=github&logoColor=white&labelColor=00C7B7" alt="Download v2-beta">
  </a>
  <br>
</p>


# 🚀 Labless

> AI-powered intelligent email labeling and organization platform designed to automate your inbox workflow and eliminate manual sorting.

---

## 📖 Description

Labless is an innovative productivity application that uses Large Language Models (LLMs) to automatically categorize and label your emails. By analyzing your inbox in real-time, it identifies transactions, bills, personal messages, and more, allowing you to focus on what matters while the AI handles the organization.

What makes it unique:
- **Intelligent Categorization** – Uses LLMs (Groq/Llama) to understand context, not just keywords.
- **Cross-Platform Support** – Available as a sleek JavaFX GUI or a robust Python CLI.
- **Privacy-First** – Processes data locally where possible; your login tokens never leave your machine.
- **Portable Design** – Self-contained Windows release that runs without needing a pre-installed Java Runtime.
- **Real-time Feedback** – Monitor labeling progress and AI reasoning live.

---

## ✨ Features

- **AI Email Labeling** – Practice zero-touch inbox management with AI-driven categorization.
- **Transaction Detection** – Automatically identifies and tags financial transactions and receipts.
- **Multi-Model Support** – Connect with Groq, OpenAI, or local Ollama instances.
- **Portable Executable** – Download and run immediately with our bundled JRE releases.
- **Smart Onboarding** – Simple OAuth2 flow to connect your Gmail safely.
- **Detailed Progress** – Visual feedback for large-scale inbox processing jobs.

<p align="center">
  <img width="90%" alt="Labless Workspace" src="Images/MailsList.png" />
</p>

---

## 🧠 Tech Stack

**Java GUI App**
- Java 17+ / JavaFX
- Google Gmail API
- Groq / LLM Integration
- SQLite (Local Cache)
- Maven (Build System)

**Command-Based App**
- Python 3.10+
- Flask (Web Interface)
- SQLAlchemy / SQLite
- Ollama / OpenAI Integration
- Docker Support

**Deployment**
- GitHub Releases
- WiX Toolset (MSI Installers)

---

## 🏗️ Architecture / Workflow

```text
Gmail API → Fetch Inbox → Transaction Detection → LLM Categorization → Apply Labels → Update Local Cache
```

---

## ⚙️ Installation & Setup

### Java GUI (Recommended)
```bash
# Clone the repository
git clone https://github.com/DevRanbir/Labless.git

# Navigate to project
cd Labless/java-gui-mail-labeler

# Build and Run
mvn clean javafx:run
```

### Command-Based App
```bash
# Navigate to project
cd Labless/command-based-app

# Install dependencies
pip install -r requirements.txt

# Start Web Server
python web_app.py
```

---

## 🔐 Environment Variables / Configuration

For the Java GUI, place your `credentials.json` in `src/main/resources/` and create an `app-config.yaml` in the root.

For the Python app, create a `.env` file:
```env
LLM_SERVICE=Ollama
DATABASE_PATH=email_pipeline.db
OLLAMA_BASE_URL=http://localhost:11434/v1
```

---

## 🧪 Usage

* **Step 1:** Connect your Gmail account via the secure OAuth onboarding.
* **Step 2:** Select the folders or labels you want the AI to monitor.
* **Step 3:** Choose your preferred AI model (Groq is recommended for speed).
* **Step 4:** Start the "Labeling Job" and watch your inbox get organized.
* **Step 5:** Review the AI's reasoning for each label directly in the app.

---

## 🎥 Demo

* **Download Portable Release:** [Releases Page](https://github.com/DevRanbir/Labless/releases)

---

## 📂 Project Structure

```text
Labless/
├── java-gui-mail-labeler/    # Premium JavaFX Desktop Application
├── command-based-app/        # Python CLI and Flask Web Interface
├── Labless-Portable/         # Bundled standalone Windows release
├── build-release.ps1         # Automation script for building releases
└── README.md                 # Root documentation
```

---

## 🚧 Future Improvements

- [ ] Add support for Outlook / Office 365
- [ ] Implement local-only LLM processing for 100% privacy
- [ ] Add advanced search with natural language queries
- [ ] Create a mobile companion app
- [ ] Add multi-account support

---

## 👥 Team / Author

* **Name:** DevRanbir
* **GitHub:** [https://github.com/DevRanbir](https://github.com/DevRanbir)

---

## 📜 License

This project is licensed under the MIT License.

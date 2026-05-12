# Labless - AI-Powered Email Labeling

**Intelligent Email Management Made Simple**

Labless is a desktop application that automatically categorizes and labels your Gmail emails using AI. Choose between a modern JavaFX GUI or a command-line interface.

## 📁 Project Structure

This repository contains two implementations:

### 1. **Labless GUI** (Recommended)
**Location:** `java-gui-mail-labeler/`

Modern JavaFX desktop application with:
- Beautiful dark-mode interface
- Real-time progress tracking
- Labeling history
- Auto-refresh every 2 minutes
- User-friendly onboarding

**Quick Start:**
```bash
cd java-gui-mail-labeler
mvn clean javafx:run
```

**Documentation:** See [java-gui-mail-labeler/README.md](java-gui-mail-labeler/README.md)

### 2. **Command-Based App** (Legacy)
**Location:** `command-based-app/`

Python-based command-line tool for email labeling.

**Quick Start:**
```bash
cd command-based-app
python label_transactions.py
```

**Documentation:** See [command-based-app/README.md](command-based-app/README.md)

## � Security First

**Before cloning or contributing, please read:**
- 📖 [SECURITY.md](SECURITY.md) - Security guidelines and best practices
- 🚀 [SAFE_GIT_INIT.md](SAFE_GIT_INIT.md) - Safe git initialization guide
- ⚙️ [SETUP.md](java-gui-mail-labeler/SETUP.md) - Secure setup instructions

**Quick Security Check:**
```bash
# Run before committing
verify-security.bat
```

**Protected Files (Never Commit):**
- ✅ `credentials.json` - OAuth secrets
- ✅ `token.json` - User tokens
- ✅ `app-config.yaml` - API keys
- ✅ `*.db` - User data
- ✅ `.env` - Environment variables

## �🚀 Features

### AI-Powered Categorization
- Automatic email labeling using Groq AI
- Intelligent transaction detection
- Custom category support
- Batch processing

### Gmail Integration
- OAuth 2.0 authentication
- Automatic label creation
- Smart archiving
- Real-time sync

### Smart Processing
- Rate limit handling with exponential backoff
- User-configurable email count
- Background processing
- State persistence

## 🎯 Quick Start (GUI)

1. **Prerequisites**
   - Java 17+
   - Maven 3.6+
   - Gmail account
   - Groq API key (free at https://console.groq.com)

2. **Run**
   ```bash
   cd java-gui-mail-labeler
   mvn clean javafx:run
   ```

3. **Setup**
   - Choose theme (Dark Mode recommended)
   - Connect Gmail account
   - Define categories
   - Enter Groq API key

4. **Start Labeling**
   - Click sparkles icon
   - Enter email count
   - Click "Start Labeling"
   - Watch the magic happen!

## 📊 Default Categories

**Personal:**
- Account Security
- Bills Payments
- Receipts Invoices
- Travel Bookings
- Transaction

**Work Related:**
- University
- Work
- Action Required
- Events Invitations
- Certificates

**Miscellaneous:**
- Promotions
- Subscriptions
- Alerts
- Notes
- Spam / Low Priority

## 🎨 Screenshots

### Main Interface
- Three-column layout with smooth animations
- Corner flag unread indicators
- Real-time email list updates

### Labeling Panel
- Progress tracking
- Results display with AI explanations
- Start/Stop controls

### History View
- Table view of all labeled emails
- Category badges
- Timestamps and explanations

## 🔧 Configuration

### Groq API (Default)
- **Model:** llama-3.1-8b-instant
- **Rate Limit:** 6000 tokens/minute (free tier)
- **Get API Key:** https://console.groq.com

### Auto-Refresh
- **Interval:** 2 minutes
- **Configurable:** Yes

### Processing
- **Default:** 100 emails
- **Maximum:** 10,000 emails
- **Batch Size:** 50 emails

## 📚 Documentation

### GUI Application
- [README](java-gui-mail-labeler/README.md) - Complete guide
- [IMPROVEMENTS](java-gui-mail-labeler/IMPROVEMENTS.md) - Detailed feature documentation
- [QUICK_START_GUIDE](java-gui-mail-labeler/QUICK_START_GUIDE.md) - Step-by-step tutorial

### Command-Based App
- [README](command-based-app/README.md) - Python CLI guide
- [DEPLOYMENT](command-based-app/DEPLOYMENT.md) - Deployment instructions
- [CONFIGURATION](command-based-app/docs/CONFIGURATION.md) - Configuration guide

## 🛠️ Technology Stack

### GUI Application
- **Framework:** JavaFX 21
- **Build:** Maven
- **Database:** SQLite
- **HTTP:** OkHttp 4.12.0
- **JSON:** Gson 2.10.1
- **Gmail API:** Google API Client 2.2.0

### Command-Based App
- **Language:** Python 3.8+
- **Framework:** Flask (web interface)
- **Database:** SQLite
- **Gmail API:** google-api-python-client

## 🎯 Use Cases

### Personal Email Management
- Automatically categorize bills and receipts
- Track bank transactions
- Organize travel bookings
- Filter promotions and subscriptions

### Work Email Organization
- Separate work from personal
- Track action items
- Organize events and invitations
- Manage university communications

### Inbox Zero
- Auto-archive low-priority emails
- Smart categorization
- Quick email triage
- Maintain organized inbox

## 🔒 Security & Privacy

- **OAuth 2.0:** Secure Gmail authentication
- **Local Storage:** All data stored locally
- **No Data Sharing:** Your emails never leave your machine
- **API Keys:** Stored in local configuration files
- **Open Source:** Transparent and auditable code

## 🚧 Troubleshooting

### Rate Limit Errors
Increase wait time or process fewer emails at once

### Labels Not Showing
Click refresh button or check Gmail web interface

### Configuration Issues
Rerun onboarding or check config files

### Video Not Playing
Ensure video files exist in resources folder

## 🤝 Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## 📝 License

[Add your license here]

## 🙏 Credits

- **AI Provider:** Groq (https://groq.com)
- **Email API:** Gmail API
- **Framework:** JavaFX & Python Flask

---

## 🎬 Getting Started

**Recommended:** Start with the GUI application for the best experience!

```bash
cd java-gui-mail-labeler
mvn clean javafx:run
```

**Labless** - Making email management effortless with AI ✨

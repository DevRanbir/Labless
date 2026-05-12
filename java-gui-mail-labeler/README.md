# Labless

**Intelligent Email Labeling Application Powered by AI**

Labless is a modern JavaFX desktop application that automatically categorizes and labels your Gmail emails using AI. It features a beautiful dark-mode interface, intelligent email processing, and seamless Gmail integration.

## Features

### 🤖 AI-Powered Labeling
- Automatic email categorization using Groq AI (llama-3.1-8b-instant)
- Intelligent transaction detection for bank emails
- Custom category support
- Batch processing with rate limit handling

### 📧 Gmail Integration
- OAuth 2.0 authentication
- Automatic label creation and application
- Smart archiving for low-priority emails
- Real-time sync with Gmail

### 🎨 Modern UI
- Clean, dark-mode interface
- Three-column animated layout
- Real-time progress tracking
- Labeling history with database persistence
- Corner flag unread indicators

### ⚡ Smart Features
- Auto-refresh every 2 minutes
- User-configurable email count
- Start/Stop controls
- Batch processing (50 emails per batch)
- Exponential backoff for rate limiting

## Quick Start

### For End Users (No Java Required)

**Currently, a standalone .exe is not available due to JavaFX packaging limitations.**

**To run Labless, you need:**
1. Java 17+ installed ([Download here](https://www.oracle.com/java/technologies/downloads/))
2. Download the project
3. Run `run_labless.bat`

**Quick Start:**
```bash
# Double-click or run:
run_labless.bat
```

The launcher automatically finds Maven and starts the application.

### For Developers

#### Prerequisites
- Java 17 or higher
- Maven 3.6+
- Gmail account
- Groq API key (free at https://console.groq.com)

#### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd java-gui-mail-labeler
   ```

2. **Run the application**
   ```bash
   mvn clean javafx:run
   ```

### First Time Setup

1. **Choose Theme**: Select Dark Mode or System Default (Light mode is disabled)
2. **Authenticate Gmail**: Connect your Google account via OAuth
3. **Define Categories**: Choose from default categories or add custom ones
4. **Configure AI**: Enter your Groq API key (get it from https://console.groq.com)

### Default Categories

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

## Usage

### Labeling Emails

1. Click the **sparkles icon** in the sidebar or top navigation
2. Enter the number of emails to process (default: 100)
3. Click **Start Labeling**
4. Watch real-time progress and results
5. Click **Stop** to interrupt at any time

### Viewing History

1. Click the **History** button in the top navigation
2. View all previously labeled emails with:
   - Subject and sender
   - Applied category
   - AI explanation
   - Timestamp

### Managing Labels

- Labels are automatically created in Gmail
- View labeled emails in the mail list
- Archived emails are removed from inbox
- Sync with Gmail using the refresh button

## Configuration

### Groq API
- **Provider:** Groq (default)
- **Model:** llama-3.1-8b-instant
- **Rate Limit:** 6000 tokens/minute (free tier)
- **Wait Time:** 1.5 seconds between requests

### Auto-Refresh
- **Interval:** 2 minutes
- **Configurable in:** `MainApplication.java`

### Email Processing
- **Default count:** 100 emails
- **Maximum count:** 10,000 emails
- **Batch size:** 50 emails per batch
- **Max retries:** 3 per email

## Architecture

### Technology Stack
- **Framework:** JavaFX 21
- **Build Tool:** Maven
- **Database:** SQLite
- **HTTP Client:** OkHttp 4.12.0

## Building Release Executables

### Quick Build

Run the interactive build menu:
```bash
build-quick.bat
```

Choose from:
1. **Windows Installer** - Professional .exe installer (~200 MB)
2. **Portable App** - No installation required (~200 MB)
3. **JAR File** - Requires Java 17+ (~50 MB)
4. **Run Development** - Quick testing

### Build Scripts

#### Create Windows Installer:
```bash
build-exe.bat
```
Output: `target\installer\Labless-0.1.0.exe`

#### Create Portable Application:
```bash
build-portable.bat
```
Output: `target\Labless-Portable\Labless.exe`

#### Create JAR Only:
```bash
mvn clean package -DskipTests
```
Output: `target\labless-0.1.0-SNAPSHOT.jar`

### Documentation
- **[BUILD_RELEASE.md](BUILD_RELEASE.md)** - Complete build guide
- **[BUILD_SUMMARY.md](BUILD_SUMMARY.md)** - Quick reference
- **[RELEASE_NOTES.md](RELEASE_NOTES.md)** - Release documentation

**Note:** Built executables include Java runtime - no Java installation required for end users!
- **JSON:** Gson 2.10.1
- **Gmail API:** Google API Client 2.2.0

### Project Structure
```
src/main/java/com/labless/
├── app/              # Application entry point
├── ui/               # UI screens and components
├── service/          # Business logic and API clients
├── model/            # Data models
├── database/         # Database management
├── gmail/            # Gmail API integration
└── processor/        # Email processing logic

src/main/resources/
├── styles/           # CSS stylesheets
├── videos/           # Logo and loading videos
└── icons/            # UI icons
```

## Features in Detail

### Transaction Detection
Emails are categorized as "Transaction" only if they meet ALL criteria:
- From a bank (HDFC, ICICI, SBI, Axis, Kotak, Paytm, PhonePe, etc.)
- Contains transaction keywords (debited, credited, withdrawn, deposited)
- NOT promotional (no offers, rewards, cashback, etc.)

Otherwise, they're categorized as "Bills & Payments"

### Rate Limiting
- Automatic retry with exponential backoff
- Up to 3 retries per email
- Smart wait time parsing from API errors
- Graceful degradation after max retries

### State Persistence
- All UI components are instance variables
- State persists when navigating away
- Progress continues in background
- No UI/thread desynchronization

## Troubleshooting

### Rate Limit Errors
**Solution:** Increase wait time to 15 seconds or process fewer emails

### Labels Not Showing
**Solution:** Click refresh button or check Gmail web interface

### All Emails Show as Unread
**Solution:** Check Gmail - they may actually be unread. Click refresh to sync.

### Configuration Not Loading
**Solution:** Rerun onboarding or check `config/app-config.yaml`

## Documentation

- **Complete Improvements:** See [IMPROVEMENTS.md](IMPROVEMENTS.md) for detailed feature documentation
- **Quick Start Guide:** See [QUICK_START_GUIDE.md](QUICK_START_GUIDE.md) for step-by-step instructions

## Development

### Building
```bash
mvn clean compile
```

### Running Tests
```bash
mvn test
```

### Packaging
```bash
mvn package
```

## License

[Add your license here]

## Credits

- **AI Provider:** Groq (https://groq.com)
- **Email API:** Gmail API
- **Framework:** JavaFX

---

**Labless** - Making email management effortless with AI


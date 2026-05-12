# Quick Start: Web Interface

Get the Gmail LLM Labeler web interface running in 5 minutes!

## 🎯 Prerequisites

- Python 3.9+ installed
- Gmail API credentials (`credentials.json`)
- API key (Groq/OpenAI/Gemini) in `.env` file

## 🚀 Steps

### 1. Install Flask

```bash
pip install flask
```

### 2. Start the Web Server

**Windows:**
```bash
start_web_server.bat
```

**Linux/Mac:**
```bash
chmod +x start_web_server.sh
./start_web_server.sh
```

### 3. Open Dashboard

Open your browser to: **http://localhost:5000**

## 🎮 Using the Dashboard

### Start a Job

1. **Select Job Type**:
   - **Complete Workflow**: Transaction labeler + LLM pipeline (recommended)
   - **Transaction Only**: Fast rule-based Axis Bank transaction labeling
   - **LLM Pipeline Only**: AI-powered categorization

2. **Configure Limits**:
   - **Transaction Limit**: Max Axis Bank emails to process (default: 500)
   - **Pipeline Limit**: Max emails for LLM categorization (default: 200)

3. **Dry Run** (optional):
   - Check this to simulate without making changes
   - Great for testing!

4. **Click "Start Job"**

### Monitor Progress

- **Active Jobs** section shows running jobs with live log updates
- **Recent Jobs** section shows completed job history
- Click **"View Logs"** on any job to see full output

### View Metrics

Click **"View Metrics"** to see:
- Total emails processed
- Success/failure rates
- Category distribution
- Processing times

## 📱 Access from Other Devices

### Same Network

1. Find your computer's IP address:
   ```bash
   # Windows
   ipconfig
   
   # Mac/Linux
   ifconfig
   ```

2. Access from phone/tablet: `http://YOUR_IP:5000`

### Internet Access (ngrok)

```bash
# Install ngrok
# Download from: https://ngrok.com/download

# Start tunnel
ngrok http 5000

# Use the provided URL (e.g., https://abc123.ngrok.io)
```

## 🔄 Automated Scheduling

### Windows Task Scheduler

1. Open Task Scheduler
2. Create Basic Task
3. Name: "Gmail Labeler Daily"
4. Trigger: Daily at 2:00 AM
5. Action: Start a program
6. Program: `curl`
7. Arguments:
   ```
   -X POST http://localhost:5000/api/jobs/start -H "Content-Type: application/json" -d "{\"type\":\"complete\",\"dry_run\":false}"
   ```

### Linux/Mac Cron

```bash
crontab -e
```

Add:
```cron
# Run every day at 2 AM
0 2 * * * curl -X POST http://localhost:5000/api/jobs/start -H "Content-Type: application/json" -d '{"type":"complete","dry_run":false}'
```

## 🐳 Docker (Optional)

For a containerized setup:

```bash
# Build and run
docker-compose up -d

# View logs
docker-compose logs -f

# Stop
docker-compose down
```

## 🔒 Security Tips

### Add Password Protection

1. Install Flask-HTTPAuth:
   ```bash
   pip install Flask-HTTPAuth
   ```

2. Edit `web_app.py` and add at the top:
   ```python
   from flask_httpauth import HTTPBasicAuth
   from werkzeug.security import generate_password_hash, check_password_hash
   
   auth = HTTPBasicAuth()
   
   users = {
       "admin": generate_password_hash("your-secure-password")
   }
   
   @auth.verify_password
   def verify_password(username, password):
       if username in users:
           return check_password_hash(users.get(username), password)
       return False
   ```

3. Add `@auth.login_required` decorator to routes:
   ```python
   @app.route("/")
   @auth.login_required
   def index():
       return render_template("index.html")
   ```

### Enable HTTPS

Use a reverse proxy like Caddy (automatic HTTPS):

```bash
# Install Caddy
sudo apt install caddy

# Configure
sudo nano /etc/caddy/Caddyfile
```

Add:
```
your-domain.com {
    reverse_proxy localhost:5000
}
```

## 📊 API Endpoints

You can also control the system via API:

### Get Status
```bash
curl http://localhost:5000/api/status
```

### Start Job
```bash
curl -X POST http://localhost:5000/api/jobs/start \
  -H "Content-Type: application/json" \
  -d '{
    "type": "complete",
    "transaction_limit": 500,
    "pipeline_limit": 200,
    "dry_run": false
  }'
```

### Get Job Details
```bash
curl http://localhost:5000/api/jobs/<job_id>
```

### View Logs
```bash
curl http://localhost:5000/api/logs/<filename>
```

### Get Metrics
```bash
curl http://localhost:5000/api/metrics
```

## 🐛 Troubleshooting

### Port 5000 Already in Use

Change the port in `web_app.py`:
```python
app.run(host="0.0.0.0", port=8080, debug=True)
```

### Can't Access from Other Devices

1. Check firewall settings
2. Ensure server is running on `0.0.0.0` (not `127.0.0.1`)
3. Verify devices are on same network

### Jobs Not Starting

1. Check logs in `/logs` directory
2. Verify credentials.json and token.json exist
3. Check .env file has API keys
4. Ensure Python scripts are executable

## 🎉 Next Steps

- Set up automated daily runs
- Configure custom categories in YAML config
- Add more banks to transaction patterns
- Deploy to cloud for 24/7 access
- Set up monitoring and alerts

## 📚 More Information

- Full deployment guide: `DEPLOYMENT.md`
- Transaction labeling: See README.md
- Configuration: `docs/CONFIGURATION.md`

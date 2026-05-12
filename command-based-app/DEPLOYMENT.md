# Gmail LLM Labeler - Deployment Guide

Complete guide for deploying the Gmail LLM Labeler with web interface.

## 🚀 Quick Start (Local)

### Windows

```bash
# Start the web server
start_web_server.bat

# Access dashboard at: http://localhost:5000
```

### Linux/Mac

```bash
# Make script executable
chmod +x start_web_server.sh

# Start the web server
./start_web_server.sh

# Access dashboard at: http://localhost:5000
```

## 📦 Installation

### 1. Install Dependencies

```bash
# Install Flask
pip install flask

# Or install all dependencies
pip install -e .
```

### 2. Setup Credentials

Ensure you have:
- `credentials.json` (Gmail API OAuth credentials)
- `token.json` (Generated after first authentication)
- `.env` file with API keys

## 🐳 Docker Deployment

### Local Docker

```bash
# Build and run with Docker Compose
docker-compose up -d

# View logs
docker-compose logs -f

# Stop
docker-compose down
```

### Manual Docker Build

```bash
# Build image
docker build -t gmail-llm-labeler .

# Run container
docker run -d \
  -p 5000:5000 \
  -v $(pwd)/credentials.json:/app/credentials.json:ro \
  -v $(pwd)/token.json:/app/token.json \
  -v $(pwd)/.env:/app/.env:ro \
  -v $(pwd)/logs:/app/logs \
  --name gmail-labeler \
  gmail-llm-labeler
```

## ☁️ Cloud Deployment Options

### Option 1: Railway.app

1. **Create account** at [railway.app](https://railway.app)

2. **Install Railway CLI**:
   ```bash
   npm install -g @railway/cli
   ```

3. **Deploy**:
   ```bash
   railway login
   railway init
   railway up
   ```

4. **Set environment variables** in Railway dashboard:
   - Add your `.env` variables
   - Upload `credentials.json` as a file

5. **Access**: Railway provides a public URL

### Option 2: Render.com

1. **Create account** at [render.com](https://render.com)

2. **Create new Web Service**:
   - Connect your GitHub repository
   - Build Command: `pip install -e .`
   - Start Command: `python web_app.py`

3. **Environment Variables**:
   - Add all variables from `.env`
   - Upload `credentials.json` via Render dashboard

4. **Deploy**: Render auto-deploys on git push

### Option 3: Heroku

1. **Install Heroku CLI**:
   ```bash
   # Windows
   choco install heroku-cli
   
   # Mac
   brew tap heroku/brew && brew install heroku
   ```

2. **Create Heroku app**:
   ```bash
   heroku login
   heroku create gmail-llm-labeler
   ```

3. **Set environment variables**:
   ```bash
   heroku config:set GROQ_API_KEY=your_key_here
   # Add all other env vars
   ```

4. **Deploy**:
   ```bash
   git push heroku main
   ```

### Option 4: AWS EC2

1. **Launch EC2 instance** (Ubuntu 22.04)

2. **SSH into instance**:
   ```bash
   ssh -i your-key.pem ubuntu@your-ec2-ip
   ```

3. **Install dependencies**:
   ```bash
   sudo apt update
   sudo apt install python3-pip git
   ```

4. **Clone repository**:
   ```bash
   git clone your-repo-url
   cd gmail-llm-labeler
   ```

5. **Install Python packages**:
   ```bash
   pip3 install -e .
   ```

6. **Setup credentials**:
   ```bash
   # Upload credentials.json and .env
   scp -i your-key.pem credentials.json ubuntu@your-ec2-ip:~/gmail-llm-labeler/
   scp -i your-key.pem .env ubuntu@your-ec2-ip:~/gmail-llm-labeler/
   ```

7. **Run with systemd** (persistent service):
   ```bash
   sudo nano /etc/systemd/system/gmail-labeler.service
   ```

   Add:
   ```ini
   [Unit]
   Description=Gmail LLM Labeler Web Service
   After=network.target

   [Service]
   Type=simple
   User=ubuntu
   WorkingDirectory=/home/ubuntu/gmail-llm-labeler
   ExecStart=/usr/bin/python3 /home/ubuntu/gmail-llm-labeler/web_app.py
   Restart=always
   RestartSec=10

   [Install]
   WantedBy=multi-user.target
   ```

   Enable and start:
   ```bash
   sudo systemctl enable gmail-labeler
   sudo systemctl start gmail-labeler
   sudo systemctl status gmail-labeler
   ```

8. **Setup nginx reverse proxy**:
   ```bash
   sudo apt install nginx
   sudo nano /etc/nginx/sites-available/gmail-labeler
   ```

   Add:
   ```nginx
   server {
       listen 80;
       server_name your-domain.com;

       location / {
           proxy_pass http://localhost:5000;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
       }
   }
   ```

   Enable:
   ```bash
   sudo ln -s /etc/nginx/sites-available/gmail-labeler /etc/nginx/sites-enabled/
   sudo nginx -t
   sudo systemctl restart nginx
   ```

### Option 5: Google Cloud Run

1. **Install gcloud CLI**

2. **Build and push image**:
   ```bash
   gcloud builds submit --tag gcr.io/YOUR_PROJECT_ID/gmail-labeler
   ```

3. **Deploy**:
   ```bash
   gcloud run deploy gmail-labeler \
     --image gcr.io/YOUR_PROJECT_ID/gmail-labeler \
     --platform managed \
     --region us-central1 \
     --allow-unauthenticated
   ```

4. **Set environment variables**:
   ```bash
   gcloud run services update gmail-labeler \
     --set-env-vars GROQ_API_KEY=your_key
   ```

## 🔒 Security Considerations

### 1. Authentication

Add basic authentication to `web_app.py`:

```python
from flask_httpauth import HTTPBasicAuth
from werkzeug.security import check_password_hash, generate_password_hash

auth = HTTPBasicAuth()

users = {
    "admin": generate_password_hash("your-secure-password")
}

@auth.verify_password
def verify_password(username, password):
    if username in users and check_password_hash(users.get(username), password):
        return username

@app.route("/")
@auth.login_required
def index():
    return render_template("index.html")
```

Install:
```bash
pip install Flask-HTTPAuth
```

### 2. HTTPS

Use a reverse proxy (nginx/Caddy) with Let's Encrypt:

```bash
# Install Caddy
sudo apt install caddy

# Configure Caddy
sudo nano /etc/caddy/Caddyfile
```

Add:
```
your-domain.com {
    reverse_proxy localhost:5000
}
```

Caddy automatically handles HTTPS certificates!

### 3. Environment Variables

Never commit:
- `credentials.json`
- `token.json`
- `.env`

These are already in `.gitignore`.

## 📊 Monitoring

### View Logs

```bash
# Docker
docker-compose logs -f

# Local
tail -f logs/*.log

# Systemd (Linux)
sudo journalctl -u gmail-labeler -f
```

### Health Check

```bash
curl http://localhost:5000/api/status
```

## 🔄 Scheduled Jobs

### Option 1: Cron (Linux)

```bash
crontab -e
```

Add:
```cron
# Run every day at 2 AM
0 2 * * * curl -X POST http://localhost:5000/api/jobs/start -H "Content-Type: application/json" -d '{"type":"complete","dry_run":false}'
```

### Option 2: Windows Task Scheduler

1. Open Task Scheduler
2. Create Basic Task
3. Trigger: Daily at 2:00 AM
4. Action: Start a program
5. Program: `curl`
6. Arguments: `-X POST http://localhost:5000/api/jobs/start -H "Content-Type: application/json" -d "{\"type\":\"complete\",\"dry_run\":false}"`

### Option 3: Built-in Scheduler (Add to web_app.py)

```python
from apscheduler.schedulers.background import BackgroundScheduler

scheduler = BackgroundScheduler()

def scheduled_job():
    # Trigger complete workflow
    job_id = f"scheduled_{int(time.time())}"
    active_jobs[job_id] = {
        "id": job_id,
        "type": "complete",
        "status": "queued",
        "dry_run": False,
        "created_time": datetime.now().isoformat(),
    }
    thread = threading.Thread(
        target=JobRunner.run_complete_workflow,
        args=(job_id, "examples/pipeline_config_gemini.yaml", 500, 200, 40, False),
    )
    thread.daemon = True
    thread.start()

# Schedule job every day at 2 AM
scheduler.add_job(scheduled_job, 'cron', hour=2, minute=0)
scheduler.start()
```

Install:
```bash
pip install APScheduler
```

## 🐛 Troubleshooting

### Port Already in Use

```bash
# Find process using port 5000
# Windows
netstat -ano | findstr :5000

# Linux/Mac
lsof -i :5000

# Kill process
# Windows
taskkill /PID <PID> /F

# Linux/Mac
kill -9 <PID>
```

### Permission Errors

```bash
# Linux/Mac
chmod +x start_web_server.sh
chmod 644 credentials.json
chmod 644 token.json
```

### Docker Issues

```bash
# Rebuild without cache
docker-compose build --no-cache

# View container logs
docker-compose logs -f

# Restart container
docker-compose restart
```

## 📱 Mobile Access

Access from any device on your network:

1. **Find your local IP**:
   ```bash
   # Windows
   ipconfig
   
   # Linux/Mac
   ifconfig
   ```

2. **Access from mobile**: `http://YOUR_LOCAL_IP:5000`

3. **For external access**, use:
   - ngrok: `ngrok http 5000`
   - Cloudflare Tunnel
   - Port forwarding on your router

## 🎯 Production Checklist

- [ ] Set strong authentication password
- [ ] Enable HTTPS
- [ ] Set up automated backups
- [ ] Configure monitoring/alerts
- [ ] Set up scheduled jobs
- [ ] Test disaster recovery
- [ ] Document custom configurations
- [ ] Set up log rotation
- [ ] Configure firewall rules
- [ ] Enable rate limiting

## 📞 Support

For issues or questions:
1. Check logs in `/logs` directory
2. Review error messages in web dashboard
3. Check GitHub issues
4. Review this deployment guide

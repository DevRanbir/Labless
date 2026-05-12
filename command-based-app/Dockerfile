# Gmail LLM Labeler - Docker Image
FROM python:3.11-slim

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements
COPY pyproject.toml setup.py ./
COPY email_labeler ./email_labeler/

# Install Python dependencies
RUN pip install --no-cache-dir -e .

# Copy application files
COPY web_app.py ./
COPY label_transactions.py ./
COPY templates ./templates/
COPY examples ./examples/

# Create logs directory
RUN mkdir -p logs

# Expose port
EXPOSE 5000

# Set environment variables
ENV FLASK_APP=web_app.py
ENV PYTHONUNBUFFERED=1

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD python -c "import requests; requests.get('http://localhost:5000/api/status')"

# Run the application
CMD ["python", "web_app.py"]

#!/usr/bin/env python3
"""
Web application for Gmail LLM Labeler
Provides a web interface to trigger labeling jobs, view logs, and monitor progress.
"""

import json
import logging
import os
import subprocess
import threading
import time
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional

from flask import Flask, jsonify, render_template, request, send_from_directory

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - [%(funcName)s] - %(message)s",
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Configuration
PROJECT_ROOT = Path(__file__).parent
LOGS_DIR = PROJECT_ROOT / "logs"
LOGS_DIR.mkdir(exist_ok=True)

# Job tracking
active_jobs: Dict[str, dict] = {}
job_history: List[dict] = []
MAX_HISTORY = 50


class JobRunner:
    """Handles running labeling jobs in background threads."""

    @staticmethod
    def run_transaction_labeler(job_id: str, limit: int = 500, dry_run: bool = False):
        """Run the transaction labeler."""
        job = active_jobs[job_id]
        job["status"] = "running"
        job["start_time"] = datetime.now().isoformat()

        log_file = LOGS_DIR / f"{job_id}_transaction.log"

        try:
            cmd = ["python", "label_transactions.py", "--limit", str(limit)]
            if dry_run:
                cmd.append("--dry-run")

            logger.info(f"Starting transaction labeler: {' '.join(cmd)}")

            with open(log_file, "w") as f:
                process = subprocess.Popen(
                    cmd,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    bufsize=1,
                )

                for line in process.stdout:
                    f.write(line)
                    f.flush()
                    # Update last log line for live preview
                    job["last_log"] = line.strip()

                process.wait()
                job["exit_code"] = process.returncode

            job["status"] = "completed" if process.returncode == 0 else "failed"
            job["end_time"] = datetime.now().isoformat()
            job["log_file"] = str(log_file.name)

            logger.info(f"Transaction labeler finished: {job['status']}")

        except Exception as e:
            logger.error(f"Error running transaction labeler: {e}")
            job["status"] = "error"
            job["error"] = str(e)
            job["end_time"] = datetime.now().isoformat()

        finally:
            # Move to history
            job_history.insert(0, job.copy())
            if len(job_history) > MAX_HISTORY:
                job_history.pop()
            del active_jobs[job_id]

    @staticmethod
    def run_llm_pipeline(
        job_id: str, config: str, limit: int = 200, save_every: int = 40, dry_run: bool = False
    ):
        """Run the LLM pipeline."""
        job = active_jobs[job_id]
        job["status"] = "running"
        job["start_time"] = datetime.now().isoformat()

        log_file = LOGS_DIR / f"{job_id}_pipeline.log"

        try:
            cmd = [
                "python",
                "-m",
                "email_labeler.pipeline.cli",
                "run",
                "--config",
                config,
                "--limit",
                str(limit),
                "--save-every",
                str(save_every),
            ]
            if dry_run:
                cmd.append("--dry-run")

            logger.info(f"Starting LLM pipeline: {' '.join(cmd)}")

            with open(log_file, "w") as f:
                process = subprocess.Popen(
                    cmd,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    bufsize=1,
                )

                for line in process.stdout:
                    f.write(line)
                    f.flush()
                    job["last_log"] = line.strip()

                process.wait()
                job["exit_code"] = process.returncode

            job["status"] = "completed" if process.returncode == 0 else "failed"
            job["end_time"] = datetime.now().isoformat()
            job["log_file"] = str(log_file.name)

            logger.info(f"LLM pipeline finished: {job['status']}")

        except Exception as e:
            logger.error(f"Error running LLM pipeline: {e}")
            job["status"] = "error"
            job["error"] = str(e)
            job["end_time"] = datetime.now().isoformat()

        finally:
            job_history.insert(0, job.copy())
            if len(job_history) > MAX_HISTORY:
                job_history.pop()
            del active_jobs[job_id]

    @staticmethod
    def run_complete_workflow(
        job_id: str,
        config: str,
        transaction_limit: int = 500,
        pipeline_limit: int = 200,
        save_every: int = 40,
        dry_run: bool = False,
    ):
        """Run both transaction labeler and LLM pipeline."""
        job = active_jobs[job_id]
        job["status"] = "running"
        job["start_time"] = datetime.now().isoformat()
        job["steps"] = []

        log_file = LOGS_DIR / f"{job_id}_complete.log"

        try:
            with open(log_file, "w") as f:
                # Step 1: Transaction Labeler
                f.write("=" * 60 + "\n")
                f.write("STEP 1: Transaction Labeler\n")
                f.write("=" * 60 + "\n\n")
                f.flush()

                cmd1 = ["python", "label_transactions.py", "--limit", str(transaction_limit)]
                if dry_run:
                    cmd1.append("--dry-run")

                logger.info(f"Step 1: {' '.join(cmd1)}")
                job["current_step"] = "Transaction Labeler"

                process1 = subprocess.Popen(
                    cmd1,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    bufsize=1,
                )

                for line in process1.stdout:
                    f.write(line)
                    f.flush()
                    job["last_log"] = line.strip()

                process1.wait()
                step1_result = {
                    "name": "Transaction Labeler",
                    "exit_code": process1.returncode,
                    "status": "completed" if process1.returncode == 0 else "failed",
                }
                job["steps"].append(step1_result)

                # Step 2: LLM Pipeline
                f.write("\n" + "=" * 60 + "\n")
                f.write("STEP 2: LLM Pipeline\n")
                f.write("=" * 60 + "\n\n")
                f.flush()

                cmd2 = [
                    "python",
                    "-m",
                    "email_labeler.pipeline.cli",
                    "run",
                    "--config",
                    config,
                    "--limit",
                    str(pipeline_limit),
                    "--save-every",
                    str(save_every),
                ]
                if dry_run:
                    cmd2.append("--dry-run")

                logger.info(f"Step 2: {' '.join(cmd2)}")
                job["current_step"] = "LLM Pipeline"

                process2 = subprocess.Popen(
                    cmd2,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    bufsize=1,
                )

                for line in process2.stdout:
                    f.write(line)
                    f.flush()
                    job["last_log"] = line.strip()

                process2.wait()
                step2_result = {
                    "name": "LLM Pipeline",
                    "exit_code": process2.returncode,
                    "status": "completed" if process2.returncode == 0 else "failed",
                }
                job["steps"].append(step2_result)

                # Overall status
                if step1_result["status"] == "completed" and step2_result["status"] == "completed":
                    job["status"] = "completed"
                    job["exit_code"] = 0
                else:
                    job["status"] = "partial" if step1_result["status"] == "completed" else "failed"
                    job["exit_code"] = 1

            job["end_time"] = datetime.now().isoformat()
            job["log_file"] = str(log_file.name)

            logger.info(f"Complete workflow finished: {job['status']}")

        except Exception as e:
            logger.error(f"Error running complete workflow: {e}")
            job["status"] = "error"
            job["error"] = str(e)
            job["end_time"] = datetime.now().isoformat()

        finally:
            job_history.insert(0, job.copy())
            if len(job_history) > MAX_HISTORY:
                job_history.pop()
            del active_jobs[job_id]


# Routes
@app.route("/")
def index():
    """Main dashboard page."""
    return render_template("index.html")


@app.route("/api/status")
def get_status():
    """Get current system status."""
    return jsonify(
        {
            "active_jobs": list(active_jobs.values()),
            "job_history": job_history[:20],  # Last 20 jobs
            "total_active": len(active_jobs),
        }
    )


@app.route("/api/jobs/start", methods=["POST"])
def start_job():
    """Start a new labeling job."""
    data = request.json
    job_type = data.get("type", "complete")  # complete, transaction, pipeline
    dry_run = data.get("dry_run", False)

    job_id = f"{job_type}_{int(time.time())}"

    job = {
        "id": job_id,
        "type": job_type,
        "status": "queued",
        "dry_run": dry_run,
        "created_time": datetime.now().isoformat(),
        "last_log": "",
    }

    active_jobs[job_id] = job

    # Start job in background thread
    if job_type == "transaction":
        thread = threading.Thread(
            target=JobRunner.run_transaction_labeler,
            args=(job_id, data.get("limit", 500), dry_run),
        )
    elif job_type == "pipeline":
        thread = threading.Thread(
            target=JobRunner.run_llm_pipeline,
            args=(
                job_id,
                data.get("config", "examples/pipeline_config_gemini.yaml"),
                data.get("limit", 200),
                data.get("save_every", 40),
                dry_run,
            ),
        )
    else:  # complete
        thread = threading.Thread(
            target=JobRunner.run_complete_workflow,
            args=(
                job_id,
                data.get("config", "examples/pipeline_config_gemini.yaml"),
                data.get("transaction_limit", 500),
                data.get("pipeline_limit", 200),
                data.get("save_every", 40),
                dry_run,
            ),
        )

    thread.daemon = True
    thread.start()

    return jsonify({"success": True, "job_id": job_id, "job": job})


@app.route("/api/jobs/<job_id>")
def get_job(job_id):
    """Get job details."""
    # Check active jobs
    if job_id in active_jobs:
        return jsonify(active_jobs[job_id])

    # Check history
    for job in job_history:
        if job["id"] == job_id:
            return jsonify(job)

    return jsonify({"error": "Job not found"}), 404


@app.route("/api/logs/<filename>")
def get_log(filename):
    """Get log file content."""
    try:
        log_path = LOGS_DIR / filename
        if not log_path.exists():
            return jsonify({"error": "Log file not found"}), 404

        with open(log_path, "r") as f:
            content = f.read()

        return jsonify({"filename": filename, "content": content})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/logs/<filename>/tail")
def tail_log(filename):
    """Get last N lines of log file."""
    try:
        lines = int(request.args.get("lines", 100))
        log_path = LOGS_DIR / filename

        if not log_path.exists():
            return jsonify({"error": "Log file not found"}), 404

        with open(log_path, "r") as f:
            all_lines = f.readlines()
            tail_lines = all_lines[-lines:]

        return jsonify({"filename": filename, "lines": tail_lines, "total_lines": len(all_lines)})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/metrics")
def get_metrics():
    """Get pipeline metrics."""
    try:
        metrics_file = PROJECT_ROOT / "pipeline_metrics_gemini.json"
        if metrics_file.exists():
            with open(metrics_file, "r") as f:
                metrics = json.load(f)
            return jsonify(metrics)
        return jsonify({"error": "No metrics available"}), 404
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/config")
def get_config():
    """Get available configurations."""
    configs = []
    examples_dir = PROJECT_ROOT / "examples"

    if examples_dir.exists():
        for config_file in examples_dir.glob("*.yaml"):
            configs.append(
                {
                    "name": config_file.stem,
                    "path": str(config_file.relative_to(PROJECT_ROOT)),
                    "full_path": str(config_file),
                }
            )

    return jsonify({"configs": configs})


if __name__ == "__main__":
    print("=" * 60)
    print("Gmail LLM Labeler - Web Interface")
    print("=" * 60)
    print(f"Project Root: {PROJECT_ROOT}")
    print(f"Logs Directory: {LOGS_DIR}")
    print("=" * 60)
    print("\nStarting web server...")
    print("Access the dashboard at: http://localhost:5000")
    print("=" * 60)

    app.run(host="0.0.0.0", port=5000, debug=True)

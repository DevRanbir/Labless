@echo off
:: ============================================================
:: run_labless.bat
:: Complete email labeling workflow:
::   1. Labels transaction emails (fast, rule-based)
::   2. Runs LLM pipeline for remaining emails
:: ============================================================

:: Resolve the directory where this .bat file lives (project root)
set "PROJECT_DIR=%~dp0"
:: Remove trailing backslash
if "%PROJECT_DIR:~-1%"=="\" set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"

:: ---- Configuration ----
set "CONFIG=%PROJECT_DIR%\examples\pipeline_config_gemini.yaml"
set "LIMIT=200"
set "SAVE_EVERY=40"
set "TRANSACTION_LIMIT=500"
:: -----------------------

echo.
echo ============================================================
echo  Gmail LLM Labeler - Complete Workflow
echo  Project : %PROJECT_DIR%
echo  Config  : %CONFIG%
echo ============================================================
echo.

:: Change to project directory so relative paths (DB, metrics) resolve correctly
cd /d "%PROJECT_DIR%"

:: ============================================================
:: STEP 1: Label Transaction Emails (Rule-Based)
:: ============================================================
echo.
echo ============================================================
echo  STEP 1: Labeling Transaction Emails
echo  (Axis Bank transaction alerts only)
echo  Limit: %TRANSACTION_LIMIT% emails
echo ============================================================
echo.

python label_transactions.py --query "(from:alerts@axis.bank.in OR from:notification@axis.bank.in) -label:\"Transaction\"" --limit %TRANSACTION_LIMIT%

set "TRANS_EXIT=%ERRORLEVEL%"

if %TRANS_EXIT% NEQ 0 (
    echo.
    echo [WARN] Transaction labeling had issues (exit code %TRANS_EXIT%)
    echo Continuing with main pipeline...
    echo.
) else (
    echo.
    echo [OK] Transaction labeling completed successfully.
    echo.
)

:: ============================================================
:: STEP 2: Run Main LLM Pipeline (Remaining Emails)
:: ============================================================
echo.
echo ============================================================
echo  STEP 2: Running LLM Pipeline
echo  (Categorizing remaining emails)
echo  Limit: %LIMIT% emails, Save every %SAVE_EVERY%
echo ============================================================
echo.

python -m email_labeler.pipeline.cli run ^
    --config "%CONFIG%" ^
    --limit %LIMIT% ^
    --save-every %SAVE_EVERY%

set "PIPELINE_EXIT=%ERRORLEVEL%"

:: ============================================================
:: Summary
:: ============================================================
echo.
echo ============================================================
echo  WORKFLOW COMPLETE
echo ============================================================
echo  Step 1 (Transactions): Exit code %TRANS_EXIT%
echo  Step 2 (LLM Pipeline): Exit code %PIPELINE_EXIT%
echo ============================================================
echo.

if %TRANS_EXIT%==0 (
    if %PIPELINE_EXIT%==0 (
        echo [OK] All steps completed successfully!
    ) else (
        echo [WARN] Transaction labeling OK, but pipeline had issues.
    )
) else (
    if %PIPELINE_EXIT%==0 (
        echo [WARN] Pipeline OK, but transaction labeling had issues.
    ) else (
        echo [WARN] Both steps had issues. Check logs above.
    )
)

echo.
pause
exit /b %PIPELINE_EXIT%

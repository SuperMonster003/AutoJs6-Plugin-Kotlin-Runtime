@echo off
setlocal
cd /d "%~dp0"

:loop
cls
python -B generate_markdown.py
echo.
echo Press any key to regenerate, or close this window to exit.
pause >nul
goto loop

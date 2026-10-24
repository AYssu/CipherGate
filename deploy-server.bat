@echo off
setlocal
cd /d "%~dp0"
python scripts\project.py package
exit /b %errorlevel%

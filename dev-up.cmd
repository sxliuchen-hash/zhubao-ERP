@echo off
REM 珠宝 ERP 本地开发一键启动（双击即可）
REM 同时拉起后端(8080)与前端(5173)，并自动打开浏览器。
powershell -ExecutionPolicy Bypass -File "%~dp0scripts\dev-up.ps1" %*

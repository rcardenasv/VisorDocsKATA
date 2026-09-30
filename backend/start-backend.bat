@echo off
cd "%~dp0"
echo Iniciando VisorDocs Backend...
java -jar target\visordocs-backend-1.0.0-SNAPSHOT.jar > backend.log 2>&1
echo Proceso finalizado, revisa backend.log
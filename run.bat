@echo off
echo ===================================================
echo   LibriSphere Bookstore - Automated Startup Script
echo ===================================================
echo.
echo [1/3] Preparing Maven Wrapper folders...
if not exist .mvn\wrapper mkdir .mvn\wrapper

echo.
echo [2/3] Downloading Maven Wrapper files...
echo Downloading mvnw.cmd...
powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/spring-io/start.spring.io/main/mvnw.cmd' -OutFile 'mvnw.cmd'"
echo Downloading maven-wrapper.properties...
powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/spring-io/start.spring.io/main/.mvn/wrapper/maven-wrapper.properties' -OutFile '.mvn\wrapper\maven-wrapper.properties'"
echo Downloading maven-wrapper.jar...
powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/spring-io/start.spring.io/main/.mvn/wrapper/maven-wrapper.jar' -OutFile '.mvn\wrapper\maven-wrapper.jar'"

echo.
echo [3/3] Compiling and starting LibriSphere Bookstore application...
echo.
call mvnw.cmd spring-boot:run
pause

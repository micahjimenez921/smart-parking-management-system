@echo off
dir /s /b source_code\SmartParking\src\*.java > sources.txt
javac -cp "source_code\SmartParking\lib\flatlaf-3.7.2.jar" -d out @sources.txt
if errorlevel 1 exit /b 1
java -cp "out;source_code\SmartParking\lib\flatlaf-3.7.2.jar" app.Main
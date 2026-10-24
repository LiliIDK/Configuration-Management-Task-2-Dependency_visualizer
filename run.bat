@echo off
setlocal
if /I "%~1"=="test" goto tests
call mvn -q -DskipTests package || exit /b 1
java -jar target\dependency-visualizer-1.0.0.jar config.yaml
exit /b %errorlevel%
:tests
call mvn -q test-compile || exit /b 1
java -cp target\classes;target\test-classes edu.example.visualizer.SmokeTest || exit /b 1
java -cp target\classes;target\test-classes edu.example.visualizer.ParentResolutionTest
exit /b %errorlevel%

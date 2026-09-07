@ECHO OFF
SET DIR=%~dp0
java -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew -classpath "%DIR%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*

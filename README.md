# Gradle 源码分析

## gradlew.bat分析

gradlew.bat 大部分是环境设置、参数处理、代码注释等内容

最重要的一行 

`"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*`

`set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar`

其实就是执行了 gradle\wrapper\gradle-wrapper.jar 里面的 org.gradle.wrapper.GradleWrapperMain 的 main() 方法

## GradleWrapperMain分析

源码在`gradle-7.6.0/subprojects/wrapper/src/main/java/org/gradle/wrapper/GradleWrapperMain.java`

## 任务的命名

使用小驼峰命名法

## 命令行参数

-q, --quiet                        Log errors only.

## 源码分析图

![source-code-analysis.png](readme/source-code-analysis-01.png)

![source-code-analysis-02.png](readme/source-code-analysis-02.png)

*来自网络*
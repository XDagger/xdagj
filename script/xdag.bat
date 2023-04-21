@echo off

set XDAG_VERSION="0.6.3"
set XDAG_JARNAME="xdagj-%XDAG_VERSION%-shaded.jar"
set XDAG_OPTS="-t"

#set JAVA_HOME="C:\Program Files\Java\jdk"

# default JVM options
set JAVA_OPTS="--add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED -server -Xms4g -Xmx4g"

set JAVA_HEAPDUMP="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./logs/xdag-heapdump"

set JAVA_GC_LOG="-Xlog:gc*,gc+heap=trace,gc+age=trace,safepoint:file=./logs/xdag-gc-%t.log:time,level,tid,tags:filecount=8,filesize=10m"

java %JAVA_OPTS% %JAVA_HEAPDUMP% %JAVA_GC_LOG% -cp .;%XDAG_JARNAME% io.xdag.Bootstrap %*

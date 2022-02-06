#!/bin/sh

XDAG_VERSION="0.4.6"
XDAG_JARNAME="xdagj-${XDAG_VERSION}-shaded.jar"
XDAG_OPTS="-t"

# Linux Java Home
#JAVA_HOME="/usr/local/java/"

# MacOS Java Home
#JAVA_HOME=/usr/local/opt/openjdk/libexec/openjdk.jdk/Contents/Home/

# default JVM options
JAVA_OPTS="--add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED -server -Xms1g -Xmx1g"

# start kernel
java ${JAVA_OPTS} -cp .:${XDAG_JARNAME} io.xdag.Bootstrap "$@"
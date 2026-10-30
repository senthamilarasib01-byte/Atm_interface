#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

# Resolve working Java runtime (system or embedded IDE JRE)
if java -version >/dev/null 2>&1; then
    JAVA_CMD="java"
elif [ -f "$HOME/.antigravity-ide/extensions/redhat.java-1.58.0-darwin-arm64/jre/25.0.4.1-macosx-aarch64/bin/java" ]; then
    JAVA_CMD="$HOME/.antigravity-ide/extensions/redhat.java-1.58.0-darwin-arm64/jre/25.0.4.1-macosx-aarch64/bin/java"
else
    echo "Error: Java runtime not found. Please install JDK 21+ or use the IDE."
    exit 1
fi

M2="$HOME/.m2/repository"
FX_PATH="$M2/org/openjfx/javafx-base/21.0.6/javafx-base-21.0.6-mac-aarch64.jar:$M2/org/openjfx/javafx-controls/21.0.6/javafx-controls-21.0.6-mac-aarch64.jar:$M2/org/openjfx/javafx-graphics/21.0.6/javafx-graphics-21.0.6-mac-aarch64.jar:$M2/org/openjfx/javafx-fxml/21.0.6/javafx-fxml-21.0.6-mac-aarch64.jar"
CP="target/classes:$M2/com/mysql/mysql-connector-j/9.2.0/mysql-connector-j-9.2.0.jar:$M2/com/h2database/h2/2.3.232/h2-2.3.232.jar"

if [ "$1" = "--gui" ]; then
    echo "Launching AUREL — Premium ATM Desktop GUI..."
    exec "$JAVA_CMD" --enable-native-access=javafx.graphics \
        --module-path "$FX_PATH" \
        --add-modules javafx.controls,javafx.fxml \
        -cp "$CP" \
        com.premiumatm.App
else
    echo "Launching AUREL — Premium ATM on http://localhost:8080..."
    exec "$JAVA_CMD" -cp "$CP" com.premiumatm.web.WebServer
fi

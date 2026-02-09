#!/usr/bin/env sh
# Start Appium with ANDROID_HOME set if not already.
# Use this in the terminal where you run Appium so the UiAutomator2 driver can find the SDK.

if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
  # Try common SDK locations
  if [ -d "/d/Sdk" ]; then
    export ANDROID_HOME="/d/Sdk"
  elif [ -d "$HOME/AppData/Local/Android/Sdk" ]; then
    export ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"
  elif [ -d "$HOME/Library/Android/sdk" ]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
  elif [ -d "/usr/local/share/android-sdk" ]; then
    export ANDROID_HOME="/usr/local/share/android-sdk"
  else
    echo "ANDROID_HOME / ANDROID_SDK_ROOT not set and no default SDK path found."
    echo "Set ANDROID_HOME to your Android SDK path, e.g.:"
    echo "  export ANDROID_HOME=C:\\Users\\You\\AppData\\Local\\Android\\Sdk   # Windows Git Bash"
    echo "  set ANDROID_HOME=C:\\Users\\You\\AppData\\Local\\Android\\Sdk     # Windows Cmd"
    echo "  export ANDROID_HOME=\$HOME/Library/Android/sdk                      # Mac"
    exit 1
  fi
  echo "Using ANDROID_HOME=$ANDROID_HOME"
fi

cd "$(dirname "$0")"
exec npm run appium

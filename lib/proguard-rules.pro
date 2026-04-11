# NetHack Android ProGuard Rules
# These rules ensure that JNI callbacks and native method signatures are preserved
# during code obfuscation in release builds.

# Keep all native methods (called from native code)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep NetHackIO JNI callback methods (called from native code via JNI)
-keepclassmembers class com.tbd.forkfront.NetHackIO {
    public void ynFunction(...);
    public void getLine(...);
    public void display(...);
    public void printGlyph(...);
    public void clearGlyph(...);
    public void printTile(...);
    public void putString(...);
    public void rawPrint(...);
    public void createWindow(...);
    public void destroyWindow(...);
    public void clearWindow(...);
    public void displayWindow(...);
    public void startMenu(...);
    public void addMenu(...);
    public void endMenu(...);
    public void selectMenu(...);
    public void updateInventory(...);
    public void markSyncPos(...);
    public void clipAround(...);
    public void delayOutput(...);
    public int getNhWid(...);
    public void printGlyph2(...);
    public void playerSelection(...);
    public void updateStatus(...);
    public void preferenceUpdate(...);
    public void lineInput(...);
}

# Keep NH_Handler interface (implemented by NH_State, referenced from native code)
-keep interface com.tbd.forkfront.NH_Handler {
    *;
}

# Keep NH_State handler getter (used during initialization)
-keepclassmembers class com.tbd.forkfront.NH_State {
    public com.tbd.forkfront.NH_Handler getNhHandler();
}

# Keep ByteDecoder (used in JNI callbacks)
-keep class com.tbd.forkfront.ByteDecoder {
    *;
}

# Keep MenuItem class (used in JNI menu callbacks)
-keep class com.tbd.forkfront.MenuItem {
    *;
}

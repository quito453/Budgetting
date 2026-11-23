# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---- Valiu ----
# Readable crash reports in Play Console (with the mapping.txt it uploads).
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Capacitor finds plugins and their @PluginMethod methods by reflection: keep them intact.
-keep class com.getcapacitor.** { *; }
-keep @com.getcapacitor.annotation.CapacitorPlugin class * { *; }
-keepclassmembers class * { @com.getcapacitor.PluginMethod public <methods>; }
-keep class com.capacitorjs.plugins.** { *; }
-keep class ee.forgr.capacitor_updater.** { *; }
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

# Push notifications (Firebase ships its own rules; this is a safety net).
-keep class com.google.firebase.messaging.** { *; }
-dontwarn com.google.firebase.**

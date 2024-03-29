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
# debugging mActivityStack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontobfuscate

-keep public class com.liuzhenlin.videos.App {
    public static void cacheNightMode(boolean);
}

# Application classes that will be serialized/deserialized over Gson
-keep public class com.liuzhenlin.videos.bean.TVGroup { <fields>; }

# hotfix
-dontoptimize
-keep class com.taobao.sophix.**{*;}
-keep class com.ta.utdid2.device.**{*;}

# java mail
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class com.sun.activation.** { *; }
-keep class javax.activation.** { *; }

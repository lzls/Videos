# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in F:\dev\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keepclassmembers,allowobfuscation class * extends com.liuzhenlin.texturevideoview.VideoPlayer {
    <init>(android.content.Context);
}

-keep class org.videolan.libvlc.** { *; }

-keep class com.danikula.videocache.** { *; }

-keep class com.coremedia.iso.** { *; }
-keep class com.googlecode.mp4parser.** { *; }
-keep class com.mp4parser.** { *; }
-keep class org.mp4parser.** { *; }

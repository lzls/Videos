# Proguard rules specific to the AV1 extension.

# This prevents the names of native methods from being obfuscated.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Some members of this class are being accessed from native methods. Keep them unobfuscated.
-keep class androidx.media3.decoder.VideoDecoderOutputBuffer {
  *;
}
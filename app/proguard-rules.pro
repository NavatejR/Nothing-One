# Nothing One release rules (R8 / minify enabled).
#
# Keep this file minimal: libraries that ship consumer rules (Hilt, Room,
# Media3, Compose) need nothing here. What needs help:
#   1. JNI — the C++ bridge looks up Java_com_nothing_one_ai_WhisperJni_*
#      symbols, so those method names must survive obfuscation.
#   2. kotlinx.serialization — AssistantTool's polymorphic serializers are
#      discovered reflectively; without keeps, tool parsing dies at runtime.

# ---- JNI -----------------------------------------------------------------
-keepclasseswithmembernames class com.nothing.one.ai.WhisperJni {
    native <methods>;
}

# ---- kotlinx.serialization (official R8 rules) -----------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.nothing.one.**$$serializer { *; }
-keepclassmembers class com.nothing.one.** {
    *** Companion;
}
-keepclasseswithmembers class com.nothing.one.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Google AI Edge / MediaPipe --------------------------------------------
# Blunt but safe: the genai task loading path is native+reflective and a
# broken release build only shows up on a real device.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

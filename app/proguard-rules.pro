# ProGuard rules for kotoSay

# TFLite
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# MediaPipe
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Keep model-related data classes
-keep class com.kotosay.app.ml.** { *; }

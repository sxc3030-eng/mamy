# Add project-specific ProGuard rules here.
# Keep room entities (Room handles via @Keep annotation in P1.10).
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

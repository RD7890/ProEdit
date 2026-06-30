-keep class com.rohan.proedit.processing.NativeProcessor { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn kotlin.**
-keepattributes *Annotation*

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# General Android optimizations
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Keep annotations
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature,Exceptions

# Keep line numbers for debugging stack traces but obfuscate source file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep important metadata for crash reporting
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod

# ProGuard rules for Koin 3.5.6
-keep class org.koin.** { *; }
-keep class io.insert-koin.** { *; }
-dontwarn org.koin.core.context.GlobalContext

# ProGuard rules for ObjectBox 3.8.0
-keep class io.objectbox.EntityInfo { *; }
-keep class io.objectbox.Property { *; }
-keep class io.objectbox.annotation.Entity { *; }
-keep class io.objectbox.annotation.Id { *; }
-keep @io.objectbox.annotation.Entity class *
-keep class **.*_
-keepclassmembers class * {
    @io.objectbox.annotation.Id long id;
    @io.objectbox.annotation.Id Long id;
}
-keepclassmembers enum io.objectbox.** {
    **[] $VALUES;
    public *;
}

# ProGuard rules for TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.lite.gpu.**

# ProGuard rules for MediaPipe
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
-dontwarn com.google.mediapipe.**

# ProGuard rules for ML Kit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.** { *; }
-dontwarn com.google.android.gms.**
-dontwarn com.google.mlkit.**

# ProGuard rules for CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Keep the following classes for coroutines
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory { 
    private volatile kotlinx.coroutines.MainCoroutineDispatcher main;
}
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ProGuard rules for Jetpack Compose
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-dontwarn androidx.compose.**

# ProGuard rules for Gemini AI
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# Keep application class and activities
-keep public class com.example.vocaleyesnew.VocalEyesApplication
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep model classes if any
-keep class com.example.vocaleyesnew.**.model.** { *; }
-keep class com.example.vocaleyesnew.**.data.** { *; }

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Remove System.out/err debugging
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# Remove Timber logging for release builds
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Keep serialization classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# AutoValue and annotation processors
-dontwarn javax.lang.model.**
-dontwarn com.google.auto.value.**
-dontwarn autovalue.shaded.**
-keep class javax.lang.model.** { *; }
-keep class com.google.auto.value.** { *; }
-keep class autovalue.shaded.** { *; }

# Additional rules for annotation processing
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom View constructors
-keepclasseswithmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
    public static final ** CREATOR;
    public <fields>;
    private <fields>;
}

# Keep annotation classes
-keep class * extends java.lang.annotation.Annotation { *; }

# ProGuard rules for OpenCV
-keep class org.opencv.** { *; }
-keep class org.opencv.android.** { *; }
-keep class org.opencv.core.** { *; }
-keep class org.opencv.imgproc.** { *; }
-keepclassmembers class org.opencv.** { 
    native <methods>; 
}
-dontwarn org.opencv.**

# Additional MediaPipe and ML Kit rules for R8
-keep class com.google.mediapipe.components.** { *; }
-keep class com.google.mediapipe.framework.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**

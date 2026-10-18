# SLF4J / Ktor
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn java.awt.**
-dontwarn javax.annotation.**
-dontwarn kotlinx.coroutines.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep data models for kotlinx.serialization
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}

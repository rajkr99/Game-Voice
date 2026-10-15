# LiveKit / WebRTC
-keep class io.livekit.android.** { *; }
-keep class livekit.org.webrtc.** { *; }
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.gamervoice.app.**$$serializer { *; }
-keepclassmembers class com.gamervoice.app.** { *** Companion; }
-dontwarn org.slf4j.**

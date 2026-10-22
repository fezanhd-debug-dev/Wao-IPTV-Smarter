# Gson reflection se data classes ke fields padhta hai — inhe obfuscate nahi karna,
# warna cache/settings load/save fail ho jayega
-keep class com.wao.iptv.Session { *; }
-keep class com.wao.iptv.SavedAccount { *; }
-keep class com.wao.iptv.Category { *; }
-keep class com.wao.iptv.Channel { *; }
-keep class com.wao.iptv.VodItem { *; }
-keep class com.wao.iptv.Episode { *; }
-keep class com.wao.iptv.EpgEntry { *; }
-keep class com.wao.iptv.AccountInfo { *; }
-keep class com.wao.iptv.PlayItem { *; }
-keep class com.wao.iptv.HistoryItem { *; }
-keep class com.wao.iptv.AppSettings { *; }
-keep class com.wao.iptv.FavoriteItem { *; }
-keep class com.wao.iptv.ConnectionTestResult { *; }
-keep class com.wao.iptv.ContentSnapshot { *; }
-keep class com.wao.iptv.CachedContent { *; }
-keep class com.wao.iptv.UpdateInfo { *; }
-keep class com.wao.iptv.DeviceActivationResult { *; }

-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault

# Gson khud ke internal classes
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp / Media3 ke liye standard safe rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keep class androidx.media3.** { *; }

# Stack traces readable rahen crash-report ke liye, lekin naam chhup jayen
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Retrofit / Gson / OkHttp default keep rules are generally not required
# with recent versions using R8 full mode, but kept conservative for v1.
-keep class com.levidor.kehribarvideo.data.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

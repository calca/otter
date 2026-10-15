# Regole R8 di Calm Otter. Le librerie (Room, Glance, CameraX, Tink di
# security-crypto, Compose) portano le proprie consumer rules; qui solo
# quello che l'app istanzia o legge per nome.

# Glance istanzia le ActionCallback per nome di classe (actionRunCallback<T>()).
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# Enum letti da SharedPreferences con valueOf(nome salvato).
-keepclassmembers enum com.calmotter.app.** { *; }

# Nomi di file e righe leggibili nei crash deoffuscati da Play.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

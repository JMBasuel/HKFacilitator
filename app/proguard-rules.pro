-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class com.upang.hkfacilitator.models.** { *; }
-keep class org.apache.poi.** { *; }
-keep class org.apache.logging.** { *; }
-keep class org.apache.commons.** { *; }
-keep class org.openxmlformats.schemas.** { *; }

-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.OpenSSLProvider
-dontwarn java.awt.Shape
-dontwarn org.openxmlformats.schemas.**
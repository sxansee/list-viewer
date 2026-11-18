# Rules for the optimised (release) build. They come on top of proguard-android-optimize.txt and of the rules that every
# library brings along (Compose, Media3, Coil and the others need nothing more).

# R8 removes the code that is never used and optimises the rest, but keeps all names. A crash report from a phone stays
# readable, and nothing that is looked up by its name can break. The few kilobytes that renaming would save are not worth that.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# PdfBox-Android (search inside PDF files) loads fonts, filters and security handlers by their names.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }

# Optional parts of PdfBox (JPEG 2000, JBIG2, encryption providers, desktop Java) and libraries that only carry annotations.
# They are not packaged with the app, and none of the app's features reaches them.
-dontwarn com.gemalto.jp2.**
-dontwarn com.levigo.jbig2.**
-dontwarn org.bouncycastle.**
-dontwarn org.spongycastle.**
-dontwarn org.apache.commons.logging.**
-dontwarn javax.annotation.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn java.awt.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.jspecify.**

# A missing optional class is reported as a warning and does not stop the build.
-ignorewarnings

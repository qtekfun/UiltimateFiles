# Koin and Compose ship consumer rules; add project-specific keeps here when needed.

# SFTP (sshj) looks algorithms up by name and drags in optional libraries; keep it whole and ignore what Android lacks.
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn javax.naming.**
-dontwarn javax.security.auth.kerberos.**
-dontwarn org.ietf.jgss.**
-dontwarn com.sun.jna.**
-dontwarn com.jcraft.jzlib.**
-dontwarn org.slf4j.impl.**
-dontwarn org.bouncycastle.**
-dontwarn sun.security.**
-dontwarn java.lang.management.**

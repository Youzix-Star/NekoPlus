# miuix, Jetpack Compose and the AndroidX libraries ship their own consumer rules.
#
# R8 is what strips the unused Material icons from material-icons-extended; without it the
# release APK would carry several thousand unused ImageVector definitions.
#
# The floating window service is declared in the manifest and instantiated by the system.
-keep class love.miao.yun.service.FloatingWindowService { *; }

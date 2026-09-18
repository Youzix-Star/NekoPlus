# miuix, Jetpack Compose and the AndroidX libraries ship their own consumer rules.
#
# R8 is what strips the unused Material icons from material-icons-extended; without it the
# release APK would carry several thousand unused ImageVector definitions.
#
# The floating window service is declared in the manifest and instantiated by the system.
-keep class love.miao.yun.service.FloatingWindowService { *; }

# --- Crash reports have to be readable ---
#
# Without these, every frame in a crash report reads `r8-map-id-<hash>:29`: no file name, and the
# number is a synthetic offset rather than a line. Keeping the line-number table and renaming the
# source file attribute to a plain `SourceFile` gives `MiuixOnboarding.kt:412` style frames, which
# is the difference between a report someone can act on and one they have to guess at. Copied in
# spirit from HyperCeiler's own app-level rules (`app/src/main/keepRules/rules.keep`).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

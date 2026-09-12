# miuix, Jetpack Compose and the AndroidX libraries ship their own consumer rules.
#
# R8 is what strips the unused Material icons from material-icons-extended; without it the
# release APK would carry several thousand unused ImageVector definitions.
#
# The accessibility service and the floating window service are declared in the manifest and
# are instantiated by the system, so keep their entry points.
-keep class love.miao.yun.AccessibilityService { *; }
-keep class love.miao.yun.FloatingWindowService { *; }

# org.json is used for rule / AI preset persistence via reflection-free APIs, but keep the
# model classes that are serialised into SharedPreferences.
-keep class love.miao.yun.RuleManager$Rule { *; }

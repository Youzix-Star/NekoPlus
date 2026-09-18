# miuix, Jetpack Compose and the AndroidX libraries ship their own consumer rules.
#
# R8 is what strips the unused Material icons from material-icons-extended; without it the
# release APK would carry several thousand unused ImageVector definitions.
#
# The floating window service is declared in the manifest and instantiated by the system.
-keep class love.miao.yun.service.FloatingWindowService { *; }

# --- The ported first-run guide (HyperCeiler's library/provision, AGPL-3.0-only) ---
#
# The guide is written against Xiaomi's MIUI/Miuix framework jars (fan.miuix:*), and those jars
# reference framework internals that are not in android.jar: R8 refuses to finish a release build
# while they are unresolved. These two -dontwarn lines are copied verbatim from the ones HyperCeiler
# itself uses for the same jars (library/core/src/main/keepRules/rules.keep), and the two -keep
# lines from the provisioning module's own keep rules (library/provision/src/main/keepRules).
# The classes do exist at runtime on MIUI/HyperOS; elsewhere the guide's blur and haptics paths are
# already guarded by the miuix library's own feature checks.
-dontwarn miui.**
-dontwarn com.android.internal.view.menu.MenuBuilder
-dontwarn javax.annotation.**

# --- The rule whose absence crashed the guide (2026-09-19) ---
#
# Copied from the same file as the -dontwarn lines above, `library/core/src/main/keepRules/rules.keep`.
# Without it R8 strips methods out of `fan.**` that it cannot see anyone calling: the buttons the
# guide is built from are inflated by class name out of XML, and `DrawableInflater` then instantiates
# them reflectively, so R8's reachability graph never reaches `FolmeEase.spring(float, float)`.
# The result was `NoSuchMethodError: No static method spring(FF)…` while inflating
# `miuix_appcompat_group_buttons_layout`, i.e. the moment the guide opened. Verified by diffing the
# dex: the `(FF)Lfan/animation/utils/EaseManager$EaseStyle;` descriptor is absent from the crashing
# build and present once this rule is back.
-keep class fan.** { *; }
-keep class com.fan.** { *; }

-keep class com.sevtinge.hyperceiler.provision.activity.** { *; }
-keep class com.sevtinge.hyperceiler.provision.fragment.** { *; }

# Not one of HyperCeiler's rules, but the same reasoning as theirs: StateMachine persists the guide's
# position as `Class.getSimpleName()` (`com.android.provision.STATE_<i>`), so the state classes have
# to keep their names. Obfuscated, save and restore would still agree within one build, but a chain
# written by the previous APK could name a different state after an update and resume the guide on
# the wrong page.
-keep class com.sevtinge.hyperceiler.provision.state.** { *; }

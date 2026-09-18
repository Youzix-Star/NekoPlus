pluginManagement {
    repositories {
        // Official repositories first: a 5xx from a mirror aborts resolution instead of
        // falling through, so mirrors must never be the first place Gradle looks.
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // The miuix / MIUI framework jars that HyperCeiler's provisioning module is written against
        // (`fan.miuix:*`) are published by HyperCeiler itself to GitHub Packages. The packages are
        // public, but GitHub Packages answers 401 to anonymous requests, so a token is needed even
        // to read them. Locally: GIT_ACTOR / GIT_TOKEN in the environment, or gpr.user / gpr.key in
        // local.properties. In CI: the workflow passes them through.
        val gprUser = System.getenv("GIT_ACTOR") ?: localProperty("gpr.user")
        val gprKey = System.getenv("GIT_TOKEN") ?: localProperty("gpr.key")
        if (gprUser != null && gprKey != null) {
            maven("https://maven.pkg.github.com/ReChronoRain/HyperCeiler") {
                credentials {
                    username = gprUser
                    password = gprKey
                }
            }
        } else {
            logger.warn(
                "GIT_ACTOR/GIT_TOKEN (or gpr.user/gpr.key in local.properties) are not set: " +
                    "the fan.miuix:* dependencies of the first-run guide cannot be resolved.",
            )
        }
    }
}

/** Reads one key out of local.properties, which is where machine-local secrets belong. */
fun localProperty(key: String): String? {
    val file = file("local.properties")
    if (!file.exists()) return null
    val properties = java.util.Properties()
    file.inputStream().use(properties::load)
    return properties.getProperty(key)?.takeIf { it.isNotBlank() }
}

rootProject.name = "MiaoAssistant"

include(":app")

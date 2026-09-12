pluginManagement {
    repositories {
        // Official repositories come first on purpose: CI runs on GitHub's runners, and a
        // 5xx from a mirror aborts dependency resolution instead of falling through to the
        // next repository (only 404 does). The Aliyun mirrors are kept last as a fallback
        // for builds from mainland China, where the official hosts can be slow.
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}
rootProject.name = "MiaoAssistant"
include(":app")

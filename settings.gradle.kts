pluginManagement {
    repositories {
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
    }
}

plugins {
    id("com.android.application") version "9.1.0" apply false
    // Não é aplicado em nenhum módulo: o AGP 9 compila Kotlin sozinho
    // (android.builtInKotlin=true em gradle.properties). Fica declarado só para
    // deixar as classes do plugin Kotlin no classpath (JvmTarget, usado em
    // app/build.gradle.kts).
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
}

rootProject.name = "SiKey"
include(":app")

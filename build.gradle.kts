// Toda a configuração do app fica em app/build.gradle.kts.
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

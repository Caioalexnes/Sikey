plugins {
    id("com.android.application")
}

android {
    namespace = "com.caioalexnes.sikey"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.caioalexnes.sikey"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Sem ofuscação: o app é pequeno e assim o APK de release sai
            // instalável direto, assinado com a chave de depuração local.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

// ---------------------------------------------------------------------------
// Compila o Java num processo separado, chamando o executavel `javac`.
//
// NAO e preferencia de estilo: sem isto o build NAO PASSA nesta maquina.
//
// O compilador embutido do Gradle abre os jars do SDK como sistema de arquivos
// zip e os fecha no fim da tarefa. O fechamento chama toRealPath(), e para
// QUALQUER arquivo dentro de AppData/Local isso falha com "being used by
// another process" -- e o Android SDK mora exatamente la.
//
// O problema e da maquina, nao do projeto: o mesmo jar copiado para fora de
// AppData/Local abre e fecha sem erro. Reproduz em 15 linhas de Java puro, sem
// Gradle nenhum. Ver "Problema conhecido no Windows" no README.
//
// Chamando o `javac` de fora, nada disso acontece: o processo termina e o
// sistema operacional libera os arquivos. O custo e perder a compilacao
// incremental de Java, irrelevante aqui: o unico Java do projeto sao as poucas
// classes que o ViewBinding gera.
tasks.withType<JavaCompile>().configureEach {
    options.isFork = true
    val javac = if (System.getProperty("os.name").startsWith("Windows")) "javac.exe" else "javac"
    options.forkOptions.executable =
        File(System.getProperty("java.home"), "bin/" + javac).absolutePath
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.12.4")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.12.0")
}

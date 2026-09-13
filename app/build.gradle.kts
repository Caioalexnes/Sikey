import java.util.Properties

// ---------------------------------------------------------------------------
// Chave de assinatura de release, se ela existir NESTA maquina.
//
// Nem o `.jks` nem o `key.properties` estao no repositorio, e isso E O DESENHO:
// a assinatura e o que amarra o app a quem o publica. Quem clonar o projeto nao
// tem a chave, e o build de release dele continua saindo assinado com a chave de
// DEPURACAO, como qualquer projeto recem-clonado. Um projeto aberto em que
// `assembleRelease` falha para quem clonou e um projeto que ninguem consegue
// conferir.
//
// A defensividade aqui nao e estilo: este bloco roda na CONFIGURACAO do Gradle,
// e um valor invalido derruba TODAS as tarefas, debug inclusive.
val arquivoDaChave = rootProject.file("key.properties")
val propriedadesDaChave = Properties()
if (arquivoDaChave.exists()) {
    arquivoDaChave.inputStream().use { propriedadesDaChave.load(it) }
}

// So vale se as QUATRO estiverem la, e se o .jks existir de verdade. Meia
// configuracao de assinatura falha tarde, na tarefa de empacotar, com uma
// mensagem que nao diz o que falta.
val temChaveDeRelease =
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .all { !propriedadesDaChave.getProperty(it).isNullOrBlank() } &&
        rootProject.file(propriedadesDaChave.getProperty("storeFile") ?: "").exists()

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

    signingConfigs {
        if (temChaveDeRelease) {
            create("release") {
                storeFile = rootProject.file(propriedadesDaChave.getProperty("storeFile"))
                storePassword = propriedadesDaChave.getProperty("storePassword")
                keyAlias = propriedadesDaChave.getProperty("keyAlias")
                keyPassword = propriedadesDaChave.getProperty("keyPassword")

                // v1 e a assinatura antiga, dentro do META-INF do zip, e so
                // serve para Android 6 ou anterior. O minSdk daqui e 24, entao
                // ela so aumentaria o tamanho do APK.
                enableV1Signing = false
                // v2 assina o arquivo inteiro (Android 7+). v3 e o mesmo, mais
                // a linhagem que permite TROCAR de chave um dia sem quebrar a
                // atualizacao. Sem v3 gravado agora, essa porta fica fechada.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Sem ofuscação: o app é pequeno e o ganho não paga o risco de o R8
            // remover algo por reflexão sem ninguém perceber.
            isMinifyEnabled = false

            // Sem `key.properties`, assina com a chave de DEPURACAO, que e o que
            // mantem `assembleRelease` funcionando para quem clonou o projeto.
            //
            // ATENCAO ao trocar de chave: a impressao digital SHA-256 do
            // certificado e a identidade do app. Trocar a chave faz o Android
            // RECUSAR a atualizacao por cima da versao anterior, e quem ja tinha
            // o app instalado precisa desinstalar antes. Nao ha volta depois de
            // distribuir.
            signingConfig = if (temChaveDeRelease) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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

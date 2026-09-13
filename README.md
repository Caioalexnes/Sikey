# SiKey

App Android que mostra as impressões digitais **SHA-256, SHA-1 e MD5** dos apps
instalados no aparelho e de qualquer arquivo `.apk`.

Serve para responder uma pergunta só, mas que aparece o tempo todo: *este app
aqui é mesmo o que eu acho que é?*

## O app mostra duas coisas diferentes

Os dois costumam ser chamados de "hash do app", e confundir um com o outro é o
erro mais comum ao conferir um APK.

| | Certificado de assinatura | Arquivo APK |
|---|---|---|
| Responde | **quem** assinou | **qual** arquivo é |
| Muda quando o app é atualizado? | não | sim, toda versão |
| Onde esse número aparece | Play Console, Firebase, `assetlinks.json`, `keytool`, `apksigner` | página de download, `sha256sum` |
| Serve para | saber se o APK veio de quem você espera | conferir um download byte a byte |

Para decidir se confia num APK baixado fora da loja, o número que importa é o do
**certificado**: ele é o mesmo em todas as versões publicadas por aquele
desenvolvedor. O hash do arquivo só prova que o download não veio corrompido ou
trocado.

## O que tem na tela

- Lista dos apps instalados, com busca por nome ou pacote e opção de incluir os
  apps do sistema.
- Por app: os três hashes do certificado, os três do `base.apk` (e de cada
  split, quando existe), além de emissor, validade, algoritmo da chave e número
  de série.
- **Caixa de comparação**: cole o valor esperado e o app diz se bate e com o
  quê. Aceita qualquer formatação — com dois-pontos, com espaços, maiúsculo ou
  minúsculo.
- **Verificar arquivo APK**: escolhe um `.apk` pelo seletor de arquivos e faz a
  mesma análise, sem precisar instalar nada. Também abre por "Abrir com" a
  partir de um gerenciador de arquivos.
- Copiar cada hash, copiar tudo de uma vez, compartilhar o relatório.

Hash de certificado é copiado no formato `AA:BB:CC` (o do Play Console e do
`keytool`); hash de arquivo, em hexadecimal puro minúsculo (o do `sha256sum`).

## Sobre MD5 e SHA-1

Estão na tela porque ferramentas antigas ainda imprimem esses valores e às vezes
é só isso que a outra ponta te dá. Os dois têm colisões práticas há anos e não
servem para decidir se um APK é confiável. **Use o SHA-256.**

## Compilar

Precisa do Android SDK e de um JDK 17 ou mais novo. O `local.properties` aponta
para o SDK desta máquina e não vai para o repositório.

```bash
./gradlew :app:assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`. Para instalar:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Sem `key.properties` na raiz, a build de release sai assinada com a chave de
depuração — instalável, sem configuração nenhuma. Com o arquivo lá, sai assinada
com a chave de release. Ver a seção "Assinatura" abaixo.

Versões usadas: Gradle 9.3.1, AGP 9.1.0, Kotlin embutido do AGP
(`android.builtInKotlin=true`, sem aplicar o plugin Kotlin), `compileSdk` 36,
`minSdk` 24.

## Permissão QUERY_ALL_PACKAGES

Do Android 11 em diante, um app só enxerga os outros se declarar
`QUERY_ALL_PACKAGES`. Sem ela a lista viria praticamente vazia, então ela é o
app inteiro, não um detalhe.

A Play Store trata essa permissão como restrita e exige justificativa para
publicar. Para uso próprio ou distribuição por fora, nada muda.

## Conferido contra

Os valores foram comparados com os do `apksigner` e do `sha256sum` nos dois
casos, e batem:

```
apksigner verify --print-certs app-debug.apk
sha256sum app-debug.apk
```

Testado em Android 16 (SDK 36) e Android 9 (SDK 28).

## Limite conhecido

Ler o `base.apk` de **outro** app depende da versão do Android. Quando o sistema
não deixa, a seção do arquivo mostra o erro e os hashes do certificado continuam
aparecendo normalmente — eles vêm do PackageManager, não do arquivo.

## Problema conhecido no Windows (desta máquina)

Nesta máquina, **qualquer** programa Java falha ao fechar um arquivo `.zip`/
`.jar` que esteja dentro de `AppData\Local` — e é justamente onde o Android SDK
está instalado. O erro é sempre o mesmo:

```
java.nio.file.FileSystemException: ...jar: The process cannot access the file
because it is being used by another process
```

Não é problema do projeto. Reproduz em Java puro, sem Gradle, e depende só de
**onde** o arquivo está: o mesmo `.jar`, byte a byte, abre e fecha sem erro fora
de `AppData\Local`.

```java
// java ZipTest.java <caminho-do-jar>
FileSystem fs = FileSystems.newFileSystem(Paths.get(args[0]));
fs.close();   // falha se o jar estiver em AppData\Local
```

| Local do mesmo jar | Fechar |
|---|---|
| `C:\Users\<user>\` | ok |
| `C:\Users\<user>\AppData\Roaming\` | ok |
| `C:\ziptest\` | ok |
| `C:\Users\<user>\AppData\Local\` | **falha** |

O `apksigner` do SDK também para de funcionar por isso: a JVM não consegue nem
carregar as classes do `apksigner.jar` de lá. Copiado para fora, roda normal.

**O que o projeto faz a respeito:** `app/build.gradle.kts` manda o Gradle
compilar o Java chamando o `javac` como processo separado, em vez do compilador
embutido. O compilador embutido abre os jars do SDK como sistema de arquivos zip
e falha ao fechá-los; o `javac` de fora não passa por esse caminho. Com isso o
build passa, debug e release.

**Como resolver de verdade** (fora do escopo do projeto), em ordem de preferência:

1. Descobrir e desativar o que monitora `AppData\Local`. Há um Google Drive para
   Desktop rodando nesta máquina; vale testar com ele fechado. Listar os drivers
   de filtro (`fltmc filters`) exige prompt de administrador.
2. Excluir a pasta do SDK do antivírus.
3. Reinstalar o Android SDK fora de `AppData\Local`, por exemplo em
   `C:\Android\Sdk`, e apontar o `sdk.dir` do `local.properties` para lá.

Resolvido isso, o bloco `tasks.withType<JavaCompile>` pode ser removido.

## Ícone

A arte original está em `arte/Sikey_icone.png` (1024×1024, fundo transparente),
com o fonte do GIMP ao lado. Os PNG do lançador não são editados à mão: saem
dela por `arte/MakeIcons.java`.

```bash
java arte/MakeIcons.java arte/Sikey_icone.png app/src/main/res
```

O programa recorta a moldura transparente e gera, para cada densidade, duas
coisas com regras diferentes:

- `ic_launcher.png` — ícone legado (Android 7 e anteriores), com a arte ocupando
  92% do quadrado.
- `ic_launcher_foreground.png` — primeiro plano do ícone adaptativo, com a arte
  dentro da zona segura de 66dp do canvas de 108dp. Fora dela, a máscara do
  lançador corta: num aparelho a máscara é círculo, no outro é quadrado
  arredondado, e o desenho não pode depender de qual.

A redução é feita pela metade de cada vez até chegar perto do tamanho final.
Ir de 1024 para 48 num passo só borra os traços finos do escudo.

O ícone adaptativo não declara camada `monochrome`: o ícone temático do Android
13 usa só o canal alfa, e como o miolo do escudo é opaco a silhueta viraria um
borrão sólido. Sem a camada, o sistema usa o ícone normal.

## Assinatura

A chave de release do SiKey é uma RSA de 2048 bits, válida até 29/01/2054, com
este certificado:

```
CN=Caio Cunha, OU=SiKey, O=SiKey, L=Aparecida de Goiania, ST=Goias, C=BR
SHA-256: 6F:FA:31:61:6B:EC:74:8C:8F:86:D4:7A:DF:1D:FE:0F:
         F7:C6:6A:09:CA:62:5D:79:C9:63:60:72:4F:07:43:7D
```

Essa impressão digital é **pública**: ela vai dentro de todo APK assinado por
essa chave, e é justamente o número que o próprio SiKey mostra. Serve para
qualquer pessoa conferir se um APK do SiKey veio mesmo daqui.

Nem a chave nem as senhas estão no repositório:

| O quê | Onde | No git? |
|---|---|---|
| Chave privada (`sikey-release.jks`) | fora do repositório, em `chaves-android/` | nunca |
| Senhas e caminho (`key.properties`) | raiz do projeto | no `.gitignore` |

A chave ficou **fora** da pasta do projeto de propósito. O repositório é
público, e um `.jks` dentro dele depende de o `.gitignore` estar certo para
sempre; fora dele, nem um `git add -f` distraído alcança.

### Faça backup dos dois arquivos

Perder o `.jks` ou a senha significa **nunca mais** conseguir publicar uma
atualização do SiKey. Não existe recuperação: a chave privada não está em lugar
nenhum além desse arquivo. Guarde uma cópia dos dois em outro lugar, hoje.

E trocar de chave depois não resolve: o Android identifica o app pelo par
`applicationId` + certificado. Um APK assinado com chave diferente é **recusado**
na atualização, e quem já tinha o app precisa desinstalar antes de instalar o
novo.

### Refazer (só antes de distribuir)

```bash
keytool -genkeypair -v \
  -keystore ~/chaves-android/sikey-release.jks -storetype PKCS12 \
  -alias sikey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=..., OU=SiKey, O=SiKey, L=..., ST=..., C=BR"
```

Depois é só apontar `key.properties` para o arquivo novo. O `app/build.gradle.kts`
lê as quatro propriedades (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`) e só monta a configuração de assinatura se as quatro existirem e
o `.jks` estiver no lugar — meia configuração falharia tarde, na hora de
empacotar, com uma mensagem que não diz o que falta.

O APK sai com os esquemas v2 e v3. O v1 está desligado porque só serve para
Android 6 ou anterior, e o `minSdk` aqui é 24. O v3 grava a linhagem que permite
trocar de chave um dia sem quebrar a atualização — sem ele, essa porta fica
fechada para sempre.

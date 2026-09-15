# SiKey

An Android app that shows the **SHA-256, SHA-1 and MD5** fingerprints of the
apps installed on your device, and of any `.apk` file.

It answers a single question, one that comes up all the time: *is this app
really the one I think it is?*

## Two different things, both called "the app's hash"

Mixing them up is the most common mistake when verifying an APK.

| | Signing certificate | APK file |
|---|---|---|
| Answers | **who** signed it | **which** file it is |
| Changes when the app is updated? | no | yes, every version |
| Where you see this number | Play Console, Firebase, `assetlinks.json`, `keytool`, `apksigner` | download pages, `sha256sum` |
| Use it to | check the APK came from who you expect | verify a download byte for byte |

To decide whether to trust an APK downloaded outside a store, the number that
matters is the **certificate** one: it is the same across every version a
developer publishes. The file hash only proves the download wasn't corrupted or
swapped.

## Features

- List of installed apps, searchable by name or package, with an option to
  include system apps.
- Per app: the three certificate hashes, the three hashes of `base.apk` (and of
  each split, when present), plus issuer, validity, key algorithm and serial
  number.
- **Compare box**: paste the expected value and the app tells you whether it
  matches, and what it matched. Any formatting works — with colons, with
  spaces, upper or lower case.
- **Verify APK file**: pick an `.apk` with the file picker and get the same
  analysis, without installing anything. SiKey also shows up under "Open with"
  in file managers.
- Copy each hash, copy everything at once, share the report.

Certificate hashes are copied in `AA:BB:CC` form (as in Play Console and
`keytool`); file hashes as plain lowercase hex (as in `sha256sum`).

The interface is available in Portuguese (default) and English.

## About MD5 and SHA-1

They are shown because older tools still print them, and sometimes that is all
the other side gives you. Both have had practical collisions for years and must
not be used to decide whether an APK is trustworthy. **Use SHA-256.**

## Building

Requires the Android SDK and JDK 17 or newer. `local.properties` points to the
SDK on your machine and is not committed.

```bash
./gradlew :app:assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/app-debug.apk`. To install it:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Without a `key.properties` file at the project root, the release build is signed
with the debug key — installable, no setup needed. With the file present, it is
signed with the release key. See [Signing](#signing) below.

Toolchain: Gradle 9.3.1, AGP 9.1.0, AGP's built-in Kotlin
(`android.builtInKotlin=true`, without applying the Kotlin plugin),
`compileSdk` 36, `minSdk` 24.

## The QUERY_ALL_PACKAGES permission

Since Android 11, an app can only see other apps if it declares
`QUERY_ALL_PACKAGES`. Without it the list would be nearly empty, so this
permission is the whole app, not a detail.

Google Play treats it as a restricted permission and requires a justification to
publish. For personal use or distribution outside Play, nothing changes.

## Verified against

The values were compared with `apksigner` and `sha256sum` for both kinds of
hash, and they match:

```
apksigner verify --print-certs app-debug.apk
sha256sum app-debug.apk
```

Tested on Android 16 (SDK 36) and Android 9 (SDK 28).

## Known limitation

Reading **another** app's `base.apk` depends on the Android version. When the
system doesn't allow it, the file section shows the error and the certificate
hashes still appear normally — they come from the PackageManager, not from the
file.

## Known issue on Windows

On the author's machine, **any** Java program fails to close a `.zip`/`.jar`
file located under `AppData\Local` — which is exactly where the Android SDK is
installed by default. The error is always the same:

```
java.nio.file.FileSystemException: ...jar: The process cannot access the file
because it is being used by another process
```

It is not a project problem. It reproduces in plain Java, without Gradle, and
depends only on **where** the file is: the very same `.jar`, byte for byte,
opens and closes fine outside `AppData\Local`.

```java
// java ZipTest.java <path-to-jar>
FileSystem fs = FileSystems.newFileSystem(Paths.get(args[0]));
fs.close();   // fails if the jar is under AppData\Local
```

| Location of the same jar | Close |
|---|---|
| `C:\Users\<user>\` | ok |
| `C:\Users\<user>\AppData\Roaming\` | ok |
| `C:\ziptest\` | ok |
| `C:\Users\<user>\AppData\Local\` | **fails** |

The SDK's `apksigner` breaks for the same reason: the JVM can't even load the
classes in `apksigner.jar` from there. Copied elsewhere, it runs fine.

**What the project does about it:** `app/build.gradle.kts` tells Gradle to
compile Java by running `javac` as a separate process instead of the in-process
compiler. The in-process compiler opens the SDK jars as zip file systems and
fails to close them; an external `javac` doesn't go down that path. With that,
both debug and release builds pass.

On machines without the problem the workaround is harmless. Its only cost is
losing incremental Java compilation, and the only Java in the project is the
handful of classes ViewBinding generates.

**How to actually fix it** (outside the project's scope), in order of
preference:

1. Find and disable whatever is monitoring `AppData\Local`. Google Drive for
   Desktop was running on the affected machine, so it is worth testing with it
   closed. Listing filesystem filter drivers (`fltmc filters`) requires an
   administrator prompt.
2. Exclude the SDK folder from your antivirus.
3. Reinstall the Android SDK outside `AppData\Local`, e.g. in `C:\Android\Sdk`,
   and point `sdk.dir` in `local.properties` there.

Once that's fixed, the `tasks.withType<JavaCompile>` block can be removed.

## Icon

The original artwork is in `arte/Sikey_icone.png` (1024×1024, transparent
background), with the GIMP source next to it. Launcher PNGs are not edited by
hand: they are generated from it by `arte/MakeIcons.java`.

```bash
java arte/MakeIcons.java arte/Sikey_icone.png app/src/main/res
```

The program trims the transparent frame and generates, for each density, two
things with different rules:

- `ic_launcher.png` — legacy icon (Android 7 and earlier), with the artwork
  filling 92% of the square.
- `ic_launcher_foreground.png` — adaptive icon foreground, with the artwork
  inside the 66dp safe zone of the 108dp canvas. Outside it, the launcher mask
  crops: one device masks to a circle, another to a rounded square, and the
  drawing can't depend on which.

Downscaling halves the image repeatedly until it is close to the target size.
Going from 1024 to 48 in a single step blurs the shield's thin lines.

The adaptive icon doesn't declare a `monochrome` layer: Android 13 themed icons
use only the alpha channel, and since the inside of the shield is opaque the
silhouette would become a solid blob. Without the layer, the system uses the
regular icon.

## Signing

SiKey's release key is a 2048-bit RSA key, valid until 2054-01-29, with this
certificate:

```
CN=Caio Cunha, OU=SiKey, O=SiKey, L=Aparecida de Goiania, ST=Goias, C=BR
SHA-256: 6F:FA:31:61:6B:EC:74:8C:8F:86:D4:7A:DF:1D:FE:0F:
         F7:C6:6A:09:CA:62:5D:79:C9:63:60:72:4F:07:43:7D
```

This fingerprint is **public**: it is embedded in every APK signed with this
key, and it is exactly the number SiKey itself shows. Anyone can use it to check
that a SiKey APK really came from here.

Neither the key nor its passwords are in the repository:

| What | Where | In git? |
|---|---|---|
| Private key (`sikey-release.jks`) | outside the repository, in the author's `~/chaves-android/` | never |
| Passwords and path (`key.properties`) | project root | in `.gitignore` |

The key lives **outside** the project folder on purpose. The repository is
public, and a `.jks` inside it would rely on `.gitignore` being right forever;
outside it, not even a careless `git add -f` can reach it.

### Back up both files

Losing the `.jks` or its password means **never** being able to publish another
SiKey update. There is no recovery: the private key exists nowhere but in that
file. Keep a copy of both somewhere else.

Switching keys later doesn't help either: Android identifies an app by the
`applicationId` + certificate pair. An APK signed with a different key is
**rejected** as an update, and anyone who already had the app has to uninstall
it before installing the new one.

### Regenerating (only before distributing)

```bash
keytool -genkeypair -v \
  -keystore ~/chaves-android/sikey-release.jks -storetype PKCS12 \
  -alias sikey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=..., OU=SiKey, O=SiKey, L=..., ST=..., C=BR"
```

Then create `key.properties` at the project root pointing at it:

```properties
storeFile=C:/path/to/sikey-release.jks
storePassword=...
keyAlias=sikey
keyPassword=...
```

`app/build.gradle.kts` reads those four properties and only sets up the signing
config if all four exist and the `.jks` is in place — a half configuration would
fail late, at packaging time, with a message that doesn't say what's missing.

The APK is signed with schemes v2 and v3. v1 is disabled because it only matters
for Android 6 and earlier, and `minSdk` here is 24. v3 records the lineage that
makes it possible to rotate the key one day without breaking updates — without
it, that door stays closed forever.

## License

[MIT](LICENSE) © 2026 Caio Cunha

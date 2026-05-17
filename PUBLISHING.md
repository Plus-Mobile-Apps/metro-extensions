# Publishing

Maintainer notes for cutting releases of `metro-extensions` to Maven Central.

## Coordinates

Group: `com.plusmobileapps.metro-extensions`

| Module | Artifact ID |
|---|---|
| `:lib:assisted-factory:runtime` | `assisted-factory-runtime` |
| `:lib:assisted-factory:compiler` | `assisted-factory-compiler` |

The version for all assisted-factory artifacts is controlled by the `assistedFactory` key in `gradle/libs.versions.toml`. Each extension added in the future should follow the same convention: a single shared version key in the catalog referenced from the module's `mavenPublishing { coordinates(...) }` block.

## One-time setup

### Maven Central (Central Portal)

The project publishes through the **Central Portal**, not the legacy OSSRH/S01 host. You need:

1. A Sonatype Central Portal account with the `com.plusmobileapps` namespace verified.
2. A **user token** generated from the Central Portal UI (token name + secret — these become `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD`, NOT your portal login).

### Signing key

Maven Central requires signed artifacts. Generate an ASCII-armored GPG key and export it:

```bash
gpg --full-generate-key                       # RSA 4096, no expiry recommended
gpg --list-secret-keys --keyid-format LONG    # grab the long key id
gpg --armor --export-secret-keys <KEY_ID>     # ASCII-armored secret key
```

Distribute the public half to a keyserver so the Central Portal validator can find it:

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
```

### Local credentials — `~/.gradle/gradle.properties`

Put your publishing credentials in your **user-home Gradle properties file** at `~/.gradle/gradle.properties` — NOT in this repo's `gradle.properties` (committed) and NOT in `local.properties` (Gradle does not load it as project properties; AGP reads it only for `sdk.dir`).

The vanniktech maven-publish plugin reads these property names directly (no `ORG_GRADLE_PROJECT_` prefix when stored in a properties file — that prefix is only for environment variables):

```properties
# ~/.gradle/gradle.properties
mavenCentralUsername=<Central Portal user-token name>
mavenCentralPassword=<Central Portal user-token secret>

signingInMemoryKeyId=<last 8 chars of the GPG key id>
signingInMemoryKeyPassword=<GPG key passphrase>
signingInMemoryKey=<ASCII-armored secret key>
```

The `signingInMemoryKey` value is the entire `-----BEGIN PGP PRIVATE KEY BLOCK-----…-----END PGP PRIVATE KEY BLOCK-----` block. Two formats work:

- **Single line with `\n` literals:** replace every newline with the two characters `\n` so the whole thing fits on one line.
- **Multi-line with backslash continuations:** end every line of the armored block (except the last) with a trailing `\` so Java's `Properties` parser folds them into one logical value.

Generate the export with:

```bash
gpg --armor --export-secret-keys <LONG_KEY_ID>
```

> **Never** put these in `local.properties`, the repo's `gradle.properties`, or anywhere git-tracked. Treat `~/.gradle/gradle.properties` as machine-local secrets.

### GitHub secrets

Set the following on the repo (`Settings → Secrets and variables → Actions`):

| Secret | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Central Portal user-token name |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user-token secret |
| `SIGNING_KEY_ID` | Last 8 chars of the GPG key id |
| `SIGNING_PASSWORD` | Passphrase for the GPG key |
| `GPG_KEY_CONTENTS` | ASCII-armored secret key (entire `-----BEGIN…-----END-----` block) |

The CI workflow at `.github/workflows/publish-libraries.yml` exports each as an `ORG_GRADLE_PROJECT_<name>` environment variable — Gradle maps those to the same project property names the plugin reads (`mavenCentralUsername`, `signingInMemoryKey`, etc.).

## Local dry run

Before tagging a release, verify the artifacts publish to your local Maven repo:

```bash
./gradlew publishToMavenLocal --no-configuration-cache
```

Inspect `~/.m2/repository/com/plusmobileapps/metro-extensions/` for the expected per-target artifacts (`-jvm`, `-android`, `-iosarm64`, etc. for the runtime; a single jar for the compiler). Confirm `.asc` signature files appear alongside each artifact, and that `gpg --verify <file>.asc <file>` passes.

For a full pre-release rehearsal that exercises uploading without auto-releasing the deployment, run:

```bash
./gradlew publishAllPublicationsToMavenCentralRepository --no-configuration-cache
```

This stages artifacts in a Central Portal deployment but does not release them — useful for catching POM/signing problems.

## Cutting a release

1. Bump `assistedFactory` in `gradle/libs.versions.toml` (semver — `0.1.0` → `0.2.0` for new extensions, `0.1.1` for fixes).
2. Commit + push to `main`.
3. Create a GitHub release with tag `v<version>` (e.g. `v0.2.0`). Mark as **pre-release** for `-SNAPSHOT`/`-alpha` versions if desired — the workflow fires on both `released` and `prereleased`.
4. The `Publish` workflow (`.github/workflows/publish-libraries.yml`) runs `./gradlew publishToMavenCentral`, which uploads + automatically releases the deployment via the Central Portal API.
5. Verify on https://central.sonatype.com/ → namespace `com.plusmobileapps.metro-extensions`. New versions usually appear in Maven Central search within ~30 minutes.

## Adding a new extension

When introducing a new extension (e.g. `:lib:my-extension:{runtime,compiler}`):

1. Add a version key to `gradle/libs.versions.toml` (e.g. `myExtension = "0.1.0"`).
2. In each module's `build.gradle.kts`, apply `alias(libs.plugins.mavenPublish)` and add a `mavenPublishing { … }` block — copy from `lib/assisted-factory/{runtime,compiler}/build.gradle.kts` and adjust coordinates / POM name / description.
3. No workflow changes needed — `publishToMavenCentral` aggregates every publishable subproject.

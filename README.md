# Jenkinsfile Studio

Free Jenkinsfile editing support for GoLand and IntelliJ-based IDEs. This plugin is MIT licensed, runs locally, and has no paid plugin or server requirement.

## Features

- Recognizes `Jenkinsfile`, files whose names start with `Jenkinsfile` (such as `Jenkinsfile_Production`), and files ending in `.jenkinsfile`.
- Highlights Groovy syntax, Jenkins Declarative Pipeline keywords, method calls, named argument field keys (such as `name`, `choices`, and `description`), constants, comments, strings, operators, and paired braces.
- Reports Groovy grammar syntax errors while editing.
- Provides context-aware completion for Declarative Pipeline sections and conditions, common built-in steps, globals, and other frequently used DSL entries.
- Shows pipeline blocks such as `pipeline`, `stages`, `stage`, `steps`, `post`, and `when` in the IDE Structure view for quick navigation.
- Formats blocks and expressions through the IDE's **Reformat Code** action (`Ctrl+Alt+L`) or the Jenkinsfile-only `Ctrl+Shift+L` shortcut on the default keymap. Formatting keeps statement line breaks and canonicalizes spaces and indentation, so running it again leaves the file unchanged.
- Imports Jenkins Pipeline method and global names from a controller-generated IntelliJ GDSL file.

The plugin supplies its own Jenkinsfile language support, so GoLand does not need a separate Groovy plugin. It bundles Apache Groovy's parser to check Groovy grammar.

Jenkins Pipeline DSL entries can vary by controller version, installed plugins, and shared libraries. The bundled completion list is a useful baseline; importing GDSL adds method and global names from a specific controller. The importer currently does not read method signatures or validate Jenkins-specific Pipeline rules. Use the target controller's Pipeline Syntax and linter for server-side validation.

## Import DSL entries from Jenkins

1. Open a Jenkins job's **Pipeline Syntax** page.
2. Download **IntelliJ IDEA GDSL** from that page.
3. In the IDE, choose **Tools → Import Jenkins Pipeline GDSL…** and select the downloaded file.

Imported names are stored in the IDE's project workspace settings. The GDSL file stays on your computer. Re-import it after Jenkins plugins or shared libraries change.

## Install from a release

Download `jenkinsfile-jetbrains-plugin-<version>.zip` from [GitHub Releases](https://github.com/neko233-com/Jenkinsfile-jetbrains-plugin/releases). In the IDE, open **Settings → Plugins → ⚙ → Install Plugin from Disk…**, select the ZIP, then restart when prompted.

The plugin and its source are free. JetBrains IDE licensing is separate.

## Build from source

Requirements: JDK 25 and the included Gradle Wrapper. By default the build downloads the GoLand 2026.2.1 SDK. To use a local JetBrains IDE installation:

```powershell
.\gradlew.bat -PplatformPath="D:\IDE\Jetbrains\GoLand" buildPlugin verifyPluginStructure
```

The plugin archive is written to `build/distributions/`. Run `verifyPlugin` to check the plugin against the configured IDE.

## Compatibility

The plugin declares IntelliJ Platform build compatibility from 243 through 262.* (2024.3–2026.2) and depends only on the shared IntelliJ Platform module. Version 1.0.2 was built and verified with GoLand 2026.2.1.1 and IntelliJ IDEA 2026.2.1 using the Marketplace Plugin Verifier.

The plugin makes no network calls and does not upload source files. GDSL import reads a local file. See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for the bundled parser license.

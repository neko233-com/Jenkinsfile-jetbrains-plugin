# Jenkinsfile Free

Free Jenkinsfile editing support for GoLand and IntelliJ-based IDEs. This plugin is MIT licensed, runs locally, and has no paid plugin or server requirement.

## Features

- Recognizes every filename containing `jenkinsfile`, case-insensitively and regardless of extension: for example, `Jenkinsfile`, `deploy.JENKINSFILE.prod`, `my-jEnKiNsFiLe.groovy`, and `release_jenkinsfile_backup`.
- Highlights Groovy syntax, Jenkins Declarative Pipeline keywords, method calls, named argument field keys (such as `name`, `choices`, and `description`), constants, comments, strings, operators, and paired braces.
- Highlights common script syntax inside Jenkins `sh`, `bat`/`cmd`, `powershell`, and `pwsh` string arguments, including commands, options, variables, operators, keywords, strings, and comments.
- Reports Groovy grammar syntax errors while editing.
- Provides context-aware completion for Declarative Pipeline sections and conditions, common built-in steps, globals, and other frequently used DSL entries.
- Shows pipeline blocks such as `pipeline`, `stages`, `stage`, `steps`, `post`, and `when` in the IDE Structure view for quick navigation.
- Formats blocks and expressions through the IDE's **Reformat Code** action (`Ctrl+Alt+L`) or the Jenkinsfile-only `Ctrl+Shift+L` shortcut on the default keymap. It uses four-space indentation, separates Pipeline sections and stages, and puts longer named-argument lists on one field per line. Safe `sh`, `bat`/`cmd`, and PowerShell literals are split into readable multiline scripts, with each dialect's continuation syntax; existing multiline scripts are formatted in place. Ambiguous shell constructs are left untouched. Repeating the formatter leaves the file unchanged.
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

## JetBrains Marketplace

Marketplace listing: [Jenkinsfile Free](https://plugins.jetbrains.com/plugin/35027-jenkinsfile-free).

Version **1.0.5** was submitted on **14 October 2026** on the Stable channel with public visibility (the Hidden flag is off). JetBrains accepted the upload and reported no problems so far; the Support team's additional checks are pending, and the submission status is **Under review**. The listing will become publicly available and searchable after JetBrains approves it; submission alone does not mean approval. Until then, use the GitHub release ZIP above.

The Marketplace plugin ID is `35027`, the plugin XML ID is `com.neko233.jenkinsfile`, and the submitted update ID is `1193696`. The plugin ID remains the same across the rename so existing installations can receive updates.

## Build from source

Requirements: JDK 25 and the included Gradle Wrapper. By default the build downloads the GoLand 2026.2.1 SDK. To use a local JetBrains IDE installation:

```powershell
.\gradlew.bat -PplatformPath="D:\IDE\Jetbrains\GoLand" buildPlugin verifyPluginStructure
```

The plugin archive is written to `build/distributions/`. Run `verifyPlugin` to check the plugin against the configured IDE.

## Compatibility

The plugin declares IntelliJ Platform build compatibility from 243 through 262.* (2024.3–2026.2) and depends only on the shared IntelliJ Platform module. Version 1.0.5 passes the local IntelliJ Plugin Verifier against the installed GoLand build `GO-262.9437.286`. Marketplace Plugin Verifier 1.410 reports **Compatible** for GoLand 2026.3 EAP build `263.6259.35` and IntelliJ IDEA 2026.2.3; the declared compatibility range remains 243 through 262.*.

The plugin makes no network calls and does not upload source files. GDSL import reads a local file. See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for the bundled parser license.

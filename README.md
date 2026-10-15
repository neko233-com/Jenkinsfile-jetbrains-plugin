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

## VS Code

The repository also contains a free VS Code extension under the same **Jenkinsfile Free** name and `neko233-com` authorship. Version **1.0.2**, including the classic Jenkins butler icon and the formatting improvements from 1.0.1, is publicly available on the [VS Code Marketplace](https://marketplace.visualstudio.com/items?itemName=neko233-com.jenkinsfile-free). Search for **Jenkinsfile Free** in the Extensions view or run `code --install-extension neko233-com.jenkinsfile-free`. Public version history and an anonymous VSIX download were verified on **15 October 2026**; the downloaded package's SHA-256 matches the release VSIX. The packaged extension passed a VS Code 1.142.0 Extension Host check for activation, filename recognition, Format Document, five unchanged repeated passes, and format on save. See [`vscode/README.md`](vscode/README.md) for features, limitations, offline installation, and build instructions.

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

Version **1.0.5**, submitted on **14 October 2026**, has been approved and is publicly available on the Stable channel. Search for **Jenkinsfile Free** in the IDE's Marketplace tab or open the listing above. The listing is free, supports GoLand and IntelliJ-based IDEs, and provides public version downloads.

The Marketplace plugin ID is `35027`, the plugin XML ID is `com.neko233.jenkinsfile`, and the approved update ID is `1193696`. The plugin ID remains the same across the rename so existing installations can receive updates.

### 1.0.6 update

Version **1.0.6** adds the classic Jenkins butler icons and fixes indentation throughout nested blocks and multiline argument lists. Its GoLand 2026.2.1.1 platform tests pass (22 tests), and Plugin Verifier reports **Compatible** with `GO-262.9437.286`. The user-reported release fixture also matches its expected layout after formatting in the isolated GoLand instance. Marketplace submission and approval are tracked separately from these local checks.

For installations still showing 1.0.1, an update may already have been downloaded and be waiting for an IDE restart. On 15 October, the local GoLand log confirmed 1.0.5 downloaded at 17:35 and loaded after restarting at 18:18. Marketplace TLS or HTTP errors can also prevent the update page and artwork from loading.

## Build from source

Requirements: JDK 25 and the included Gradle Wrapper. By default the build downloads the GoLand 2026.2.1 SDK. To use a local JetBrains IDE installation:

```powershell
.\gradlew.bat -PplatformPath="D:\IDE\Jetbrains\GoLand" buildPlugin verifyPluginStructure
```

The plugin archive is written to `build/distributions/`. Run `verifyPlugin` to check the plugin against the configured IDE.

If Gradle launched by a packaged Windows application fails with `PipeImpl` / `UnixDomainSockets.connect0: Invalid argument`, use a workspace-local socket directory for that build process. Set `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<absolute workspace path>/build/runtime-tmp` and create that directory before launching Gradle. This avoids the failing temporary socket path; no system security setting needs to change.

## Compatibility

The plugin declares IntelliJ Platform build compatibility from 243 through 262.* (2024.3–2026.2) and depends only on the shared IntelliJ Platform module. Version 1.0.5 passes the local IntelliJ Plugin Verifier against the installed GoLand build `GO-262.9437.286`. Marketplace Plugin Verifier 1.410 reports **Compatible** for GoLand 2026.3 EAP build `263.6259.35` and IntelliJ IDEA 2026.2.3; the declared compatibility range remains 243 through 262.*.

The plugin makes no network calls and does not upload source files. GDSL import reads a local file. See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for the bundled parser license.

Jenkins butler artwork is by the [Jenkins project](https://www.jenkins.io/) and Frontside, under [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/). Code remains MIT and is authored by **neko233-com**. This is an independent plugin, not an official Jenkins project product.

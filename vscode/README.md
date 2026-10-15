# Jenkinsfile Free for VS Code

Free Jenkinsfile support for VS Code, published under **neko233-com**.

## Features

- Automatically recognizes any file whose **filename** contains `jenkinsfile`, case-insensitively, with any prefix, suffix, or extension.
- Highlights Groovy syntax, Declarative Pipeline blocks, Jenkins steps, named argument keys such as `name`, `choices`, and `description`, variables, comments, strings, and operators.
- Highlights common commands and variables inside Jenkins `sh`, `bat`/`cmd`, `powershell`, and `pwsh` arguments.
- Completes common Pipeline sections, parameters, steps, named fields, Jenkins globals, and environment variables.
- Shows `pipeline`, section blocks, and named stages in the Outline view.
- Formats a document with **Format Document** (`Shift+Alt+F` on the default Windows keymap) or **Jenkinsfile Free: Format Jenkinsfile** from the Command Palette. The formatter uses four spaces by default, places long named arguments on separate lines, and formats safe embedded shell scripts as readable multiline literals. Unsupported shell constructs are preserved. Repeating formatting leaves the file unchanged.
- Reports unclosed strings, comments, and mismatched braces, brackets, or parentheses.

The formatter and basic diagnostics run locally. Jenkins Pipeline step names and completions are a static baseline; installed Jenkins plugins, shared libraries, and controller-specific Pipeline rules can differ. Use the Jenkins controller's Pipeline Syntax/linter for server-side validation.

## Formatting example

```groovy
pipeline {
    parameters {
        choice(
            name: 'RELEASE_CHANNEL',
            choices: ['wechat', 'taptap'],
            description: 'Choose one release channel'
        )
    }

    stages {
        stage('Release') {
            steps {
                sh '''
                    go run ./tools/release/cmd \\
                        --channel="$RELEASE_CHANNEL" \\
                        --environment=test
                '''
            }
        }
    }
}
```

## Install

Version **1.0.0** is available for free on the [VS Code Marketplace](https://marketplace.visualstudio.com/items?itemName=neko233-com.jenkinsfile-free), published by **neko233-com**. Search for **Jenkinsfile Free** in the VS Code Extensions view, or run:

```powershell
code --install-extension neko233-com.jenkinsfile-free
```

Public Marketplace search, the Free label, and an anonymous download of version 1.0.0 were verified on **15 October 2026**. The downloaded VSIX matches the release package's SHA-256.

For offline installation, download the VSIX from the [GitHub release](https://github.com/neko233-com/Jenkinsfile-jetbrains-plugin/releases/tag/vscode-v1.0.0), then run:

```powershell
code --install-extension .\jenkinsfile-free-1.0.0.vsix
```

## Build and verify

Requirements: Node.js 20.18 or newer and npm.

```powershell
npm ci
npm test
npm run compile
npm run package
```

The distributable `.vsix` is written to this directory. Publishing requires the `neko233-com` VS Code Marketplace publisher account and its authorized publishing credential; credentials must not be committed to this repository.

## License and authorship

MIT. Author and publisher: [neko233-com](https://github.com/neko233-com).

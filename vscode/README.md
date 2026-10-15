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

Install from the free [VS Code Marketplace listing](https://marketplace.visualstudio.com/items?itemName=neko233-com.jenkinsfile-free), published by **neko233-com**. Search for **Jenkinsfile Free** in the VS Code Extensions view, or run:

```powershell
code --install-extension neko233-com.jenkinsfile-free
```

Requires VS Code **1.90.0** or newer. The extension works locally and has no paid service requirement.

For offline installation, download the VSIX from the [GitHub release](https://github.com/neko233-com/Jenkinsfile-jetbrains-plugin/releases/tag/vscode-v1.0.1), then run:

```powershell
code --install-extension .\jenkinsfile-free-1.0.1.vsix
```

## Formatting style

The formatter uses one indentation level per block, one blank line between Pipeline sections and stages, and one field per line for calls with three or more named arguments. Multiline parameter definitions are separated by one blank line. Short shell commands stay on one line; longer safe scripts use readable multiline literals. Lists, indexing, operators, comments, and optional statement semicolons follow a consistent layout. Strings are never split or trimmed, and unfinished edits are preserved.

`jenkinsfileFree.format.printWidth` sets the preferred line width (default **100**, range **60–200**). Long string literals remain intact even when they exceed this width. The formatter respects the editor's indentation and line endings and writes exactly one final newline. See the [complete release example](examples/Jenkinsfile.release).

The extension includes a JF icon for the Extensions view and a file icon for Jenkinsfile documents. File icon themes may override the contributed file icon.

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

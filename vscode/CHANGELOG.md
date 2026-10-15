# Changelog

## 1.0.2

- Use the classic Jenkins butler artwork for the extension and Jenkinsfile file icons, in both light and dark themes.
- Include the Jenkins project / Frontside attribution and CC BY-SA 3.0 artwork license notice.
- Retain the formatting improvements and idempotence fixes from 1.0.1.

## 1.0.1

- Add the JF extension icon and light/dark Jenkinsfile file icons, with immediate filename recognition before activation.
- Keep short commands compact; wrap long argument lists and safe shell scripts using a configurable preferred width (100 by default).
- Normalize collections, operators, optional statement semicolons, section spacing, and the final newline.
- Preserve argument comments, string contents, unfinished edits, and empty control statements. Cover the reported release pipeline with a golden example and repeated-format checks.
- Select Jenkinsfile Free as the default formatter for Jenkinsfile documents while respecting explicit user settings.

## 1.0.0

- Initial VS Code extension with Jenkinsfile detection, syntax highlighting, common completions, Outline symbols, structural diagnostics, and idempotent formatting.

package com.neko233.jenkinsfile.highlighting;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.neko233.jenkinsfile.language.JenkinsfileFileType;

import java.util.List;

public final class JenkinsfileEmbeddedScriptAnnotatorTest extends BasePlatformTestCase {
    public void testSelectsOnlyScriptArgumentsForShellFamilySteps() {
        String source = """
                pipeline { stages { stage('x') { steps {
                    sh 'AUTO_BUMP=true go run ./cmd --action=\"$RELEASE_ACTION\"'
                    bat 'echo %BUILD_NUMBER%'
                    cmd 'echo %PATH%'
                    powershell(label: 'release label', script: \"\"\"Write-Output $env:VERSION\"\"\")
                    pwsh(script: \"$env:CI\")
                    echo 'the text sh is not a script step'
                } } } }
                """;

        List<JenkinsfileEmbeddedScriptAnnotator.ScriptLiteral> scripts =
                JenkinsfileEmbeddedScriptAnnotator.findScriptLiterals(source);

        assertEquals(5, scripts.size());
        assertEquals("AUTO_BUMP=true go run ./cmd --action=\"$RELEASE_ACTION\"", scripts.get(0).text());
        assertEquals("echo %BUILD_NUMBER%", scripts.get(1).text());
        assertEquals("echo %PATH%", scripts.get(2).text());
        assertEquals("Write-Output $env:VERSION", scripts.get(3).text());
        assertEquals("$env:CI", scripts.get(4).text());
    }

    public void testShellAndPowerShellTokensAreClassified() {
        String shellSource = "AUTO_BUMP=true go run ./cmd --action=\"$RELEASE_ACTION\"; test -z \"$(git status --short)\" # release";
        List<EmbeddedScriptLexer.Segment> shell = EmbeddedScriptLexer.lex(
                shellSource,
                EmbeddedScriptLexer.Mode.POSIX, 0);
        assertHas(shellSource, shell, "go", EmbeddedScriptLexer.Kind.COMMAND);
        assertHas(shellSource, shell, "git", EmbeddedScriptLexer.Kind.COMMAND);
        assertHas(shellSource, shell, "$RELEASE_ACTION", EmbeddedScriptLexer.Kind.VARIABLE);
        assertHas(shellSource, shell, "--action", EmbeddedScriptLexer.Kind.PARAMETER);
        assertHas(shellSource, shell, "# release", EmbeddedScriptLexer.Kind.COMMENT);

        String powershellSource = "Write-Output $env:RELEASE_CHANNEL -eq 'release' # deploy";
        List<EmbeddedScriptLexer.Segment> powershell = EmbeddedScriptLexer.lex(
                powershellSource,
                EmbeddedScriptLexer.Mode.POWERSHELL, 0);
        assertHas(powershellSource, powershell, "Write-Output", EmbeddedScriptLexer.Kind.COMMAND);
        assertHas(powershellSource, powershell, "$env:RELEASE_CHANNEL", EmbeddedScriptLexer.Kind.VARIABLE);
        assertHas(powershellSource, powershell, "-eq", EmbeddedScriptLexer.Kind.OPERATOR);
        assertHas(powershellSource, powershell, "'release'", EmbeddedScriptLexer.Kind.STRING);
        assertHas(powershellSource, powershell, "# deploy", EmbeddedScriptLexer.Kind.COMMENT);

        String cmdSource = "echo %BUILD_NUMBER% && exit /b 0";
        List<EmbeddedScriptLexer.Segment> cmd = EmbeddedScriptLexer.lex(cmdSource, EmbeddedScriptLexer.Mode.CMD, 0);
        assertHas(cmdSource, cmd, "echo", EmbeddedScriptLexer.Kind.COMMAND);
        assertHas(cmdSource, cmd, "%BUILD_NUMBER%", EmbeddedScriptLexer.Kind.VARIABLE);
        assertHas(cmdSource, cmd, "&&", EmbeddedScriptLexer.Kind.OPERATOR);
    }

    public void testAnnotatorHighlightsShellCommandInEditor() {
        String source = "pipeline { stages { stage('x') { steps { sh 'go run ./cmd' } } } }";
        myFixture.configureByText(JenkinsfileFileType.INSTANCE, source);

        List<HighlightInfo> highlights = myFixture.doHighlighting();

        int commandOffset = source.indexOf("go run");
        assertTrue("The embedded command should receive a syntax highlighting annotation",
                highlights.stream().anyMatch(info -> info.getStartOffset() == commandOffset
                        && info.getEndOffset() == commandOffset + 2));
    }

    public void testShellFormatterSkipsHereDocsControlFlowAndShebang() {
        assertShellFormattingSkipped("#!/bin/sh\necho start; echo done");
        assertShellFormattingSkipped("cat <<EOF\nvalue; still heredoc\nEOF");
        assertShellFormattingSkipped("if test -f file; then echo yes; fi");
        String continued = "echo first \\" + "\n" + "    && echo second";
        String source = "pipeline { stages { stage('x') { steps { sh '''" + continued + "''' } } } }";
        var literal = JenkinsfileEmbeddedScriptAnnotator.findScriptLiterals(source).get(0);
        assertNull(EmbeddedShellFormatter.formatLiteral(source, literal));
    }

    private static void assertShellFormattingSkipped(String script) {
        String source = "pipeline { stages { stage('x') { steps { sh " + quoteGroovy(script) + " } } } }";
        var literal = JenkinsfileEmbeddedScriptAnnotator.findScriptLiterals(source).get(0);
        assertNull("Unsafe or ambiguous shell source must be left alone: " + script,
                EmbeddedShellFormatter.formatLiteral(source, literal));
    }

    private static String quoteGroovy(String script) {
        String escaped = script.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "\\r").replace("\n", "\\n");
        return "'" + escaped + "'";
    }

    private static void assertHas(String source, List<EmbeddedScriptLexer.Segment> segments, String token, EmbeddedScriptLexer.Kind kind) {
        assertTrue("Expected " + kind + " token: " + token,
                segments.stream().anyMatch(segment -> segment.kind() == kind
                        && token.equals(source.substring(segment.start(), segment.end()))));
    }
}

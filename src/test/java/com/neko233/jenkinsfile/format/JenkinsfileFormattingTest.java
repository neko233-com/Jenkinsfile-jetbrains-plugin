package com.neko233.jenkinsfile.format;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.neko233.jenkinsfile.language.JenkinsfileFileType;

public final class JenkinsfileFormattingTest extends BasePlatformTestCase {
    @Override
    protected String getTestDataPath() {
        return java.nio.file.Path.of("src/test/testData/formatting").toAbsolutePath().toString();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(), getTestDataPath());
        assertNotNull("Jenkinsfile parser extension must be loaded",
                com.intellij.lang.LanguageParserDefinitions.INSTANCE.forLanguage(com.neko233.jenkinsfile.language.JenkinsfileLanguage.INSTANCE));
        assertNotNull("Jenkinsfile formatter extension must be loaded",
                com.intellij.lang.LanguageFormatting.INSTANCE.forLanguage(com.neko233.jenkinsfile.language.JenkinsfileLanguage.INSTANCE));
    }

    public void testUserReleasePipeline() {
        myFixture.configureByFile("release.before.jenkinsfile");
        format();
        myFixture.checkResultByFile("release.after.jenkinsfile");
        assertIdempotent();
    }

    public void testFormatActionIsRegistered() {
        assertNotNull(ActionManager.getInstance().getAction("Jenkinsfile.Reformat"));
    }

    public void testFormatterWinsItsShortcutConflictOnlyInJenkinsfiles() {
        assertTrue(com.intellij.openapi.actionSystem.ActionPromoter.EP_NAME.getExtensionList().stream()
                .anyMatch(promoter -> promoter instanceof com.neko233.jenkinsfile.actions.JenkinsfileActionPromoter));
        myFixture.configureByText(JenkinsfileFileType.INSTANCE, "pipeline {}");
        var actionManager = ActionManager.getInstance();
        var formatter = actionManager.getAction("Jenkinsfile.Reformat");
        var previousSearch = actionManager.getAction("FindPrevious");
        assertNotNull(formatter);
        assertNotNull(previousSearch);

        var context = com.intellij.ide.DataManager.getInstance()
                .getDataContext(myFixture.getEditor().getContentComponent());
        var promoted = new com.neko233.jenkinsfile.actions.JenkinsfileActionPromoter()
                .promote(java.util.List.of(previousSearch, formatter), context);
        assertEquals(1, promoted.size());
        assertSame(formatter, promoted.get(0));

        myFixture.configureByText("plain.txt", "ordinary text");
        context = com.intellij.ide.DataManager.getInstance()
                .getDataContext(myFixture.getEditor().getContentComponent());
        assertTrue(new com.neko233.jenkinsfile.actions.JenkinsfileActionPromoter()
                .promote(java.util.List.of(previousSearch, formatter), context).isEmpty());
    }

    public void testInlinePipelineAndNamedArguments() {
        myFixture.configureByText(JenkinsfileFileType.INSTANCE,
                "pipeline { agent any; stages { stage('Build') { steps { echo 'hello'; sh 'make' } } } }");
        format();
        assertEquals("pipeline {\n    agent any;\n\n    stages {\n        stage('Build') {\n            steps {\n                echo 'hello';\n                sh '''\n                    make\n                '''\n            }\n        }\n    }\n}", myFixture.getFile().getText());
        assertIdempotent();
    }

    public void testNestedParametersAndStagesUseOneIndentPerBlock() {
        myFixture.configureByText(JenkinsfileFileType.INSTANCE,
                "pipeline{parameters{choice(name:'CHANNEL',choices:['wechat','taptap'],description:'target'); string(name:'VERSION',defaultValue:'',description:'version')} stages{stage('Build'){steps{sh 'echo ok'}} stage('Deploy'){steps{sh 'echo done'}}}}");

        format();

        assertEquals("pipeline {\n"
                        + "    parameters {\n"
                        + "        choice(\n"
                        + "            name: 'CHANNEL',\n"
                        + "            choices: ['wechat', 'taptap'],\n"
                        + "            description: 'target'\n"
                        + "        );\n"
                        + "        string(\n"
                        + "            name: 'VERSION',\n"
                        + "            defaultValue: '',\n"
                        + "            description: 'version'\n"
                        + "        )\n"
                        + "    }\n\n"
                        + "    stages {\n"
                        + "        stage('Build') {\n"
                        + "            steps {\n"
                        + "                sh '''\n"
                        + "                    echo ok\n"
                        + "                '''\n"
                        + "            }\n"
                        + "        }\n\n"
                        + "        stage('Deploy') {\n"
                        + "            steps {\n"
                        + "                sh '''\n"
                        + "                    echo done\n"
                        + "                '''\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}", myFixture.getFile().getText());
        assertIdempotent();
    }

    public void testShortArgumentsListsAndComments() {
        myFixture.configureByText(JenkinsfileFileType.INSTANCE,
                "pipeline {\n\n options {\n\n timeout(time:   5 , unit:  'MINUTES')  ; // timeout\n\n }\n\n stages { stage('X') { steps { echo([ 'a' ,  'b' ]) } } }\n\n}");
        format();
        assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("timeout(time: 5, unit: 'MINUTES'); // timeout"));
        assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("echo(['a', 'b'])"));
        assertIdempotent();
    }

    public void testLongPosixShellCommandsBecomeReadableMultilineAndRemainIdempotent() {
        myFixture.configureByText(JenkinsfileFileType.INSTANCE,
                "pipeline { stages { stage('Release') { steps { sh 'AUTO_BUMP=true go run ./tools/production_release/cmd --action=\"$RELEASE_ACTION\" --release-branch=\"$RELEASE_BRANCH\" --release-version=\"$RELEASE_VERSION\" --component game-server --channel=\"$RELEASE_CHANNEL\" --environment=\"$RELEASE_ENVIRONMENT\"' } } } }");

        format();

        String formatted = myFixture.getFile().getText();
        assertTrue(formatted, formatted.contains("sh '''\n"));
        assertTrue(formatted, formatted.contains("AUTO_BUMP=true go run ./tools/production_release/cmd"));
        assertTrue(formatted, formatted.contains("\\\\\n"));
        assertTrue(formatted, formatted.contains("--action=\"$RELEASE_ACTION\""));
        assertTrue(formatted, formatted.contains("--environment=\"$RELEASE_ENVIRONMENT\""));
        assertIdempotent();
    }

    public void testExistingMultilinePosixShellOnlyFormatsItsBody() {
        myFixture.configureByText(JenkinsfileFileType.INSTANCE,
                "pipeline { stages { stage('Release') { steps { sh '''echo start; go test ./...; AUTO_BUMP=true go run ./cmd --action=\"$ACTION\"''' } } } }");

        format();

        String formatted = myFixture.getFile().getText();
        assertTrue(formatted, formatted.contains("sh '''\n"));
        assertTrue(formatted, formatted.contains("echo start\n"));
        assertTrue(formatted, formatted.contains("go test ./...\n"));
        assertTrue(formatted, formatted.contains("AUTO_BUMP=true go run ./cmd"));
        assertIdempotent();
    }

    public void testLongWindowsCommandScriptsBecomeReadableMultilineAndRemainIdempotent() {
        myFixture.configureByText(JenkinsfileFileType.INSTANCE,
                "pipeline { stages { stage('Release') { steps { bat 'robocopy C:\\\\src C:\\\\dst /E /COPY:DAT /R:3 /W:5 /Z /MT:8 /V /ETA /TEE /LOG:release.log /DCOPY:DAT /COPYALL /PURGE /MIR && echo done' } } } }");

        format();

        String formatted = myFixture.getFile().getText();
        assertTrue(formatted, formatted.contains("bat '''\n"));
        assertTrue(formatted, formatted.contains("/COPY:DAT"));
        assertTrue(formatted, formatted.contains(" /E ^\n"));
        assertTrue(formatted, formatted.contains("&& ^\n"));
        assertTrue(formatted, formatted.contains("echo done"));
        assertIdempotent();
    }

    public void testLongPowerShellCommandsBecomeReadableMultilineAndRemainIdempotent() {
        myFixture.configureByText(JenkinsfileFileType.INSTANCE,
                "pipeline { stages { stage('Release') { steps { powershell 'Invoke-WebRequest -Uri \"$env:RELEASE_URL\" -OutFile \"$env:PACKAGE_PATH\" -Method Get -MaximumRedirection 5 -ErrorAction Stop' } } } }");

        format();

        String formatted = myFixture.getFile().getText();
        assertTrue(formatted, formatted.contains("powershell '''\n"));
        assertTrue(formatted, formatted.contains("-Uri \"$env:RELEASE_URL\" `\n"));
        assertTrue(formatted, formatted.contains("-ErrorAction Stop"));
        assertIdempotent();
    }

    public void testShortShellFamilyScriptsBecomeMultilineAndRemainIdempotent() {
        myFixture.configureByText(JenkinsfileFileType.INSTANCE,
                "pipeline { stages { stage('Release') { steps { sh 'echo shell'; bat 'echo batch'; cmd 'echo command'; powershell 'Write-Output ps'; pwsh 'Write-Output pwsh' } } } }");

        format();

        String formatted = myFixture.getFile().getText();
        for (String step : new String[]{"sh", "bat", "cmd", "powershell", "pwsh"}) {
            assertTrue(formatted, formatted.contains(step + " '''\n"));
        }
        assertTrue(formatted, formatted.contains("echo shell"));
        assertTrue(formatted, formatted.contains("echo batch"));
        assertTrue(formatted, formatted.contains("echo command"));
        assertTrue(formatted, formatted.contains("Write-Output ps"));
        assertTrue(formatted, formatted.contains("Write-Output pwsh"));
        assertIdempotent();
    }

    public void testExistingMultilineWindowsScriptsFormatOnlyTheirScriptBodies() {
        myFixture.configureByText(JenkinsfileFileType.INSTANCE,
                "pipeline { stages { stage('Release') { steps { cmd 'echo start && echo done'; powershell 'Write-Output start; Write-Output done' } } } }");

        format();

        String formatted = myFixture.getFile().getText();
        assertTrue(formatted, formatted.contains("cmd '''\n"));
        assertTrue(formatted, formatted.contains("&& ^\n"));
        assertTrue(formatted, formatted.contains("powershell '''\n"));
        assertTrue(formatted, formatted.contains("Write-Output start;\n"));
        assertTrue(formatted, formatted.contains("Write-Output done"));
        assertIdempotent();
    }

    public void testMultilineWindowsScriptsKeepStatementsAndFormatOnlyTheScriptBodies() {
        myFixture.configureByText(JenkinsfileFileType.INSTANCE,
                "pipeline { stages { stage('Release') { steps { cmd '''echo start\necho done'''; powershell '''Write-Output start\nWrite-Output done''' } } } }");

        format();

        String formatted = myFixture.getFile().getText();
        assertTrue(formatted, formatted.contains("cmd '''\n"));
        assertTrue(formatted, formatted.contains("echo start\n"));
        assertTrue(formatted, formatted.contains("echo done"));
        assertTrue(formatted, formatted.contains("powershell '''\n"));
        assertTrue(formatted, formatted.contains("Write-Output start\n"));
        assertTrue(formatted, formatted.contains("Write-Output done"));
        assertIdempotent();
    }

    public void testCmdSemicolonsAndConcatenatedScriptLiteralsArePreserved() {
        myFixture.configureByText(JenkinsfileFileType.INSTANCE,
                "pipeline { stages { stage('X') { steps { bat 'echo start; echo done'; powershell 'Write-Output start; Write-Output done'; sh 'echo first; echo second' + suffix } } } }");

        format();

        String formatted = myFixture.getFile().getText();
        assertTrue(formatted, formatted.contains("bat '''\n"));
        assertTrue(formatted, formatted.contains("echo start; echo done"));
        assertTrue(formatted, formatted.contains("powershell '''\n"));
        assertTrue(formatted, formatted.contains("Write-Output start;\n"));
        assertTrue(formatted, formatted.contains("Write-Output done"));
        assertTrue(formatted, formatted.contains("sh 'echo first; echo second' + suffix"));
        assertIdempotent();
    }

    public void testStringsAndOperatorsRemainIntact() {
        myFixture.configureByText(JenkinsfileFileType.INSTANCE,
                "pipeline { stages { stage('X') { steps { script { def x = -1; x++; def s = '''line one\n  line two'''; def r = /a b/; echo \"${x}\" } } } } }");
        String before = myFixture.getFile().getText();
        format();
        assertEquals(tokens(before), tokens(myFixture.getFile().getText()));
        assertIdempotent();
    }

    public void testShellKeywordsAsArgumentsAreNotControlFlow() {
        myFixture.configureByText(JenkinsfileFileType.INSTANCE,
                "pipeline { stages { stage('X') { steps { sh 'echo done; echo if then'; sh 'if true; then echo done; fi' } } } }");
        format();
        String formatted = myFixture.getFile().getText();
        assertTrue(formatted, formatted.contains("echo done\n"));
        assertTrue(formatted, formatted.contains("echo if then\n"));
        assertTrue(formatted, formatted.contains("sh 'if true; then echo done; fi'"));
        assertIdempotent();
    }

    public void testMixedCaseFilename() throws java.io.IOException {
        var file = myFixture.getTempDirFixture().createFile("release-jEnKiNsFiLe.backup", "pipeline {}");
        assertEquals(JenkinsfileFileType.INSTANCE, file.getFileType());
    }

    private void format() {
        WriteCommandAction.runWriteCommandAction(getProject(), (Runnable) () ->
                CodeStyleManager.getInstance(getProject()).reformat(myFixture.getFile()));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }

    private void assertIdempotent() {
        String first = myFixture.getFile().getText();
        for (int i = 0; i < 4; i++) {
            format();
            assertEquals("Formatting pass " + (i + 2), first, myFixture.getFile().getText());
        }
    }

    private static java.util.List<String> tokens(String text) {
        var lexer = new com.neko233.jenkinsfile.language.JenkinsfileLexer();
        var result = new java.util.ArrayList<String>();
        lexer.start(text);
        while (lexer.getTokenType() != null) {
            if (lexer.getTokenType() != com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.WHITE_SPACE) {
                result.add(text.substring(lexer.getTokenStart(), lexer.getTokenEnd()));
            }
            lexer.advance();
        }
        return result;
    }
}

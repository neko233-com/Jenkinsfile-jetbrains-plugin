package com.neko233.jenkinsfile.highlighting;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import com.neko233.jenkinsfile.language.JenkinsfileFileType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Formats safe embedded POSIX shell literals after the Jenkinsfile formatter runs. */
public final class JenkinsfileShellPostFormatProcessor implements PostFormatProcessor {
    @Override
    public @NotNull PsiElement processElement(@NotNull PsiElement element,
                                              @NotNull CodeStyleSettings settings) {
        if (element instanceof PsiFile file) processText(file, new TextRange(0, file.getTextLength()), settings);
        return element;
    }

    @Override
    public @NotNull TextRange processText(@NotNull PsiFile file, @NotNull TextRange rangeToFormat,
                                          @NotNull CodeStyleSettings settings) {
        if (file.getFileType() != JenkinsfileFileType.INSTANCE) return rangeToFormat;

        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null || document.getTextLength() > 250_000) return rangeToFormat;

        String source = document.getText();
        List<JenkinsfileEmbeddedScriptAnnotator.ScriptLiteral> scripts =
                JenkinsfileEmbeddedScriptAnnotator.findScriptLiterals(source);
        int delta = 0;
        for (int i = scripts.size() - 1; i >= 0; i--) {
            JenkinsfileEmbeddedScriptAnnotator.ScriptLiteral script = scripts.get(i);
            if (script.literalStart() < rangeToFormat.getStartOffset()
                    || script.literalEnd() > rangeToFormat.getEndOffset()
                    || !isStandaloneLiteral(source, script)) continue;

            String replacement = EmbeddedShellFormatter.formatLiteral(source, script);
            if (replacement == null) continue;

            document.replaceString(script.literalStart(), script.literalEnd(), replacement);
            delta += replacement.length() - (script.literalEnd() - script.literalStart());
        }

        if (delta == 0) return rangeToFormat;
        int newEnd = Math.min(document.getTextLength(), Math.max(rangeToFormat.getStartOffset(), rangeToFormat.getEndOffset() + delta));
        return new TextRange(rangeToFormat.getStartOffset(), newEnd);
    }

    private static boolean isStandaloneLiteral(String source,
                                               JenkinsfileEmbeddedScriptAnnotator.ScriptLiteral script) {
        int offset = script.literalEnd();
        while (offset < source.length() && (source.charAt(offset) == ' ' || source.charAt(offset) == '\t')) offset++;
        if (offset == source.length()) return true;
        char next = source.charAt(offset);
        if (next == '\n' || next == '\r' || next == ';' || next == ',' || next == ')' || next == '}' || next == ']') return true;
        return next == '/' && offset + 1 < source.length()
                && (source.charAt(offset + 1) == '/' || source.charAt(offset + 1) == '*');
    }
}

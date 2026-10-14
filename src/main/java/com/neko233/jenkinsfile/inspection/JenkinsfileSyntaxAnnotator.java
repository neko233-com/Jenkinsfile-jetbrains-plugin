package com.neko233.jenkinsfile.inspection;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.jetbrains.annotations.NotNull;

public final class JenkinsfileSyntaxAnnotator implements Annotator {
    private static final int MAX_FILE_LENGTH = 250_000;

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiFile file) || !file.getLanguage().getID().equals("Jenkinsfile")) return;
        String text = file.getText();
        if (text.length() > MAX_FILE_LENGTH) return;

        CompilationUnit unit = new CompilationUnit(new CompilerConfiguration());
        unit.addSource(file.getName(), text);
        try {
            unit.compile(Phases.CONVERSION);
        } catch (Exception ignored) {
            // Syntax diagnostics are read from the compiler's error collector below.
        }

        var errors = unit.getErrorCollector().getErrors();
        if (errors == null) return;
        for (Object item : errors) {
            if (!(item instanceof SyntaxErrorMessage syntaxMessage)) continue;
            SyntaxException syntax = syntaxMessage.getCause();
            if (syntax == null) continue;
            int start = offsetAt(text, syntax.getStartLine(), syntax.getStartColumn());
            int end = offsetAt(text, syntax.getEndLine(), syntax.getEndColumn());
            start = Math.min(Math.max(0, start), text.length());
            if (start >= text.length()) continue;
            end = Math.min(Math.max(start + 1, end), text.length());
            String description = syntax.getOriginalMessage();
            holder.newAnnotation(HighlightSeverity.ERROR, description)
                    .range(TextRange.create(start, end))
                    .create();
        }
    }

    private static int offsetAt(String text, int line, int column) {
        int offset = 0;
        for (int current = 1; current < line && offset < text.length(); current++) {
            int newline = text.indexOf('\n', offset);
            if (newline < 0) return text.length();
            offset = newline + 1;
        }
        return Math.min(text.length(), offset + Math.max(0, column - 1));
    }
}

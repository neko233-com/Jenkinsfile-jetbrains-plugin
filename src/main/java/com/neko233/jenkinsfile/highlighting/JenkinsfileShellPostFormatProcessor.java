package com.neko233.jenkinsfile.highlighting;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import com.intellij.psi.tree.IElementType;
import com.neko233.jenkinsfile.language.JenkinsfileFileType;
import com.neko233.jenkinsfile.language.JenkinsfileLexer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.*;

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

        int delta = indentStructure(file, document, rangeToFormat, settings);
        String source = document.getText();
        List<JenkinsfileEmbeddedScriptAnnotator.ScriptLiteral> scripts =
                JenkinsfileEmbeddedScriptAnnotator.findScriptLiterals(source);
        int formattedRangeEnd = rangeToFormat.getEndOffset() + delta;
        for (int i = scripts.size() - 1; i >= 0; i--) {
            JenkinsfileEmbeddedScriptAnnotator.ScriptLiteral script = scripts.get(i);
            if (script.literalStart() < rangeToFormat.getStartOffset()
                    || script.literalEnd() > formattedRangeEnd
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

    /**
     * Use delimiter structure for every code line, so one misplaced first child cannot become the
     * indentation anchor for its siblings. Strings and comments are atomic lexer tokens: only
     * whitespace before a token changes, never the contents of a multiline literal or comment.
     */
    private static int indentStructure(PsiFile file, Document document, TextRange range,
                                           CodeStyleSettings settings) {
        String source = document.getText();
        var options = settings.getIndentOptions(file.getFileType());
        int indentSize = Math.max(1, options.INDENT_SIZE);
        int tabSize = Math.max(1, options.TAB_SIZE);
        Map<Integer, IndentEdit> edits = new HashMap<>();
        Deque<DelimiterFrame> groups = new ArrayDeque<>();
        JenkinsfileLexer lexer = new JenkinsfileLexer();
        lexer.start(source, 0, source.length(), 0);

        while (lexer.getTokenType() != null) {
            IElementType type = lexer.getTokenType();
            int tokenStart = lexer.getTokenStart();
            if (type != WHITE_SPACE) {
                DelimiterFrame closing = null;
                if (isCloser(type) && !groups.isEmpty() && groups.peek().closer == type) {
                    closing = groups.pop();
                }
                int line = document.getLineNumber(tokenStart);
                int lineStart = document.getLineStartOffset(line);
                int expectedIndent = closing != null ? closing.openIndent
                        : groups.isEmpty() ? 0 : groups.peek().childIndent;
                if (lineStart >= range.getStartOffset() && tokenStart <= range.getEndOffset()
                        && isOnlyIndent(source, lineStart, tokenStart)) {
                    edits.put(lineStart, new IndentEdit(tokenStart, expectedIndent));
                }
                IElementType closer = closerFor(type);
                if (closer != null) {
                    Integer adjustedIndent = edits.containsKey(lineStart)
                            ? edits.get(lineStart).columns
                            : null;
                    int openIndent = adjustedIndent != null
                            ? adjustedIndent
                            : countIndentColumns(source, lineStart, tokenStart, tabSize);
                    groups.push(new DelimiterFrame(closer, openIndent, openIndent + indentSize));
                }
            }
            lexer.advance();
        }

        if (edits.isEmpty()) return 0;

        List<Map.Entry<Integer, IndentEdit>> ordered = new ArrayList<>(edits.entrySet());
        ordered.sort((left, right) -> Integer.compare(right.getKey(), left.getKey()));
        int delta = 0;
        for (Map.Entry<Integer, IndentEdit> entry : ordered) {
            int lineStart = entry.getKey();
            IndentEdit edit = entry.getValue();
            String replacement = indentation(edit.columns, tabSize, options.USE_TAB_CHARACTER);
            int oldLength = edit.tokenStart - lineStart;
            if (oldLength == replacement.length()
                    && source.regionMatches(lineStart, replacement, 0, replacement.length())) continue;
            document.replaceString(lineStart, edit.tokenStart, replacement);
            delta += replacement.length() - oldLength;
        }
        return delta;
    }

    private static boolean isOnlyIndent(String source, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = source.charAt(i);
            if (c != ' ' && c != '\t') return false;
        }
        return true;
    }

    private static int countIndentColumns(String source, int start, int end, int tabSize) {
        int columns = 0;
        for (int i = start; i < end; i++) {
            char c = source.charAt(i);
            if (c == ' ') columns++;
            else if (c == '\t') columns += tabSize - columns % tabSize;
            else break;
        }
        return columns;
    }

    private static String indentation(int columns, int tabSize, boolean useTabs) {
        if (!useTabs) return " ".repeat(Math.max(0, columns));
        int tabs = Math.max(0, columns / tabSize);
        int spaces = Math.max(0, columns % tabSize);
        return "\t".repeat(tabs) + " ".repeat(spaces);
    }

    private static IElementType closerFor(IElementType type) {
        if (type == LEFT_BRACE) return RIGHT_BRACE;
        if (type == LEFT_PAREN) return RIGHT_PAREN;
        if (type == LEFT_BRACKET) return RIGHT_BRACKET;
        return null;
    }

    private static boolean isCloser(IElementType type) {
        return type == RIGHT_BRACE || type == RIGHT_PAREN || type == RIGHT_BRACKET;
    }

    private static final class DelimiterFrame {
        private final IElementType closer;
        private final int openIndent;
        private final int childIndent;

        private DelimiterFrame(IElementType closer, int openIndent, int childIndent) {
            this.closer = closer;
            this.openIndent = openIndent;
            this.childIndent = childIndent;
        }
    }

    private record IndentEdit(int tokenStart, int columns) {
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

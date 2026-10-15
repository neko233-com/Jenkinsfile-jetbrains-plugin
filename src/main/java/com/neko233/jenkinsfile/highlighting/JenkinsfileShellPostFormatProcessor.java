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

        int delta = indentFirstChildren(file, document, rangeToFormat, settings);
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
     * IntelliJ's spacing formatter can leave the first leaf inside a delimiter group at column zero.
     * Restore just that first line from the opener's indentation; later siblings remain governed by
     * the regular formatting model. The Jenkinsfile lexer treats strings and comments atomically,
     * so their contents are never reindented here.
     */
    private static int indentFirstChildren(PsiFile file, Document document, TextRange range,
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
            if (isCloser(type)) {
                if (!groups.isEmpty() && groups.peek().closer == type) groups.pop();
            } else if (type != WHITE_SPACE) {
                if (!groups.isEmpty()) {
                    DelimiterFrame parent = groups.peek();
                    if (!parent.hasContent) {
                        parent.hasContent = true;
                        int line = document.getLineNumber(tokenStart);
                        int lineStart = document.getLineStartOffset(line);
                        if (lineStart >= range.getStartOffset() && tokenStart <= range.getEndOffset()
                                && line > parent.openLine && isOnlyIndent(source, lineStart, tokenStart)) {
                            edits.put(lineStart, new IndentEdit(tokenStart, parent.childIndent));
                        }
                    }
                }

                IElementType closer = closerFor(type);
                if (closer != null) {
                    int line = document.getLineNumber(tokenStart);
                    int lineStart = document.getLineStartOffset(line);
                    Integer adjustedIndent = edits.containsKey(lineStart)
                            ? edits.get(lineStart).columns
                            : null;
                    int openIndent = adjustedIndent != null
                            ? adjustedIndent
                            : countIndentColumns(source, lineStart, tokenStart, tabSize);
                    groups.push(new DelimiterFrame(closer, line,
                            openIndent + indentSize));
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
            String replacement = indentation(edit.columns, indentSize, options.USE_TAB_CHARACTER);
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

    private static String indentation(int columns, int indentSize, boolean useTabs) {
        if (!useTabs) return " ".repeat(Math.max(0, columns));
        int tabs = Math.max(0, columns / indentSize);
        int spaces = Math.max(0, columns % indentSize);
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
        private final int openLine;
        private final int childIndent;
        private boolean hasContent;

        private DelimiterFrame(IElementType closer, int openLine, int childIndent) {
            this.closer = closer;
            this.openLine = openLine;
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

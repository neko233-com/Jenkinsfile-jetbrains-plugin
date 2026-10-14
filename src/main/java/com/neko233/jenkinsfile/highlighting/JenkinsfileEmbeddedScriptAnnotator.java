package com.neko233.jenkinsfile.highlighting;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.neko233.jenkinsfile.language.JenkinsfileLexer;
import com.neko233.jenkinsfile.language.JenkinsfileTokenTypes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.*;

/** Adds shell-family syntax colors only to literal arguments of Jenkins script steps. */
public final class JenkinsfileEmbeddedScriptAnnotator implements Annotator {
    private static final int MAX_FILE_LENGTH = 250_000;
    public static final TextAttributesKey COMMAND = key("JENKINSFILE_SCRIPT_COMMAND", DefaultLanguageHighlighterColors.FUNCTION_CALL);
    public static final TextAttributesKey KEYWORD = key("JENKINSFILE_SCRIPT_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey VARIABLE = key("JENKINSFILE_SCRIPT_VARIABLE", DefaultLanguageHighlighterColors.CONSTANT);
    public static final TextAttributesKey PARAMETER = key("JENKINSFILE_SCRIPT_PARAMETER", DefaultLanguageHighlighterColors.METADATA);
    public static final TextAttributesKey STRING = key("JENKINSFILE_SCRIPT_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey COMMENT = key("JENKINSFILE_SCRIPT_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey OPERATOR = key("JENKINSFILE_SCRIPT_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    public static final TextAttributesKey NUMBER = key("JENKINSFILE_SCRIPT_NUMBER", DefaultLanguageHighlighterColors.NUMBER);

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiFile file) || !"Jenkinsfile".equals(file.getLanguage().getID())) return;
        String text = file.getText();
        if (text.length() > MAX_FILE_LENGTH) return;

        for (ScriptLiteral literal : findScriptLiterals(text)) {
            for (EmbeddedScriptLexer.Segment segment : EmbeddedScriptLexer.lex(
                    literal.text(), literal.mode(), literal.startOffset())) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(new TextRange(segment.start(), segment.end()))
                        .textAttributes(attributesFor(segment.kind()))
                        .create();
            }
        }
    }

    static @NotNull List<ScriptLiteral> findScriptLiterals(@NotNull String source) {
        List<LexedToken> tokens = lex(source);
        int[] matchingParens = matchingParens(tokens);
        List<ScriptLiteral> result = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            LexedToken step = tokens.get(i);
            if (step.type() != METHOD_CALL) continue;
            EmbeddedScriptLexer.Mode mode = modeFor(step.text());
            if (mode == null) continue;

            int first = nextNonWhitespace(tokens, i + 1);
            if (first < 0) continue;
            LexedToken next = tokens.get(first);
            LexedToken literal = next.type() == JenkinsfileTokenTypes.STRING ? next
                    : next.type() == LEFT_PAREN ? findCallScriptArgument(tokens, first, matchingParens[first]) : null;
            if (literal == null) continue;

            LiteralBody body = literalBody(source, literal);
            if (body != null && body.endOffset() > body.startOffset()) {
                result.add(new ScriptLiteral(
                        source.substring(body.startOffset(), body.endOffset()), body.startOffset(), mode,
                        literal.start(), literal.end(), step.start(), body.quote(), body.delimiter()));
            }
        }
        return List.copyOf(result);
    }

    private static LexedToken findCallScriptArgument(List<LexedToken> tokens, int openIndex, int closeIndex) {
        if (closeIndex < 0) return null;

        int parens = 1;
        int brackets = 0;
        int braces = 0;
        boolean hasNamedArguments = false;
        LexedToken firstPositionalString = null;
        for (int i = openIndex + 1; i < closeIndex; i++) {
            LexedToken token = tokens.get(i);
            boolean topLevelArgument = parens == 1 && brackets == 0 && braces == 0;
            if (topLevelArgument && token.type() == NAMED_ARGUMENT) {
                hasNamedArguments = true;
                if ("script".equalsIgnoreCase(token.text())) {
                    int colon = nextNonWhitespace(tokens, i + 1);
                    int value = colon < 0 ? -1 : nextNonWhitespace(tokens, colon + 1);
                    if (colon >= 0 && tokens.get(colon).type() == COLON
                            && value >= 0 && tokens.get(value).type() == JenkinsfileTokenTypes.STRING) {
                        return tokens.get(value);
                    }
                }
            } else if (topLevelArgument && token.type() == JenkinsfileTokenTypes.STRING && firstPositionalString == null) {
                firstPositionalString = token;
            }

            if (token.type() == LEFT_PAREN) parens++;
            else if (token.type() == RIGHT_PAREN) parens--;
            else if (token.type() == LEFT_BRACKET) brackets++;
            else if (token.type() == RIGHT_BRACKET) brackets--;
            else if (token.type() == LEFT_BRACE) braces++;
            else if (token.type() == RIGHT_BRACE) braces--;
        }
        return hasNamedArguments ? null : firstPositionalString;
    }

    private static int[] matchingParens(List<LexedToken> tokens) {
        int[] matching = new int[tokens.size()];
        Arrays.fill(matching, -1);
        ArrayDeque<Integer> opens = new ArrayDeque<>();
        for (int i = 0; i < tokens.size(); i++) {
            IElementType type = tokens.get(i).type();
            if (type == LEFT_PAREN) opens.push(i);
            else if (type == RIGHT_PAREN && !opens.isEmpty()) matching[opens.pop()] = i;
        }
        return matching;
    }

    private static LiteralBody literalBody(String source, LexedToken token) {
        int start = token.start();
        int end = token.end();
        if (start < 0 || end > source.length() || start >= end) return null;

        if (source.startsWith("$/", start)) {
            int bodyStart = Math.min(end, start + 2);
            int bodyEnd = source.startsWith("/$", Math.max(bodyStart, end - 2)) ? end - 2 : end;
            return new LiteralBody(bodyStart, Math.max(bodyStart, bodyEnd), '$', 2);
        }
        char quote = source.charAt(start);
        if (quote != '\'' && quote != '"' && quote != '`') return new LiteralBody(start, end, '\0', 0);
        boolean triple = start + 2 < end && source.charAt(start + 1) == quote && source.charAt(start + 2) == quote;
        int delimiter = triple ? 3 : 1;
        int bodyStart = Math.min(end, start + delimiter);
        boolean closed = end - start >= delimiter * 2;
        if (closed) {
            for (int i = 0; i < delimiter; i++) {
                if (source.charAt(end - delimiter + i) != quote) {
                    closed = false;
                    break;
                }
            }
        }
        int bodyEnd = closed ? end - delimiter : end;
        return new LiteralBody(bodyStart, Math.max(bodyStart, bodyEnd), quote, delimiter);
    }

    private static List<LexedToken> lex(String source) {
        JenkinsfileLexer lexer = new JenkinsfileLexer();
        lexer.start(source, 0, source.length(), 0);
        List<LexedToken> tokens = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            IElementType type = lexer.getTokenType();
            int start = lexer.getTokenStart();
            int end = lexer.getTokenEnd();
            tokens.add(new LexedToken(type, start, end, source.substring(start, end)));
            lexer.advance();
        }
        return tokens;
    }

    private static int nextNonWhitespace(List<LexedToken> tokens, int start) {
        for (int i = start; i < tokens.size(); i++) if (tokens.get(i).type() != WHITE_SPACE) return i;
        return -1;
    }

    private static EmbeddedScriptLexer.Mode modeFor(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "sh" -> EmbeddedScriptLexer.Mode.POSIX;
            case "bat", "cmd" -> EmbeddedScriptLexer.Mode.CMD;
            case "powershell", "pwsh" -> EmbeddedScriptLexer.Mode.POWERSHELL;
            default -> null;
        };
    }

    private static TextAttributesKey attributesFor(EmbeddedScriptLexer.Kind kind) {
        return switch (kind) {
            case COMMAND -> COMMAND;
            case KEYWORD -> KEYWORD;
            case VARIABLE -> VARIABLE;
            case PARAMETER -> PARAMETER;
            case STRING -> STRING;
            case COMMENT -> COMMENT;
            case OPERATOR -> OPERATOR;
            case NUMBER -> NUMBER;
        };
    }

    private static TextAttributesKey key(String externalName, TextAttributesKey fallback) {
        return TextAttributesKey.createTextAttributesKey(externalName, fallback);
    }

    record ScriptLiteral(String text, int startOffset, EmbeddedScriptLexer.Mode mode,
                         int literalStart, int literalEnd, int stepStart, char quote, int delimiter) { }
    private record LexedToken(IElementType type, int start, int end, String text) { }
    private record LiteralBody(int startOffset, int endOffset, char quote, int delimiter) { }
}

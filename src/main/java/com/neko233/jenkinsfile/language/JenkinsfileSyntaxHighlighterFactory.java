package com.neko233.jenkinsfile.language;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.*;

public final class JenkinsfileSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
    private static final TextAttributesKey[] KEYWORD_ATTRS = keys("JENKINSFILE_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    private static final TextAttributesKey[] STRING_ATTRS = keys("JENKINSFILE_STRING", DefaultLanguageHighlighterColors.STRING);
    private static final TextAttributesKey[] COMMENT_ATTRS = keys("JENKINSFILE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    private static final TextAttributesKey[] NUMBER_ATTRS = keys("JENKINSFILE_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    private static final TextAttributesKey[] OPERATOR_ATTRS = keys("JENKINSFILE_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    private static final TextAttributesKey[] IDENTIFIER_ATTRS = keys("JENKINSFILE_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);
    private static final TextAttributesKey[] METHOD_CALL_ATTRS = keys("JENKINSFILE_METHOD_CALL", DefaultLanguageHighlighterColors.FUNCTION_CALL);
    private static final TextAttributesKey[] NAMED_ARGUMENT_ATTRS = keys("JENKINSFILE_NAMED_ARGUMENT", DefaultLanguageHighlighterColors.PARAMETER);
    private static final TextAttributesKey[] CONSTANT_ATTRS = keys("JENKINSFILE_CONSTANT", DefaultLanguageHighlighterColors.CONSTANT);
    private static final TextAttributesKey[] PUNCTUATION_ATTRS = keys("JENKINSFILE_PUNCTUATION", DefaultLanguageHighlighterColors.COMMA);
    private static final TextAttributesKey[] BRACES_ATTRS = keys("JENKINSFILE_BRACES", DefaultLanguageHighlighterColors.BRACES);
    private static final TextAttributesKey[] BAD_ATTRS = keys("JENKINSFILE_BAD_CHARACTER", DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE);

    @Override
    public @NotNull SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile) {
        return new SyntaxHighlighterBase() {
            @Override
            public @NotNull com.intellij.lexer.Lexer getHighlightingLexer() {
                return new JenkinsfileLexer();
            }

            @Override
            public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
                if (tokenType == KEYWORD || tokenType == PIPELINE_KEYWORD) return KEYWORD_ATTRS;
                if (tokenType == STRING || tokenType == REGEX) return STRING_ATTRS;
                if (tokenType == LINE_COMMENT || tokenType == BLOCK_COMMENT) return COMMENT_ATTRS;
                if (tokenType == NUMBER) return NUMBER_ATTRS;
                if (tokenType == OPERATOR || tokenType == AT) return OPERATOR_ATTRS;
                if (tokenType == METHOD_CALL) return METHOD_CALL_ATTRS;
                if (tokenType == NAMED_ARGUMENT) return NAMED_ARGUMENT_ATTRS;
                if (tokenType == CONSTANT) return CONSTANT_ATTRS;
                if (tokenType == IDENTIFIER) return IDENTIFIER_ATTRS;
                if (tokenType == LEFT_BRACE || tokenType == RIGHT_BRACE || tokenType == LEFT_PAREN
                        || tokenType == RIGHT_PAREN || tokenType == LEFT_BRACKET || tokenType == RIGHT_BRACKET) return BRACES_ATTRS;
                if (tokenType == COMMA || tokenType == COLON || tokenType == SEMICOLON || tokenType == DOT) return PUNCTUATION_ATTRS;
                if (tokenType == BAD_CHARACTER) return BAD_ATTRS;
                return TextAttributesKey.EMPTY_ARRAY;
            }
        };
    }

    private static TextAttributesKey[] keys(String name, TextAttributesKey fallback) {
        return new TextAttributesKey[]{TextAttributesKey.createTextAttributesKey(name, fallback)};
    }
}

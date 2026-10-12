package com.neko233.jenkinsfile.language;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class JenkinsfileTokenTypes {
    public static final IElementType WHITE_SPACE = type("WHITE_SPACE");
    public static final IElementType IDENTIFIER = type("IDENTIFIER");
    public static final IElementType KEYWORD = type("KEYWORD");
    public static final IElementType PIPELINE_KEYWORD = type("PIPELINE_KEYWORD");
    public static final IElementType NUMBER = type("NUMBER");
    public static final IElementType STRING = type("STRING");
    public static final IElementType REGEX = type("REGEX");
    public static final IElementType LINE_COMMENT = type("LINE_COMMENT");
    public static final IElementType BLOCK_COMMENT = type("BLOCK_COMMENT");
    public static final IElementType OPERATOR = type("OPERATOR");
    public static final IElementType LEFT_BRACE = type("LEFT_BRACE");
    public static final IElementType RIGHT_BRACE = type("RIGHT_BRACE");
    public static final IElementType LEFT_PAREN = type("LEFT_PAREN");
    public static final IElementType RIGHT_PAREN = type("RIGHT_PAREN");
    public static final IElementType LEFT_BRACKET = type("LEFT_BRACKET");
    public static final IElementType RIGHT_BRACKET = type("RIGHT_BRACKET");
    public static final IElementType COMMA = type("COMMA");
    public static final IElementType COLON = type("COLON");
    public static final IElementType SEMICOLON = type("SEMICOLON");
    public static final IElementType DOT = type("DOT");
    public static final IElementType AT = type("AT");
    public static final IElementType BAD_CHARACTER = type("BAD_CHARACTER");

    public static final IElementType CURLY_GROUP = type("CURLY_GROUP");
    public static final IElementType PAREN_GROUP = type("PAREN_GROUP");
    public static final IElementType BRACKET_GROUP = type("BRACKET_GROUP");

    public static final TokenSet WHITESPACES = TokenSet.create(WHITE_SPACE);
    public static final TokenSet COMMENTS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT);
    public static final TokenSet STRINGS = TokenSet.create(STRING, REGEX);

    private JenkinsfileTokenTypes() {
    }

    private static IElementType type(@NonNls @NotNull String name) {
        return new IElementType(name, JenkinsfileLanguage.INSTANCE);
    }
}

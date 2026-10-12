package com.neko233.jenkinsfile.language;

import com.intellij.lang.ASTNode;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

import static com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.*;

public final class JenkinsfileParserDefinition implements ParserDefinition {
    public static final IFileElementType FILE = new IFileElementType(JenkinsfileLanguage.INSTANCE);

    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new JenkinsfileLexer();
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return (root, builder) -> {
            PsiBuilder.Marker file = builder.mark();
            parseSequence(builder, null);
            file.done(FILE);
            return builder.getTreeBuilt();
        };
    }

    private static void parseSequence(PsiBuilder builder, IElementType closingType) {
        while (!builder.eof()) {
            IElementType current = builder.getTokenType();
            if (current == closingType) {
                builder.advanceLexer();
                return;
            }

            IElementType closing = closingFor(current);
            if (closing != null) {
                IElementType groupType = current == LEFT_BRACE ? CURLY_GROUP
                        : current == LEFT_PAREN ? PAREN_GROUP : BRACKET_GROUP;
                PsiBuilder.Marker group = builder.mark();
                builder.advanceLexer();
                parseSequence(builder, closing);
                group.done(groupType);
            } else {
                builder.advanceLexer();
            }
        }
    }

    private static IElementType closingFor(IElementType opener) {
        if (opener == LEFT_BRACE) return RIGHT_BRACE;
        if (opener == LEFT_PAREN) return RIGHT_PAREN;
        if (opener == LEFT_BRACKET) return RIGHT_BRACKET;
        return null;
    }

    @Override
    public @NotNull TokenSet getWhitespaceTokens() {
        return WHITESPACES;
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return COMMENTS;
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return STRINGS;
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        return new ASTWrapperPsiElement(node);
    }

    @Override
    public @NotNull PsiFile createFile(FileViewProvider viewProvider) {
        return new PsiFileBase(viewProvider, JenkinsfileLanguage.INSTANCE) {
            @Override
            public @NotNull FileType getFileType() {
                return JenkinsfileFileType.INSTANCE;
            }
        };
    }

    @Override
    public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MAY;
    }

}

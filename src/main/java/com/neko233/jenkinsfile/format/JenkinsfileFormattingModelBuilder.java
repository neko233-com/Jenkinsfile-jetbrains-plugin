package com.neko233.jenkinsfile.format;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.*;

public final class JenkinsfileFormattingModelBuilder implements FormattingModelBuilder {
    @Override
    public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
        ASTNode node = formattingContext.getNode();
        JenkinsfileBlock root = new JenkinsfileBlock(node, null, true);
        return FormattingModelProvider.createFormattingModelForPsiFile(
                formattingContext.getContainingFile(), root, formattingContext.getCodeStyleSettings());
    }

    private static final class JenkinsfileBlock extends AbstractBlock {
        private final boolean root;
        private List<Block> subBlocks;

        private JenkinsfileBlock(ASTNode node, @Nullable Wrap wrap, boolean root) {
            super(node, wrap, null);
            this.root = root;
        }

        @Override
        protected List<Block> buildChildren() {
            List<Block> result = new ArrayList<>();
            for (ASTNode child = myNode.getFirstChildNode(); child != null; child = child.getTreeNext()) {
                if (child.getElementType() == WHITE_SPACE) continue;
                result.add(new JenkinsfileBlock(child, null, false));
            }
            return result;
        }

        @Override
        public @NotNull List<Block> getSubBlocks() {
            if (subBlocks == null) subBlocks = buildChildren();
            return subBlocks;
        }

        @Override
        public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
            if (child1 == null || !(child1 instanceof JenkinsfileBlock left)
                    || !(child2 instanceof JenkinsfileBlock right)) return null;

            IElementType before = left.myNode.getElementType();
            IElementType after = right.myNode.getElementType();
            String beforeText = left.myNode.getText();
            String afterText = right.myNode.getText();

            if (after == CURLY_GROUP) return spacing(1, 0);
            if (after == PAREN_GROUP) return spacing(isGroovyControlKeyword(beforeText) ? 1 : 0, 0);
            if (after == BRACKET_GROUP) {
                return spacing(before == IDENTIFIER || before == PAREN_GROUP || before == BRACKET_GROUP ? 0 : 1, 0);
            }
            if (before == PAREN_GROUP || before == BRACKET_GROUP) {
                return spacing(after == DOT || after == BRACKET_GROUP || after == PAREN_GROUP ? 0 : 1, 0);
            }
            if (before == CURLY_GROUP) return spacing(1, 0);
            if (before == LINE_COMMENT) return spacing(0, 1);
            if (after == LINE_COMMENT || after == BLOCK_COMMENT) return spacing(1, 0);
            if (before == LEFT_BRACE) return spacing(0, 1);
            if (after == RIGHT_BRACE) return spacing(0, before == LEFT_BRACE ? 0 : 1);
            if (before == SEMICOLON) return spacing(0, 1);
            if (after == COMMA || after == COLON || after == SEMICOLON) return spacing(0, 0);
            if (before == COMMA || before == COLON) return spacing(1, 0);
            if (before == DOT || after == DOT) return spacing(0, 0);
            if (after == LEFT_PAREN) {
                return spacing(isGroovyControlKeyword(beforeText) ? 1 : 0, 0);
            }
            if (after == RIGHT_PAREN || after == RIGHT_BRACKET || before == LEFT_PAREN || before == LEFT_BRACKET) {
                return spacing(0, 0);
            }
            if (after == LEFT_BRACE) return spacing(1, 0);
            if (before == OPERATOR || after == OPERATOR) return spacing(1, 0);
            if (before == AT || after == AT) return spacing(0, 0);
            if (before == BLOCK_COMMENT) return spacing(1, 0);
            return spacing(1, 0);
        }

        private static Spacing spacing(int spaces, int lineFeeds) {
            return Spacing.createSpacing(spaces, spaces, lineFeeds, true, 2);
        }

        @Override
        public @Nullable Indent getIndent() {
            if (root) return Indent.getNoneIndent();
            ASTNode parent = myNode.getTreeParent();
            if (parent == null) return Indent.getNoneIndent();
            IElementType parentType = parent.getElementType();
            IElementType type = myNode.getElementType();
            if (parentType == CURLY_GROUP) {
                if (type == LEFT_BRACE || type == RIGHT_BRACE) return Indent.getNoneIndent();
                return Indent.getNormalIndent();
            }
            if (parentType == PAREN_GROUP || parentType == BRACKET_GROUP) {
                if (type == LEFT_PAREN || type == RIGHT_PAREN || type == LEFT_BRACKET || type == RIGHT_BRACKET) {
                    return Indent.getNoneIndent();
                }
                return Indent.getContinuationIndent();
            }
            return Indent.getNoneIndent();
        }

        @Override
        public @Nullable Wrap getWrap() {
            return null;
        }

        @Override
        public @Nullable Alignment getAlignment() {
            return null;
        }

        @Override
        public boolean isLeaf() {
            return myNode.getFirstChildNode() == null;
        }

        @Override
        public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
            IElementType type = myNode.getElementType();
            return new ChildAttributes(type == CURLY_GROUP ? Indent.getNormalIndent() : Indent.getNoneIndent(), null);
        }

        @Override
        public boolean isIncomplete() {
            return false;
        }

        @Override
        public @NotNull TextRange getTextRange() {
            return myNode.getTextRange();
        }

        private static boolean isGroovyControlKeyword(String text) {
            return switch (text) {
                case "if", "for", "while", "switch", "catch", "synchronized", "try", "assert" -> true;
                default -> false;
            };
        }
    }
}

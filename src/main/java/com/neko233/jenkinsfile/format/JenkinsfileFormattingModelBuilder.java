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

            return SpacingRules.between(myNode, left.myNode, right.myNode);
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
                return Indent.getIndent(Indent.Type.NORMAL, false, true);
            }
            if (parentType == PAREN_GROUP || parentType == BRACKET_GROUP) {
                if (type == LEFT_PAREN || type == RIGHT_PAREN || type == LEFT_BRACKET || type == RIGHT_BRACKET) {
                    return Indent.getNoneIndent();
                }
                return Indent.getIndent(Indent.Type.NORMAL, false, true);
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
            return new ChildAttributes(type == CURLY_GROUP || type == PAREN_GROUP || type == BRACKET_GROUP
                    ? Indent.getNormalIndent() : Indent.getNoneIndent(), null);
        }

        @Override
        public boolean isIncomplete() {
            IElementType type = myNode.getElementType();
            ASTNode last = myNode.getLastChildNode();
            if (type == CURLY_GROUP) return last == null || last.getElementType() != RIGHT_BRACE;
            if (type == PAREN_GROUP) return last == null || last.getElementType() != RIGHT_PAREN;
            if (type == BRACKET_GROUP) return last == null || last.getElementType() != RIGHT_BRACKET;
            return false;
        }

        @Override
        public @NotNull TextRange getTextRange() {
            return myNode.getTextRange();
        }

    }

    /**
     * Normalize whitespace without rewriting Groovy tokens or string contents. Layout decisions
     * use token structure, never the input's indentation/line width, to make repeat runs stable.
     */
    private static final class SpacingRules {
        private SpacingRules() {
        }

        private static Spacing between(ASTNode parent, ASTNode left, ASTNode right) {
            IElementType before = left.getElementType();
            IElementType after = right.getElementType();
            String beforeText = left.getText();

            // A trailing comment must stay attached to its statement, even after a semicolon.
            Spacing spacing = spacingAroundComments(before, after);
            if (spacing != null) return spacing;

            spacing = spacingAroundBraces(before, after);
            if (spacing != null) return spacing;

            if (isMultilineArguments(parent)) {
                if (before == LEFT_PAREN || before == LEFT_BRACKET || before == COMMA
                        || after == RIGHT_PAREN || after == RIGHT_BRACKET) return lineBreak();
            }

            if (isSectionBoundary(parent, left, right)) return lines(2);

            spacing = spacingAroundPunctuation(parent, before, after);
            if (spacing != null) return spacing;

            spacing = spacingAroundExpressions(left, right);
            if (spacing != null) return spacing;

            spacing = spacingAroundGroups(before, after, beforeText);
            return spacing != null ? spacing : spaces(1);
        }

        private static Spacing spacingAroundGroups(IElementType before, IElementType after, String beforeText) {
            if (after == CURLY_GROUP) return spaces(1);
            if (after == PAREN_GROUP) return spaces(isGroovyControlKeyword(beforeText) ? 1 : 0);
            if (after == BRACKET_GROUP) {
                boolean attached = before == IDENTIFIER
                        || before == CONSTANT || before == PAREN_GROUP || before == BRACKET_GROUP;
                return spaces(attached ? 0 : 1);
            }
            if (before == PAREN_GROUP || before == BRACKET_GROUP) {
                boolean attached = after == DOT || after == BRACKET_GROUP || after == PAREN_GROUP;
                return spaces(attached ? 0 : 1);
            }
            if (before == CURLY_GROUP) return spaces(1);
            return null;
        }

        private static Spacing spacingAroundComments(IElementType before, IElementType after) {
            if (before == LINE_COMMENT) return lineBreak();
            if (after == LINE_COMMENT || after == BLOCK_COMMENT) return spaces(1);
            if (before == BLOCK_COMMENT) return spaces(1);
            return null;
        }

        private static Spacing spacingAroundBraces(IElementType before, IElementType after) {
            if (after == RIGHT_BRACE) return before == LEFT_BRACE ? fixedSpaces(0) : lineBreak();
            if (before == LEFT_BRACE) return lineBreak();
            if (after == LEFT_BRACE) return spaces(1);
            return null;
        }

        private static Spacing spacingAroundPunctuation(ASTNode parent, IElementType before, IElementType after) {
            if (before == SEMICOLON) return parent.getElementType() == PAREN_GROUP ? fixedSpaces(1) : lineBreak();
            if (after == COMMA || after == COLON || after == SEMICOLON) return fixedSpaces(0);
            if (before == COMMA || before == COLON) return spaces(1);
            if (before == DOT || after == DOT) return spaces(0);
            return null;
        }

        private static Spacing spacingAroundExpressions(ASTNode left, ASTNode right) {
            IElementType before = left.getElementType();
            IElementType after = right.getElementType();
            String beforeText = left.getText();
            if (after == LEFT_PAREN) return spaces(isGroovyControlKeyword(beforeText) ? 1 : 0);
            if (after == RIGHT_PAREN || after == RIGHT_BRACKET || before == LEFT_PAREN || before == LEFT_BRACKET) {
                return spaces(0);
            }
            if (before == OPERATOR && after == OPERATOR) return fixedSpaces(1);
            if (isAttachedOperator(left) || isAttachedOperator(right)) return fixedSpaces(0);
            if (before == OPERATOR && (beforeText.equals("!") || beforeText.equals("~") || isUnarySign(left))) return fixedSpaces(0);
            if (before == OPERATOR || after == OPERATOR) return spaces(1);
            if (before == AT || after == AT) return spaces(0);
            return null;
        }

        private static Spacing spaces(int count) {
            return Spacing.createSpacing(count, count, 0, true, 0);
        }

        private static Spacing lineBreak() {
            return lines(1);
        }

        private static Spacing lines(int count) {
            return Spacing.createSpacing(0, 0, count, false, 0);
        }

        private static Spacing fixedSpaces(int count) {
            return Spacing.createSpacing(count, count, 0, false, 0);
        }

        private static boolean isMultilineArguments(ASTNode node) {
            IElementType type = node.getElementType();
            if (type != PAREN_GROUP && type != BRACKET_GROUP) return false;
            int fields = 0;
            int commas = 0;
            int width = 0;
            for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
                IElementType childType = child.getElementType();
                if (childType == NAMED_ARGUMENT) fields++;
                if (childType == COMMA) commas++;
                if (childType != WHITE_SPACE) width += stableWidth(child) + 1;
            }
            return fields >= 3 || (commas > 0 && width > 100);
        }

        private static int stableWidth(ASTNode node) {
            if (node.getElementType() == WHITE_SPACE) return 0;
            if (node.getFirstChildNode() == null) return node.getTextLength();
            int width = 0;
            for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
                if (child.getElementType() != WHITE_SPACE) width += stableWidth(child) + 1;
            }
            return width;
        }

        private static boolean isSectionBoundary(ASTNode parent, ASTNode left, ASTNode right) {
            if (parent.getElementType() != CURLY_GROUP || left.getElementType() == LEFT_BRACE) return false;
            String owner = groupName(parent);
            String next = right.getText();
            if (owner.equals("pipeline")) {
                return switch (next) {
                    case "agent", "options", "parameters", "environment", "stages", "post", "tools", "triggers" -> true;
                    default -> false;
                };
            }
            return (owner.equals("stages") || owner.equals("parallel"))
                    && left.getElementType() == CURLY_GROUP && next.equals("stage");
        }

        private static String groupName(ASTNode group) {
            ASTNode previous = previousCode(group);
            if (previous != null && previous.getElementType() == PAREN_GROUP) previous = previousCode(previous);
            return previous == null ? "" : previous.getText();
        }

        private static ASTNode previousCode(ASTNode node) {
            ASTNode previous = node.getTreePrev();
            while (previous != null && (previous.getElementType() == WHITE_SPACE
                    || previous.getElementType() == LINE_COMMENT || previous.getElementType() == BLOCK_COMMENT)) {
                previous = previous.getTreePrev();
            }
            return previous;
        }

        private static boolean isAttachedOperator(ASTNode node) {
            if (node.getElementType() != OPERATOR) return false;
            return switch (node.getText()) {
                case "?.", "*.", ".&", "::", "++", "--" -> true;
                default -> false;
            };
        }

        private static boolean isUnarySign(ASTNode node) {
            if (!node.getText().equals("-") && !node.getText().equals("+")) return false;
            ASTNode previous = previousCode(node);
            if (previous == null) return true;
            IElementType type = previous.getElementType();
            return type == OPERATOR || type == LEFT_PAREN || type == LEFT_BRACKET || type == LEFT_BRACE
                    || type == COMMA || type == COLON || type == SEMICOLON
                    || previous.getText().equals("return") || previous.getText().equals("throw");
        }

        private static boolean isGroovyControlKeyword(String text) {
            return switch (text) {
                case "if", "for", "while", "switch", "catch", "synchronized", "try", "assert" -> true;
                default -> false;
            };
        }
    }
}

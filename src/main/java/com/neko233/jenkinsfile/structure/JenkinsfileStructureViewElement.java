package com.neko233.jenkinsfile.structure;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.CURLY_GROUP;

/** Displays Jenkins Pipeline blocks as navigable nodes in the IDE's Structure view. */
final class JenkinsfileStructureViewElement implements StructureViewTreeElement {
    private static final Set<String> PIPELINE_BLOCKS = Set.of(
            "pipeline", "agent", "stages", "stage", "steps", "post", "always", "success", "failure",
            "unstable", "changed", "aborted", "unsuccessful", "cleanup", "regression", "fixed",
            "environment", "options", "parameters", "triggers", "tools", "input", "when", "branch",
            "expression", "not", "allOf", "anyOf", "matrix", "axes", "axis", "exclude", "excludes",
            "script", "parallel", "sequential", "beforeAgent", "beforeInput", "beforeOptions", "buildingTag",
            "changelog", "changeset", "changeRequest", "tag", "triggeredBy", "credentials", "postBuild"
    );

    private final PsiElement element;
    private final String presentableText;

    JenkinsfileStructureViewElement(@NotNull PsiElement element, @NotNull String presentableText) {
        this.element = element;
        this.presentableText = presentableText;
    }

    @Override
    public Object getValue() {
        return element;
    }

    @Override
    public @NotNull PresentationData getPresentation() {
        return new PresentationData(presentableText, null, null, null);
    }

    @Override
    public TreeElement @NotNull [] getChildren() {
        ASTNode node = element.getNode();
        if (node == null) return EMPTY_ARRAY;

        List<TreeElement> children = new ArrayList<>();
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == CURLY_GROUP) {
                addPipelineBlocks(child, children);
            }
        }
        return children.toArray(TreeElement.EMPTY_ARRAY);
    }

    private static void addPipelineBlocks(ASTNode group, List<TreeElement> result) {
        String header = getBlockHeader(group);
        if (header != null && isPipelineBlock(header)) {
            PsiElement psi = group.getPsi();
            if (psi != null) {
                result.add(new JenkinsfileStructureViewElement(psi, header));
                return;
            }
        }

        // Some Groovy closures are not Pipeline sections. Walk through them to retain
        // nested Pipeline blocks without exposing every implementation-level closure.
        for (ASTNode child = group.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == CURLY_GROUP) {
                addPipelineBlocks(child, result);
            }
        }
    }

    private static String getBlockHeader(ASTNode group) {
        ASTNode previous = previousNonWhitespace(group.getTreePrev());
        if (previous == null) return null;

        StringBuilder header = new StringBuilder();
        if (previous.getElementType() == com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.PAREN_GROUP) {
            header.insert(0, previous.getText());
            ASTNode name = previousNonWhitespace(previous.getTreePrev());
            if (name != null && isIdentifier(name.getElementType())) {
                header.insert(0, name.getText());
            }
        } else if (isIdentifier(previous.getElementType())) {
            header.insert(0, previous.getText());
        } else {
            return null;
        }

        String text = header.toString().replaceAll("\\s+", " ").trim();
        return text.isEmpty() ? null : text;
    }

    private static ASTNode previousNonWhitespace(ASTNode node) {
        while (node != null && (node.getElementType() == com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.WHITE_SPACE
                || node.getElementType() == com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.LINE_COMMENT
                || node.getElementType() == com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.BLOCK_COMMENT)) {
            node = node.getTreePrev();
        }
        return node;
    }

    private static boolean isIdentifier(IElementType type) {
        return type == com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.IDENTIFIER
                || type == com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.METHOD_CALL
                || type == com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.PIPELINE_KEYWORD
                || type == com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.KEYWORD;
    }

    private static boolean isPipelineBlock(String header) {
        int paren = header.indexOf('(');
        String name = paren < 0 ? header : header.substring(0, paren);
        return PIPELINE_BLOCKS.contains(name);
    }
}

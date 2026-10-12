package com.neko233.jenkinsfile.language;

import com.intellij.lang.PairedBraceMatcher;
import com.intellij.lang.BracePair;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.*;

public final class JenkinsfileBraceMatcher implements PairedBraceMatcher {
    private static final BracePair[] PAIRS = {
            new BracePair(LEFT_BRACE, RIGHT_BRACE, true),
            new BracePair(LEFT_PAREN, RIGHT_PAREN, false),
            new BracePair(LEFT_BRACKET, RIGHT_BRACKET, false)
    };

    @Override
    public BracePair @NotNull [] getPairs() {
        return PAIRS;
    }

    @Override
    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, IElementType contextType) {
        return true;
    }

    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }
}

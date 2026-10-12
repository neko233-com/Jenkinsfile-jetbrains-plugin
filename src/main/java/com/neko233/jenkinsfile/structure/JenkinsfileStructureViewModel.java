package com.neko233.jenkinsfile.structure;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JenkinsfileStructureViewModel extends StructureViewModelBase {
    public JenkinsfileStructureViewModel(@NotNull PsiFile file, @Nullable Editor editor) {
        super(file, editor, new JenkinsfileStructureViewElement(file, "Jenkinsfile"));
    }

    @Override
    protected Class<?>[] getSuitableClasses() {
        return new Class<?>[]{PsiFile.class, ASTWrapperPsiElement.class};
    }
}

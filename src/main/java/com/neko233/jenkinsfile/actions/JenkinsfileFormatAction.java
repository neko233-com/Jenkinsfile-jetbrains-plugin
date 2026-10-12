package com.neko233.jenkinsfile.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.neko233.jenkinsfile.language.JenkinsfileFileType;
import org.jetbrains.annotations.NotNull;

public final class JenkinsfileFormatAction extends AnAction {
    @Override
    public void update(@NotNull AnActionEvent event) {
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        boolean supported = file != null && file.getFileType() == JenkinsfileFileType.INSTANCE;
        event.getPresentation().setEnabledAndVisible(supported);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        if (file == null || file.getFileType() != JenkinsfileFileType.INSTANCE) return;

        WriteCommandAction.runWriteCommandAction(file.getProject(), (Runnable) () ->
                CodeStyleManager.getInstance(file.getProject()).reformat(file));
    }
}

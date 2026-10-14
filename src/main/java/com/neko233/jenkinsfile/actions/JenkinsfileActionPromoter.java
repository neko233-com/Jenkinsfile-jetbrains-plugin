package com.neko233.jenkinsfile.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPromoter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.neko233.jenkinsfile.language.JenkinsfileFileType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Prefers Jenkinsfile formatting over GoLand's conflicting Find Previous shortcut in Jenkinsfiles. */
public final class JenkinsfileActionPromoter implements ActionPromoter {
    @Override
    public @NotNull List<AnAction> promote(@NotNull List<? extends AnAction> actions,
                                          @NotNull DataContext context) {
        if (!isJenkinsfile(context)) return List.of();

        ActionManager actionManager = ActionManager.getInstance();
        for (AnAction action : actions) {
            if ("Jenkinsfile.Reformat".equals(actionManager.getId(action))) return List.of(action);
        }
        return List.of();
    }

    private static boolean isJenkinsfile(DataContext context) {
        PsiFile file = CommonDataKeys.PSI_FILE.getData(context);
        if (file == null) {
            Editor editor = CommonDataKeys.EDITOR.getData(context);
            Project project = CommonDataKeys.PROJECT.getData(context);
            if (editor != null && project != null) {
                file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            }
        }
        return file != null && file.getFileType() == JenkinsfileFileType.INSTANCE;
    }
}

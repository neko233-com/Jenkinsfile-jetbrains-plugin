package com.neko233.jenkinsfile.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ImportJenkinsGdslAction extends AnAction {
    private static final String GDSL_STEPS_KEY = "jenkinsfile.imported.gdsl.steps";
    private static final Pattern NAMED_MEMBER = Pattern.compile(
            "(?i)\\b(?:method|property)\\s*(?:\\(\\s*)?name\\s*:\\s*['\"]([A-Za-z_$][A-Za-z0-9_$]*)['\"]");

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;

        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false)
                .withTitle("Import Jenkins Pipeline GDSL")
                .withDescription("Choose the IntelliJ IDEA GDSL file downloaded from Jenkins Pipeline Syntax.")
                .withFileFilter(file -> file.getName().toLowerCase().endsWith(".gdsl"));
        VirtualFile file = FileChooser.chooseFile(descriptor, project, null);
        if (file == null) return;

        try {
            String source = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            Matcher matcher = NAMED_MEMBER.matcher(source);
            Set<String> names = new LinkedHashSet<>();
            while (matcher.find()) names.add(matcher.group(1));
            if (names.isEmpty()) {
                Messages.showWarningDialog(project,
                        "No Pipeline methods or globals were found in this file. Download the IntelliJ IDEA GDSL from a Jenkins Pipeline Syntax page.",
                        "No Jenkins DSL Entries Found");
                return;
            }
            PropertiesComponent.getInstance(project).setValue(GDSL_STEPS_KEY, String.join("\n", names));
            Messages.showInfoMessage(project, "Imported " + names.size() + " Jenkins DSL completion entries.", "Jenkinsfile GDSL Imported");
        } catch (IOException exception) {
            Messages.showErrorDialog(project, "Could not read the GDSL file: " + exception.getMessage(), "Jenkinsfile GDSL Import Failed");
        }
    }
}

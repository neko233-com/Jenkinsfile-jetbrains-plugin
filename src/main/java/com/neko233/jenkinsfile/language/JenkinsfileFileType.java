package com.neko233.jenkinsfile.language;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public final class JenkinsfileFileType extends LanguageFileType {
    public static final JenkinsfileFileType INSTANCE = new JenkinsfileFileType();
    private static final Icon ICON = IconLoader.getIcon("/icons/jenkins.svg", JenkinsfileFileType.class);

    private JenkinsfileFileType() {
        super(JenkinsfileLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "Jenkinsfile";
    }

    @Override
    public @NotNull String getDescription() {
        return "Jenkins Pipeline definition";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "jenkinsfile";
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }
}

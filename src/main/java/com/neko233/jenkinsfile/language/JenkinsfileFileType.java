package com.neko233.jenkinsfile.language;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public final class JenkinsfileFileType extends LanguageFileType implements FileTypeIdentifiableByVirtualFile {
    public static final JenkinsfileFileType INSTANCE = new JenkinsfileFileType();
    private static final Icon ICON = IconLoader.getIcon("/icons/jenkins.svg", JenkinsfileFileType.class);

    private JenkinsfileFileType() {
        super(JenkinsfileLanguage.INSTANCE);
    }

    @Override
    public boolean isMyFileType(@NotNull VirtualFile file) {
        return StringUtil.containsIgnoreCase(file.getName(), "jenkinsfile");
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

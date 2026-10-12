package com.neko233.jenkinsfile.language;

import com.intellij.lang.Language;

public final class JenkinsfileLanguage extends Language {
    public static final JenkinsfileLanguage INSTANCE = new JenkinsfileLanguage();

    private JenkinsfileLanguage() {
        super("Jenkinsfile");
    }
}

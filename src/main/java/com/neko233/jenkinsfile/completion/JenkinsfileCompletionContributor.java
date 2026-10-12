package com.neko233.jenkinsfile.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.neko233.jenkinsfile.language.JenkinsfileLexer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.*;

public final class JenkinsfileCompletionContributor extends CompletionContributor {
    private static final String GDSL_STEPS_KEY = "jenkinsfile.imported.gdsl.steps";
    private static final List<Entry> DECLARATIVE_TOP_LEVEL = entries(
            "agent|Select where the Pipeline runs", "stages|Declare the Pipeline stages", "environment|Define environment variables",
            "options|Configure Pipeline options", "parameters|Declare build parameters", "triggers|Configure build triggers",
            "tools|Select tools installed on the agent", "input|Wait for a manual input", "when|Set stage conditions",
            "post|Declare post-build actions", "libraries|Load shared libraries", "parallel|Run stages in parallel", "matrix|Run a matrix of stages"
    );
    private static final List<Entry> STAGE_DIRECTIVES = entries(
            "agent|Override the stage agent", "environment|Set stage environment variables", "options|Set stage options",
            "input|Request input before the stage", "when|Set stage conditions", "tools|Select stage tools",
            "steps|Declare steps for the stage", "post|Declare stage post conditions", "stages|Create nested stages",
            "parallel|Run nested stages in parallel", "matrix|Run a stage matrix"
    );
    private static final List<Entry> PIPELINE_STEPS = entries(
            "archiveArtifacts|artifacts, allowEmptyArchive, fingerprint", "bat|script, returnStatus, returnStdout, encoding",
            "build|job, parameters, propagate, wait, waitForStart", "catchError|buildResult, stageResult, catchInterruptions",
            "checkout|scm", "deleteDir|Delete the current workspace directory", "dir|Enter a workspace subdirectory",
            "echo|message", "error|message", "fileExists|file", "input|message, id, ok, parameters, submitter",
            "isUnix|Check whether the current agent uses Unix", "junit|testResults, allowEmptyResults, keepLongStdio",
            "library|Load a shared library", "libraryResource|resource", "load|Load a Groovy file from the workspace",
            "mail|to, subject, body, cc, bcc", "milestone|ordinal, label", "node|label", "parallel|Map of branches",
            "powershell|script, returnStatus, returnStdout, encoding", "properties|properties", "pwd|tmp",
            "readFile|file, encoding", "readTrusted|path", "retry|count, conditions", "sh|script, returnStatus, returnStdout, encoding",
            "sleep|time, unit", "stash|name, includes, excludes, useDefaultExcludes", "step|step", "timeout|time, unit, activity",
            "tool|name, type", "unarchive|mapping", "unstable|message", "unstash|name", "waitUntil|initialRecurrencePeriod",
            "warnError|message, catchInterruptions", "withContext|context", "withCredentials|bindings",
            "withEnv|overrides", "wrap|wrapper", "ws|dir", "addBadge|text, id", "archive|artifacts",
            "compareVersions|v1, v2, comparator", "configFileProvider|configFiles", "deleteDir|Delete the current workspace directory",
            "dockerNode|image, args", "findFiles|glob, excludes", "git|url, branch, credentialsId",
            "httpRequest|url, httpMode, authentication, validResponseCodes", "lock|resource, label, quantity",
            "readJSON|file, text, returnPojo", "readYaml|file, text, codePointLimit", "recordIssues|tools",
            "resolveScm|source, targets", "retry|count", "sshagent|credentials", "stage|name", "tee|path",
            "writeFile|file, text, encoding", "zip|zipFile, archive, dir, glob", "unzip|zipFile, dir, glob"
    );
    private static final List<Entry> DECLARATIVE_POST = entries(
            "always|Run regardless of the build result", "changed|Run when the result changed", "failure|Run after failure",
            "success|Run after success", "unstable|Run when the result is unstable", "aborted|Run when aborted",
            "unsuccessful|Run when the result is not successful", "fixed|Run when the result returns to success",
            "regression|Run when the result regresses", "cleanup|Run after all other post conditions"
    );
    private static final List<Entry> AGENT_VALUES = entries(
            "any|Run on any available agent", "none|Do not allocate a top-level agent", "label|Select an agent by label",
            "node|Configure a node agent", "docker|Run in a Docker container", "dockerfile|Build an image from a Dockerfile",
            "kubernetes|Run in a Kubernetes pod"
    );
    private static final List<Entry> WHEN_CONDITIONS = entries(
            "branch|Match a branch name", "buildingTag|Match a tag build", "changelog|Match changelog text",
            "changeset|Match changed files", "changeRequest|Match a change request", "environment|Match an environment variable",
            "equals|Compare two values", "expression|Evaluate a Groovy expression", "tag|Match a tag name",
            "triggeredBy|Match the build trigger", "not|Negate a condition", "allOf|Require every condition",
            "anyOf|Require any condition"
    );
    private static final List<Entry> BUILD_OPTIONS = entries(
            "buildDiscarder|Configure build retention", "disableConcurrentBuilds|Prevent concurrent builds",
            "disableResume|Disable Pipeline resume", "newContainerPerStage|Use a fresh container for each stage",
            "overrideIndexTriggers|Override branch indexing triggers", "preserveStashes|Preserve stashes across restarts",
            "quietPeriod|Set the queue quiet period", "skipDefaultCheckout|Disable the automatic checkout",
            "skipStagesAfterUnstable|Skip remaining stages after an unstable result", "timeout|Set a Pipeline timeout",
            "parallelsAlwaysFailFast|Stop parallel branches after a failure", "retry|Retry the whole Pipeline",
            "timestamps|Add timestamps to console output", "ansiColor|Enable ANSI colors"
    );
    private static final List<Entry> PARAMETERS = entries(
            "booleanParam|name, defaultValue, description", "choice|name, choices, description", "credentials|name, credentialsId, description",
            "file|name, description", "password|name, defaultValue, description", "string|name, defaultValue, description",
            "text|name, defaultValue, description", "activeChoice|name, choiceType, script", "gitParameter|name, type, defaultValue"
    );
    private static final List<Entry> TRIGGERS = entries(
            "cron|Jenkins cron schedule", "pollSCM|SCM polling schedule", "upstream|upstreamProjects, threshold"
    );
    private static final List<Entry> TOOLS = entries(
            "jdk|Select a configured JDK", "maven|Select a configured Maven installation", "gradle|Select a configured Gradle installation"
    );
    private static final List<Entry> GLOBALS = entries(
            "env|Environment variables", "params|Build parameters", "currentBuild|Current build metadata",
            "currentResult|Current build result", "scm|The job source control configuration", "docker|Docker Pipeline DSL",
            "manager|Pipeline execution metadata", "pipeline|Declarative Pipeline entry point", "node|Allocate a node",
            "stage|Create a stage", "parallel|Run branches concurrently", "properties|Set job properties"
    );

    public JenkinsfileCompletionContributor() {
        extend(com.intellij.codeInsight.completion.CompletionType.BASIC,
                PlatformPatterns.psiElement(), new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        PsiFile file = parameters.getOriginalFile();
                        String text = file.getText();
                        int offset = Math.min(parameters.getOffset(), text.length());
                        String prefix = prefixAt(text, offset);
                        CompletionResultSet filtered = result.withPrefixMatcher(new PlainPrefixMatcher(prefix));
                        String block = currentBlock(text.substring(0, offset));

                        add(filtered, switch (block) {
                            case "pipeline" -> DECLARATIVE_TOP_LEVEL;
                            case "stages" -> entries("stage|Define a named stage");
                            case "stage" -> STAGE_DIRECTIVES;
                            case "steps", "script", "node", "parallel" -> PIPELINE_STEPS;
                            case "post" -> DECLARATIVE_POST;
                            case "agent" -> AGENT_VALUES;
                            case "when" -> WHEN_CONDITIONS;
                            case "options" -> BUILD_OPTIONS;
                            case "parameters" -> PARAMETERS;
                            case "triggers" -> TRIGGERS;
                            case "tools" -> TOOLS;
                            case "environment" -> entries("credentials|credentials(id) helper", "env|Read or set environment variables");
                            default -> rootEntries();
                        });
                        if (parameters.getPosition().getProject() != null) {
                            String imported = PropertiesComponent.getInstance(parameters.getPosition().getProject()).getValue(GDSL_STEPS_KEY, "");
                            for (String name : imported.split("\\n")) {
                                if (!name.isBlank()) add(filtered, List.of(new Entry(name, "Imported from Jenkins GDSL", "Jenkins server DSL")));
                            }
                        }
                    }
                });
    }

    private static List<Entry> rootEntries() {
        List<Entry> result = new ArrayList<>();
        result.addAll(DECLARATIVE_TOP_LEVEL);
        result.addAll(PIPELINE_STEPS);
        result.addAll(GLOBALS);
        return result;
    }

    private static void add(CompletionResultSet result, List<Entry> entries) {
        Map<String, Entry> unique = new LinkedHashMap<>();
        for (Entry entry : entries) unique.putIfAbsent(entry.name(), entry);
        for (Entry entry : unique.values()) {
            LookupElementBuilder lookup = LookupElementBuilder.create(entry.name())
                    .withTypeText(entry.type(), true)
                    .withTailText(entry.detail().isBlank() ? "" : "  " + entry.detail(), true)
                    .withBoldness(entry.type().equals("Declarative section") || entry.type().equals("Declarative directive"));
            result.addElement(lookup);
        }
    }

    private static String prefixAt(String text, int offset) {
        int start = offset;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (!Character.isJavaIdentifierPart(c) && c != '$') break;
            start--;
        }
        return text.substring(start, offset);
    }

    private static String currentBlock(String text) {
        Deque<String> blocks = new ArrayDeque<>();
        JenkinsfileLexer lexer = new JenkinsfileLexer();
        lexer.start(text, 0, text.length(), 0);
        String previousWord = "";
        while (lexer.getTokenType() != null) {
            IElementType type = lexer.getTokenType();
            if (type == LEFT_BRACE) {
                blocks.push(previousWord);
                previousWord = "";
            } else if (type == RIGHT_BRACE) {
                if (!blocks.isEmpty()) blocks.pop();
                previousWord = "";
            } else if (type == IDENTIFIER || type == KEYWORD || type == PIPELINE_KEYWORD) {
                previousWord = text.substring(lexer.getTokenStart(), lexer.getTokenEnd());
            } else if (type == SEMICOLON) {
                previousWord = "";
            }
            lexer.advance();
        }
        Set<String> known = Set.of("pipeline", "stages", "stage", "steps", "script", "node", "parallel",
                "post", "agent", "when", "options", "parameters", "triggers", "tools", "environment");
        for (String block : blocks) if (known.contains(block)) return block;
        return "";
    }

    private static List<Entry> entries(String... definitions) {
        List<Entry> result = new ArrayList<>(definitions.length);
        for (String definition : definitions) {
            int separator = definition.indexOf('|');
            String name = separator < 0 ? definition : definition.substring(0, separator);
            String detail = separator < 0 ? "" : definition.substring(separator + 1);
            String type = switch (name) {
                case "pipeline", "stages", "stage", "steps", "post", "environment", "options", "parameters",
                        "triggers", "tools", "input", "when", "agent", "matrix", "parallel" -> "Declarative section";
                default -> "Jenkins Pipeline DSL";
            };
            result.add(new Entry(name, detail, type));
        }
        return result;
    }

    private record Entry(String name, String detail, String type) {
    }
}

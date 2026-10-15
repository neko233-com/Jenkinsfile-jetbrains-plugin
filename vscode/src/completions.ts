import * as vscode from "vscode";

interface Suggestion {
  readonly label: string;
  readonly detail: string;
  readonly insertText?: string;
  readonly kind: vscode.CompletionItemKind;
}

const PIPELINE_SECTIONS: Suggestion[] = [
  { label: "agent", detail: "Choose the Jenkins agent", insertText: "agent any", kind: vscode.CompletionItemKind.Keyword },
  { label: "options", detail: "Pipeline or stage options", insertText: "options {\n\t${1:timestamps()}\n}", kind: vscode.CompletionItemKind.Keyword },
  { label: "parameters", detail: "Build parameters", insertText: "parameters {\n\t${1:string(name: 'VERSION', defaultValue: '', description: 'Release version')}\n}", kind: vscode.CompletionItemKind.Keyword },
  { label: "environment", detail: "Environment variables", insertText: "environment {\n\t${1:KEY} = '${2:value}'\n}", kind: vscode.CompletionItemKind.Keyword },
  { label: "stages", detail: "Pipeline stages", insertText: "stages {\n\tstage('${1:Build}') {\n\t\tsteps {\n\t\t\t${2:echo 'Build'}\n\t\t}\n\t}\n}", kind: vscode.CompletionItemKind.Keyword },
  { label: "post", detail: "Post-build conditions", insertText: "post {\n\talways {\n\t\t${1:echo 'Finished'}\n\t}\n}", kind: vscode.CompletionItemKind.Keyword },
  { label: "tools", detail: "Pipeline tools", insertText: "tools {\n\t${1:go '1.23'}\n}", kind: vscode.CompletionItemKind.Keyword },
  { label: "triggers", detail: "Pipeline triggers", insertText: "triggers {\n\t${1:cron('H/15 * * * *')}\n}", kind: vscode.CompletionItemKind.Keyword }
];

const PARAMETER_STEPS: Suggestion[] = [
  parameter("booleanParam", "Boolean build parameter"),
  parameter("choice", "Choice build parameter"),
  parameter("credentials", "Credentials build parameter"),
  parameter("file", "File build parameter"),
  parameter("password", "Password build parameter"),
  parameter("string", "String build parameter"),
  parameter("text", "Text build parameter")
];

const PIPELINE_STEPS: Suggestion[] = [
  step("archiveArtifacts", "Archive build artifacts"), step("bat", "Run a Windows command"),
  step("build", "Trigger another job"), step("checkout", "Check out source code"),
  step("cleanWs", "Clean the workspace"), step("cmd", "Run a Windows command"),
  step("deleteDir", "Delete the current workspace"), step("dir", "Run steps in a subdirectory"),
  step("echo", "Print a message"), step("error", "Fail the build"), step("git", "Check out a Git repository"),
  step("input", "Wait for manual input"), step("junit", "Publish JUnit reports"),
  step("powershell", "Run a PowerShell script"), step("pwsh", "Run a PowerShell Core script"),
  step("readFile", "Read a workspace file"), step("retry", "Retry a block of steps"),
  step("sh", "Run a POSIX shell script"), step("sleep", "Wait for a duration"),
  step("stash", "Save files for later stages"), step("timeout", "Apply a timeout"),
  step("unstash", "Restore saved files"), step("withCredentials", "Bind credentials in a block"),
  step("withEnv", "Set environment variables for a block"), step("writeFile", "Write a workspace file")
];

const NAMED_FIELDS: Record<string, Suggestion[]> = {
  booleanParam: [field("name"), field("defaultValue"), field("description")],
  choice: [field("name"), field("choices"), field("description")],
  credentials: [field("name"), field("defaultValue"), field("description"), field("required")],
  file: [field("name"), field("description")],
  password: [field("name"), field("defaultValue"), field("description")],
  string: [field("name"), field("defaultValue"), field("description"), field("trim")],
  text: [field("name"), field("defaultValue"), field("description")],
  sh: [field("script"), field("label"), field("returnStatus"), field("returnStdout"), field("encoding")],
  powershell: [field("script"), field("label"), field("returnStatus"), field("returnStdout"), field("encoding")],
  pwsh: [field("script"), field("label"), field("returnStatus"), field("returnStdout"), field("encoding")],
  timeout: [field("time"), field("unit"), field("activity")],
  retry: [field("count"), field("conditions")],
  stage: [field("name")]
};

const GLOBALS: Suggestion[] = [
  variable("env", "Environment variables"), variable("params", "Build parameters"),
  variable("currentBuild", "Current build metadata"), variable("scm", "Source control configuration"),
  ...["BUILD_ID", "BUILD_NUMBER", "BUILD_TAG", "BUILD_URL", "JOB_NAME", "JOB_URL", "NODE_NAME", "WORKSPACE", "BRANCH_NAME", "CHANGE_ID", "TAG_NAME"]
    .map((name) => variable(name, "Jenkins environment variable"))
];

export function provideCompletions(document: vscode.TextDocument, position: vscode.Position): vscode.CompletionItem[] {
  const beforeCursor = document.getText(new vscode.Range(new vscode.Position(0, 0), position));
  const currentLinePrefix = document.lineAt(position.line).text.slice(0, position.character);
  const items: Suggestion[] = [];
  const argument = beforeCursor.match(/\b([A-Za-z_$][\w$]*)\s*\([^)]*$/s);
  if (argument) items.push(...(NAMED_FIELDS[argument[1]!] ?? []));

  const context = beforeCursor.match(/\b(parameters|steps|stages|post|options|environment|when)\s*\{[^}]*$/s)?.[1]?.toLowerCase();
  if (context === "parameters") items.push(...PARAMETER_STEPS);
  else if (context === "steps") items.push(...PIPELINE_STEPS);
  else if (!context || context === "pipeline") items.push(...PIPELINE_SECTIONS);
  items.push(...GLOBALS);

  const prefix = currentLinePrefix.match(/[A-Za-z_$][\w$]*$/)?.[0]?.toLowerCase() ?? "";
  return dedupe(items)
    .filter((item) => !prefix || item.label.toLowerCase().startsWith(prefix))
    .map((suggestion) => {
      const item = new vscode.CompletionItem(suggestion.label, suggestion.kind);
      item.detail = suggestion.detail;
      item.insertText = suggestion.insertText ? new vscode.SnippetString(suggestion.insertText) : suggestion.label;
      item.sortText = suggestion.label;
      return item;
    });
}

function parameter(name: string, detail: string): Suggestion {
  return {
    label: name,
    detail,
    insertText: `${name}(name: '\${1:NAME}', defaultValue: '\${2:}', description: '\${3:Description}')`,
    kind: vscode.CompletionItemKind.Function
  };
}

function step(name: string, detail: string): Suggestion {
  return { label: name, detail, insertText: `${name}(${1})`, kind: vscode.CompletionItemKind.Function };
}

function field(name: string): Suggestion {
  return { label: name, detail: "Named argument", insertText: `${name}: `, kind: vscode.CompletionItemKind.Field };
}

function variable(name: string, detail: string): Suggestion {
  return { label: name, detail, kind: vscode.CompletionItemKind.Variable };
}

function dedupe(items: readonly Suggestion[]): Suggestion[] {
  return [...new Map(items.map((item) => [item.label, item])).values()];
}

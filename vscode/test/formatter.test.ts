import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";
import { formatJenkinsfile } from "../src/formatter.js";
import { isJenkinsfilePath } from "../src/filename.js";
import { tokenize } from "../src/lexer.js";

const messyPipeline = [
  "pipeline   {",
  "parameters   {",
  "choice(name:   'CHANNEL',   choices:   ['wechat',   'taptap'] ,   description:   'target')",
  "string(name:   'VERSION',   defaultValue:   '',   description:   'version')",
  "}",
  "stages   {",
  "stage('Build')   { steps   { sh 'echo ok' } }",
  "stage('Deploy')   { steps   { sh 'echo done' } }",
  "}",
  "}"
].join("\n");

const expectedPipeline = [
  "pipeline {",
  "    parameters {",
  "        choice(",
  "            name: 'CHANNEL',",
  "            choices: ['wechat', 'taptap'],",
  "            description: 'target'",
  "        )",
  "        string(",
  "            name: 'VERSION',",
  "            defaultValue: '',",
  "            description: 'version'",
  "        )",
  "    }",
  "",
  "    stages {",
  "        stage('Build') {",
  "            steps {",
  "                sh '''",
  "                    echo ok",
  "                '''",
  "            }",
  "        }",
  "",
  "        stage('Deploy') {",
  "            steps {",
  "                sh '''",
  "                    echo done",
  "                '''",
  "            }",
  "        }",
  "    }",
  "}"
].join("\n");

test("normalizes nested parameter and stage spacing with one indent per level", () => {
  const formatted = formatJenkinsfile(messyPipeline);
  assert.equal(formatted, expectedPipeline);
  assert.equal(formatJenkinsfile(formatted), formatted);
  assert.equal(formatJenkinsfile(formatJenkinsfile(formatted)), formatted);
});

test("formats shell families into readable, stable multiline literals", () => {
  const source = [
    "pipeline { stages { stage('Release') { steps {",
    "sh 'echo first; go run ./tools/release/cmd --action=restart-version --release-branch=release/wechat/test/game-server/1.2.3 --release-version=1.2.3 --confirm-publish-latest=false --component game-server --channel=wechat --environment=test --notify=false'",
    "cmd 'echo begin && echo end'",
    "powershell 'Write-Output begin; Write-Output end'",
    "} } } }"
  ].join("\n");

  const formatted = formatJenkinsfile(source);
  assert.match(formatted, /sh '''\n\s+echo first\n\s+go run \.\/tools\/release\/cmd/);
  assert.match(formatted, /go run \.\/tools\/release\/cmd \\\\\n\s+--action=/);
  assert.match(formatted, /--release-branch=release\/wechat\/test\/game-server\/1\.2\.3/);
  assert.match(formatted, /--action=restart-version/);
  assert.match(formatted, /cmd '''\n\s+echo begin && \^\n/);
  assert.match(formatted, /--environment=test \\\\\n\s+--notify=false/);
  assert.match(formatted, /powershell '''\n\s+Write-Output begin\n\s+Write-Output end/);
  assert.equal(formatJenkinsfile(formatted), formatted);
});

test("leaves shell programs with heredocs or control flow unchanged", () => {
  const source = "pipeline { stages { stage('X') { steps { sh 'cat <<EOF; if true; then echo yes; fi' } } } }";
  const formatted = formatJenkinsfile(source);
  assert.ok(formatted.includes("sh 'cat <<EOF; if true; then echo yes; fi'"));
  assert.equal(formatJenkinsfile(formatted), formatted);
});

test("does not rewrite concatenated shell expressions or whitespace inside Groovy strings", () => {
  const source = "pipeline { stages { stage('X') { steps { sh 'echo result' + suffix } } } }\n"
    + "environment { NOTE = '''line   \ncontinued''' }";
  const formatted = formatJenkinsfile(source);
  assert.ok(formatted.includes("sh 'echo result' + suffix"));
  assert.ok(formatted.includes("line   \ncontinued"));
  assert.equal(formatJenkinsfile(formatted), formatted);
});

test("uses the filename substring case-insensitively, including arbitrary extensions", () => {
  assert.equal(isJenkinsfilePath("Jenkinsfile"), true);
  assert.equal(isJenkinsfilePath("deploy-jEnKiNsFiLe.backup.prod"), true);
  assert.equal(isJenkinsfilePath("C:/repo/my_jenkinsfile_file.txt"), true);
  assert.equal(isJenkinsfilePath("C:\\repo\\release.JenkinsFile.data"), true);
  assert.equal(isJenkinsfilePath("C:/repo/Pipeline.groovy"), false);
});

test("grammar and language configuration are valid JSON with named field scopes", () => {
  const root = join(process.cwd());
  const grammar = JSON.parse(readFileSync(join(root, "syntaxes", "jenkinsfile.tmLanguage.json"), "utf8")) as {
    scopeName: string;
    repository: Record<string, unknown>;
  };
  const languageConfiguration = JSON.parse(readFileSync(join(root, "language-configuration.json"), "utf8")) as object;
  assert.equal(grammar.scopeName, "source.jenkinsfile");
  assert.ok("named-arguments" in grammar.repository);
  assert.ok("embedded-shell" in grammar.repository);
  assert.ok(languageConfiguration);
});

test("lexer keeps delimiters inside strings and reports unclosed literals", () => {
  const tokens = tokenize("sh 'echo {ok}'\n/* open");
  assert.equal(tokens.find((token) => token.kind === "string")?.terminated, true);
  assert.equal(tokens.find((token) => token.kind === "block-comment")?.terminated, false);
});

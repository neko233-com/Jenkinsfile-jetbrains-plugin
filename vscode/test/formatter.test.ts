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
  "",
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
  "                sh 'echo ok'",
  "            }",
  "        }",
  "",
  "        stage('Deploy') {",
  "            steps {",
  "                sh 'echo done'",
  "            }",
  "        }",
  "    }",
  "}"
].join("\n") + "\n";

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

test("formats the reported release pipeline exactly and stays unchanged over ten passes", () => {
  const before = readFileSync(join(process.cwd(), "../src/test/testData/formatting/release.before.jenkinsfile"), "utf8");
  const expected = readFileSync(join(process.cwd(), "examples/Jenkinsfile.release"), "utf8");
  let actual = formatJenkinsfile(before);
  assert.equal(actual, expected);
  for (let pass = 0; pass < 10; pass++) assert.equal(actual = formatJenkinsfile(actual), expected);
  assert.doesNotMatch(actual, /\n\n\n|[ \t]+\n/);
});

test("keeps short commands compact, removes optional terminators and retains empty control bodies", () => {
  assert.equal(formatJenkinsfile("sh 'echo ok';\ncmd 'echo ok';\npwsh 'Write-Host ok';\nwhile (waiting);\necho 'done';"),
    "sh 'echo ok'\ncmd 'echo ok'\npwsh 'Write-Host ok'\nwhile (waiting);\necho 'done'\n");
});

test("uses deliberate spacing for collections, indexing, operators and chained calls", () => {
  const source = "def items=[1,2]; def map=[a:1,b:2]; def total=(1 + 2); total++; def next=total++ + 1; return items[0]?.toString()";
  const expected = "def items = [1, 2]\ndef map = [a: 1, b: 2]\ndef total = (1 + 2)\ntotal++\ndef next = total++ + 1\nreturn items[0]?.toString()\n";
  assert.equal(formatJenkinsfile(source), expected);
  assert.equal(formatJenkinsfile(expected), expected);
});

test("preserves leading and trailing argument comments without swallowing fields or commas", () => {
  const source = 'choice(\n// choose one\nname: "CHANNEL", // channel id\nchoices: ["a", "b"], description: "target")';
  const expected = 'choice(\n    // choose one\n    name: "CHANNEL", // channel id\n    choices: ["a", "b"],\n    description: "target"\n)\n';
  assert.equal(formatJenkinsfile(source), expected);
  assert.equal(formatJenkinsfile(expected), expected);
  assert.equal(formatJenkinsfile("echo 'ok'; // keep\nscript { echo 'x' } // block\necho 'next'"),
    "echo 'ok' // keep\nscript {\n    echo 'x'\n} // block\necho 'next'\n");
});

test("wraps named shell script arguments and supports configurable line width", () => {
  const source = "pipeline { stages { stage('Build') { steps { sh(script: 'echo first; echo second', returnStdout: true) } } } }";
  const formatted = formatJenkinsfile(source);
  assert.match(formatted, /sh\(\n\s+script: '''\n\s+echo first\n\s+echo second/);
  assert.match(formatted, /''',\n\s+returnStdout: true\n/);
  assert.equal(formatJenkinsfile(formatted), formatted);
  const call = "archiveArtifacts(artifacts: 'build/release/**/*.zip', fingerprint: true)";
  assert.ok(formatJenkinsfile(call, {insertSpaces:true, tabSize:4, printWidth:60}).includes("archiveArtifacts(\n"));
  assert.ok(!formatJenkinsfile(call, {insertSpaces:true, tabSize:4, printWidth:120}).includes("archiveArtifacts(\n"));
});

test("preserves CRLF and literal whitespace with exactly one final newline", () => {
  const source = "pipeline {\r\n\r\nagent any\r\nenvironment { NOTE = 'a   b' }\r\n}\r\n\r\n";
  const formatted = formatJenkinsfile(source);
  assert.doesNotMatch(formatted, /(?<!\r)\n/);
  assert.ok(formatted.includes("'a   b'"));
  assert.ok(formatted.endsWith("}\r\n"));
  assert.equal(formatJenkinsfile(formatted), formatted);
  assert.equal(formatJenkinsfile(" \r\n \r\n"), "");
});

test("does not rewrite unfinished edits with missing or mismatched delimiters", () => {
  for (const source of ["pipeline { stages {", "choice(name: 'x'", "pipeline { stages ] }", "sh 'unfinished", "/* unfinished"]) {
    assert.equal(formatJenkinsfile(source), source);
  }
});

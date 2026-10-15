import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { join } from 'node:path';
import test from 'node:test';
import textmate from 'vscode-textmate';
import oniguruma from 'vscode-oniguruma';

const { Registry, parseRawGrammar } = textmate;
const { loadWASM, OnigScanner, OnigString } = oniguruma;

const require = createRequire(import.meta.url);

test('TextMate grammar highlights named fields and embedded shell dialects', async () => {
  await loadWASM(readFileSync(require.resolve('vscode-oniguruma/release/onig.wasm')));
  const grammarPath = join(process.cwd(), 'syntaxes', 'jenkinsfile.tmLanguage.json');
  const rawGrammar = readFileSync(grammarPath, 'utf8');
  const registry = new Registry({
    onigLib: Promise.resolve({
      createOnigScanner: (sources) => new OnigScanner(sources),
      createOnigString: (value) => new OnigString(value)
    }),
    loadGrammar: async (scopeName) => scopeName === 'source.jenkinsfile'
      ? parseRawGrammar(rawGrammar, 'jenkinsfile.tmLanguage.json')
      : null
  });
  const grammar = await registry.loadGrammar('source.jenkinsfile');

  const shellScopes = tokenScopes(grammar, "sh '''go run --action=release \"$ACTION\"'''");
  assert.ok(shellScopes.some((scope) => scope.includes('support.function.pipeline.jenkinsfile')), shellScopes.join(', '));
  assert.ok(shellScopes.some((scope) => scope.includes('support.function.shell.jenkinsfile')), shellScopes.join(', '));
  assert.ok(shellScopes.some((scope) => scope.includes('constant.other.option.shell.jenkinsfile')), shellScopes.join(', '));
  assert.ok(shellScopes.some((scope) => scope.includes('variable.other.shell.jenkinsfile')), shellScopes.join(', '));

  const fieldScopes = tokenScopes(grammar, "choice(name: 'MODE', choices: ['force'], description: 'mode')");
  assert.ok(fieldScopes.some((scope) => scope.includes('variable.parameter.named.jenkinsfile')), fieldScopes.join(', '));

  const cmdScopes = tokenScopes(grammar, "cmd 'echo %BUILD_NUMBER% && git status'");
  assert.ok(cmdScopes.some((scope) => scope.includes('variable.other.environment.cmd.jenkinsfile')), cmdScopes.join(', '));
  assert.ok(cmdScopes.some((scope) => scope.includes('keyword.operator.cmd.jenkinsfile')), cmdScopes.join(', '));

  const powershellScopes = tokenScopes(grammar, "powershell 'Write-Output $env:BUILD_NUMBER -ErrorAction Stop'");
  assert.ok(powershellScopes.some((scope) => scope.includes('variable.other.powershell.jenkinsfile')), powershellScopes.join(', '));
  assert.ok(powershellScopes.some((scope) => scope.includes('constant.other.option.powershell.jenkinsfile')), powershellScopes.join(', '));
});

function tokenScopes(grammar, line) {
  const result = grammar.tokenizeLine(line);
  return result.tokens.flatMap((token) => token.scopes);
}

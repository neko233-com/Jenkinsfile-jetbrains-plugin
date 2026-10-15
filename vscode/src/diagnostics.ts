import * as vscode from "vscode";
import { Token, tokenize } from "./lexer.js";

const OPEN_TO_CLOSE = new Map([["{", "}"], ["(", ")"], ["[", "]"]]);
const CLOSE_TO_OPEN = new Map([["}", "{"], [")", "("], ["]", "["]]);

interface OpenDelimiter {
  readonly token: Token;
  readonly expected: string;
}

export function collectDiagnostics(document: vscode.TextDocument): vscode.Diagnostic[] {
  const text = document.getText();
  if (text.length > 250_000) return [];
  const diagnostics: vscode.Diagnostic[] = [];
  const stack: OpenDelimiter[] = [];

  for (const token of tokenize(text)) {
    if ((token.kind === "string" || token.kind === "block-comment") && token.terminated === false) {
      diagnostics.push(makeDiagnostic(document, token.start, token.end,
        token.kind === "string" ? "Unclosed Groovy string literal." : "Unclosed block comment."));
      continue;
    }
    if (token.kind !== "punctuation") continue;
    const expected = OPEN_TO_CLOSE.get(token.text);
    if (expected) {
      stack.push({ token, expected });
      continue;
    }
    if (!CLOSE_TO_OPEN.has(token.text)) continue;

    const opening = stack.at(-1);
    if (opening?.expected === token.text) {
      stack.pop();
    } else {
      diagnostics.push(makeDiagnostic(document, token.start, token.end,
        `Unexpected '${token.text}' without a matching '${CLOSE_TO_OPEN.get(token.text)}'.`));
    }
  }

  for (const opening of stack) {
    diagnostics.push(makeDiagnostic(document, opening.token.start, opening.token.end,
      `Expected '${opening.expected}' to close this block.`));
  }
  return diagnostics;
}

function makeDiagnostic(document: vscode.TextDocument, start: number, end: number, message: string): vscode.Diagnostic {
  const diagnostic = new vscode.Diagnostic(
    new vscode.Range(document.positionAt(start), document.positionAt(end)),
    message,
    vscode.DiagnosticSeverity.Error
  );
  diagnostic.source = "Jenkinsfile Free";
  return diagnostic;
}

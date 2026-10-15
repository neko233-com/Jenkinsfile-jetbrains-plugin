import * as vscode from "vscode";
import { Token, tokenize } from "./lexer.js";

const SECTIONS = new Set(["agent", "environment", "options", "parameters", "post", "stages", "tools", "triggers"]);

export function provideDocumentSymbols(document: vscode.TextDocument): vscode.DocumentSymbol[] {
  const tokens = tokenize(document.getText());
  const pipelineIndex = tokens.findIndex((token) => token.kind === "word" && token.text === "pipeline");
  if (pipelineIndex < 0) return [];
  const pipelineOpen = findNext(tokens, pipelineIndex + 1, "{");
  if (pipelineOpen < 0) return [];
  const pipelineClose = findMatching(tokens, pipelineOpen);
  const pipelineEnd = pipelineClose >= 0 ? pipelineClose : tokens.length - 1;
  const children: vscode.DocumentSymbol[] = [];

  for (let index = pipelineOpen + 1; index < pipelineEnd; index++) {
    const token = tokens[index]!;
    if (!SECTIONS.has(token.text) || token.kind !== "word") continue;
    const open = findNext(tokens, index + 1, "{");
    if (open < 0 || open >= pipelineEnd) continue;
    const close = findMatching(tokens, open);
    if (close < 0 || close > pipelineEnd) continue;
    const sectionName = token.text;
    const sectionChildren = sectionName === "stages" || sectionName === "parallel"
      ? collectStages(document, tokens, open, close)
      : [];
    children.push(createSymbol(document, sectionName, vscode.SymbolKind.Namespace, token, tokens[close]!, sectionChildren));
    index = close;
  }

  const pipeline = createSymbol(document, "pipeline", vscode.SymbolKind.Module,
    tokens[pipelineIndex]!, tokens[pipelineClose >= 0 ? pipelineClose : pipelineIndex]!, children);
  return [pipeline];
}

function collectStages(document: vscode.TextDocument, tokens: readonly Token[], open: number, close: number): vscode.DocumentSymbol[] {
  const result: vscode.DocumentSymbol[] = [];
  for (let index = open + 1; index < close; index++) {
    if (tokens[index]!.kind !== "word" || tokens[index]!.text !== "stage") continue;
    const parenOpen = findNext(tokens, index + 1, "(");
    if (parenOpen < 0 || parenOpen >= close) continue;
    const parenClose = findMatching(tokens, parenOpen);
    const blockOpen = parenClose >= 0 ? findNext(tokens, parenClose + 1, "{") : -1;
    if (parenClose < 0 || blockOpen < 0 || blockOpen >= close) continue;
    const blockClose = findMatching(tokens, blockOpen);
    if (blockClose < 0 || blockClose > close) continue;
    const nameToken = tokens.slice(parenOpen + 1, parenClose).find((candidate) => candidate.kind === "string");
    const name = nameToken ? unquote(nameToken.text) : "stage";
    result.push(createSymbol(document, name, vscode.SymbolKind.Function, tokens[index]!, tokens[blockClose]!));
    index = blockClose;
  }
  return result;
}

function createSymbol(
  document: vscode.TextDocument,
  name: string,
  kind: vscode.SymbolKind,
  start: Token,
  end: Token,
  children: vscode.DocumentSymbol[] = []
): vscode.DocumentSymbol {
  const range = new vscode.Range(document.positionAt(start.start), document.positionAt(end.end));
  const symbol = new vscode.DocumentSymbol(name, kind === vscode.SymbolKind.Function ? "stage" : "Jenkins Pipeline block", kind, range, range);
  symbol.children = children;
  return symbol;
}

function findNext(tokens: readonly Token[], start: number, text: string): number {
  for (let index = start; index < tokens.length; index++) {
    if (tokens[index]!.text === text) return index;
    if (tokens[index]!.text === "}" && text !== "}") return -1;
  }
  return -1;
}

function findMatching(tokens: readonly Token[], open: number): number {
  const opening = tokens[open]?.text;
  const expected = opening === "{" ? "}" : opening === "(" ? ")" : opening === "[" ? "]" : "";
  if (!expected) return -1;
  const stack: string[] = [expected];
  for (let index = open + 1; index < tokens.length; index++) {
    const text = tokens[index]!.text;
    const nextExpected = text === "{" ? "}" : text === "(" ? ")" : text === "[" ? "]" : "";
    if (nextExpected) stack.push(nextExpected);
    else if (text === stack.at(-1)) {
      stack.pop();
      if (stack.length === 0) return index;
    }
  }
  return -1;
}

function unquote(value: string): string {
  const delimiter = value.startsWith("'''") || value.startsWith('"""') ? 3 : 1;
  return value.length >= delimiter * 2 ? value.slice(delimiter, -delimiter) : value;
}

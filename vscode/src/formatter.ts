import { Token, tokenize } from "./lexer.js";

export interface FormatterOptions {
  readonly insertSpaces: boolean;
  readonly tabSize: number;
  readonly printWidth?: number;
}

type GroupKind = "curly" | "paren" | "bracket";
type Node = Token | GroupNode;

interface GroupNode {
  readonly kind: "group";
  readonly groupKind: GroupKind;
  readonly open: Token;
  readonly close?: Token;
  readonly children: readonly Node[];
  readonly lineBreakBefore: boolean;
}

interface Statement {
  readonly nodes: readonly Node[];
  readonly blankBefore: boolean;
}

const PIPELINE_SECTIONS = new Set([
  "agent", "environment", "options", "parameters", "post", "stages", "tools", "triggers"
]);
const PIPELINE_BLOCKS = new Set(["pipeline"]);
const STAGE_BLOCKS = new Set(["agent", "steps", "post", "when", "parallel", "matrix", "environment", "options", "input", "tools"]);
const CONTROL_WORDS = new Set(["if", "for", "while", "switch", "catch", "synchronized"]);
const SHELL_STEPS = new Map<string, "sh" | "cmd" | "powershell">([
  ["sh", "sh"], ["bat", "cmd"], ["cmd", "cmd"], ["powershell", "powershell"], ["pwsh", "powershell"]
]);

export function formatJenkinsfile(source: string, options: FormatterOptions = { insertSpaces: true, tabSize: 4 }): string {
  if (source.length > 250_000) return source;
  const newline = source.includes("\r\n") ? "\r\n" : "\n";
  const tokens = tokenize(source);
  const stack: string[] = [];
  for (const token of tokens) {
    if (token.terminated === false) return source;
    if (token.kind !== "punctuation") continue;
    const open = openingKind(token.text);
    if (open) stack.push(closingFor(open));
    else if (isClosing(token.text) && stack.pop() !== token.text) return source;
  }
  if (stack.length > 0) return source;
  const parsed = parseGroups(tokens);
  const safeTabSize = Math.max(1, Math.min(8, Math.trunc(options.tabSize) || 4));
  const printWidth = Math.max(60, Math.min(200, Math.trunc(options.printWidth ?? 100) || 100));
  const formatOptions = { ...options, tabSize: safeTabSize, printWidth, newline };
  let formatted = formatBlock(parsed, 0, "", formatOptions);
  formatted = formatted.replace(/^(?:\r?\n)+|(?:\r?\n)+$/g, "");
  return formatted ? formatted + newline : "";
}

function parseGroups(tokens: readonly Token[]): Node[] {
  const result: Node[] = [];
  let index = 0;

  while (index < tokens.length) {
    const current = tokens[index]!;
    const kind = openingKind(current.text);
    if (!kind) {
      result.push(current);
      index++;
      continue;
    }

    const expected = closingFor(kind);
    const nested: Node[] = [];
    const open = current;
    index++;
    let close: Token | undefined;
    while (index < tokens.length) {
      const next = tokens[index]!;
      if (next.text === expected) {
        close = next;
        index++;
        break;
      }
      const childKind = openingKind(next.text);
      if (childKind) {
        const childResult = parseOneGroup(tokens, index, childKind);
        nested.push(childResult.group);
        index = childResult.nextIndex;
      } else {
        nested.push(next);
        index++;
      }
    }
    result.push({
      kind: "group",
      groupKind: kind,
      open,
      ...(close ? { close } : {}),
      children: nested,
      lineBreakBefore: open.lineBreakBefore
    });
  }
  return result;
}

function parseOneGroup(tokens: readonly Token[], index: number, kind: GroupKind): { group: GroupNode; nextIndex: number } {
  const open = tokens[index]!;
  const expected = closingFor(kind);
  const children: Node[] = [];
  let cursor = index + 1;
  let close: Token | undefined;

  while (cursor < tokens.length) {
    const current = tokens[cursor]!;
    if (current.text === expected) {
      close = current;
      cursor++;
      break;
    }
    if (isClosing(current.text)) {
      children.push(current);
      cursor++;
      continue;
    }
    const childKind = openingKind(current.text);
    if (childKind) {
      const child = parseOneGroup(tokens, cursor, childKind);
      children.push(child.group);
      cursor = child.nextIndex;
    } else {
      children.push(current);
      cursor++;
    }
  }

  return {
    group: {
      kind: "group",
      groupKind: kind,
      open,
      ...(close ? { close } : {}),
      children,
      lineBreakBefore: open.lineBreakBefore
    },
    nextIndex: cursor
  };
}

function formatBlock(nodes: readonly Node[], level: number, owner: string, options: FormatterOptions & { newline: string }): string {
  const statements = collectStatements(nodes, owner);
  const lines: string[] = [];
  let previousMultiline = false;

  for (const statement of statements) {
    const rendered = renderStatement(statement.nodes, level, options);
    if (!rendered) continue;
    const multiline = rendered.includes("\n");
    if ((statement.blankBefore || (owner === "parameters" && (previousMultiline || multiline)))
      && lines.length > 0 && lines.at(-1) !== "") lines.push("");
    lines.push(indent(level, options) + rendered);
    previousMultiline = multiline;
  }
  return lines.join(options.newline);
}

function collectStatements(nodes: readonly Node[], owner: string): Statement[] {
  const statements: Statement[] = [];
  let current: Node[] = [];
  let pendingBlank = false;

  const flush = (): void => {
    const meaningful = trimSemicolonOnly(current);
    if (meaningful.length > 0) {
      statements.push({ nodes: meaningful, blankBefore: pendingBlank });
      pendingBlank = false;
    }
    current = [];
  };

  for (let index = 0; index < nodes.length; index++) {
    const node = nodes[index]!;
    const boundary = semanticBoundaryKind(node, owner);
    if (boundary && (statements.length > 0 || current.length > 0)) {
      flush();
      if (boundary === "blank") pendingBlank = true;
    } else if (node.lineBreakBefore && current.length > 0 && !isContinuation(current.at(-1)!, node)) {
      flush();
    }

    if (isToken(node) && node.text === ";") {
      const first = current[0];
      if (current.length === 2 && isToken(first) && ["if", "for", "while"].includes(first.text)
        && isGroup(current[1]) && current[1].groupKind === "paren") current.push(node);
      // A trailing comment belongs to this statement, not to the following line.
      const next = nodes[index + 1];
      if (!(isToken(next) && next.kind === "line-comment" && !next.lineBreakBefore)) flush();
      continue;
    }

    current.push(node);
    const next = nodes[index + 1];
    if (isGroup(node) && node.groupKind === "curly" && !isChainContinuation(next)
      && !(isToken(next) && next.kind === "line-comment" && !next.lineBreakBefore)) flush();
    if (isToken(node) && node.kind === "line-comment") flush();
  }
  flush();
  return statements;
}

function semanticBoundaryKind(node: Node, owner: string): false | "line" | "blank" {
  if (!isToken(node) || node.kind !== "word") return false;
  const word = node.text.toLowerCase();
  if (PIPELINE_BLOCKS.has(owner.toLowerCase()) && PIPELINE_SECTIONS.has(word)) return "blank";
  if ((owner.toLowerCase() === "stages" || owner.toLowerCase() === "parallel") && word === "stage") return "blank";
  if (owner.toLowerCase() === "stage" && STAGE_BLOCKS.has(word)) return "blank";
  return false;
}

function isContinuation(previous: Node, current: Node): boolean {
  if (isChainContinuation(current)) return true;
  if (isToken(previous) && previous.kind === "operator" && !["++", "--"].includes(previous.text)) return true;
  if (isToken(previous) && [",", "."].includes(previous.text)) return true;
  if (isToken(current) && current.kind === "operator" && !["++", "--"].includes(current.text)) return true;
  if (isGroup(previous) && previous.groupKind === "paren" && isToken(current) && current.text === ".") return true;
  return false;
}

function isChainContinuation(node: Node | undefined): boolean {
  return isToken(node) && ["else", "catch", "finally"].includes(node.text);
}

function renderStatement(nodes: readonly Node[], level: number, options: FormatterOptions & { newline: string }): string {
  let result = "";
  let inline: Node[] = [];

  const appendInline = (): void => {
    if (inline.length === 0) return;
    const value = renderInline(inline, level, options);
    if (value) {
      if (result && !/[ \t]$/.test(result) && !/^[,.;)\]}]/.test(value)) result += " ";
      result += value;
    }
    inline = [];
  };

  for (const node of nodes) {
    if (isGroup(node) && node.groupKind === "curly") {
      appendInline();
      const owner = lastWord(nodesBeforeGroup(nodes, node));
      const block = renderCurly(node, level, owner, options);
      if (result && !result.endsWith(" ")) result += " ";
      result += block;
    } else {
      inline.push(node);
    }
  }
  appendInline();
  return result;
}

function nodesBeforeGroup(nodes: readonly Node[], target: GroupNode): Node[] {
  const index = nodes.indexOf(target);
  return index < 0 ? [] : nodes.slice(0, index);
}

function renderCurly(group: GroupNode, level: number, owner: string, options: FormatterOptions & { newline: string }): string {
  if (group.children.length === 0) return group.close ? "{}" : "{";
  const body = formatBlock(group.children, level + 1, owner, options);
  const close = group.close ? `${options.newline}${indent(level, options)}}` : "";
  return `{${options.newline}${body}${close}`;
}

function renderInline(nodes: readonly Node[], level: number, options: FormatterOptions & { newline: string }, shellMode?: "sh" | "cmd" | "powershell"): string {
  let result = "";
  let previous: Node | undefined;
  let beforePrevious: Node | undefined;

  for (let index = 0; index < nodes.length; index++) {
    const node = nodes[index]!;
    if (isToken(node) && (node.kind === "line-comment" || node.kind === "block-comment")) {
      if (result && !result.endsWith(" ")) result += " ";
      result += node.text;
      if (node.kind === "line-comment") break;
      previous = node;
      continue;
    }

    const previousWord = previous ? lastWord([previous]) : "";
    const next = isGroup(node)
      ? renderGroup(node, level, previousWord, options)
      : renderLiteral(node, previous, nodes[index + 1], level, options,
        shellMode && (!previous || (isToken(previous) && previous.text === ":"
          && isToken(beforePrevious) && beforePrevious.text === "script")) ? shellMode : undefined);
    const separator = previous ? spacingBetween(previous, node, beforePrevious) : "";
    result += separator + next;
    beforePrevious = previous;
    previous = node;
  }
  return result;
}

function renderGroup(group: GroupNode, level: number, previousWord: string, options: FormatterOptions & { newline: string }): string {
  if (group.groupKind === "curly") return renderCurly(group, level, previousWord, options);
  const close = group.close?.text ?? "";
  const open = group.open.text;
  const items = splitArguments(group.children);
  const shellMode = SHELL_STEPS.get(previousWord.toLowerCase());
  const inline = renderInline(group.children, level, options, shellMode);
  const namedArguments = items.filter((item) => containsNamedArgument(item)).length;
  const hasComments = group.children.some((node) => isToken(node) && node.kind === "line-comment");
  const multiline = hasComments || inline.includes("\n") || (items.length > 1
    && ((group.groupKind === "paren" && namedArguments >= 3)
      || indent(level, options).length + previousWord.length + inline.length + 2 > (options.printWidth ?? 100)));
  if (!multiline) return `${open}${inline}${close}`;

  const body = items.map((item, index) => renderArgument(item, level + 1, index < items.length - 1, options, shellMode))
    .join(`${options.newline}${indent(level + 1, options)}`);
  return `${open}${options.newline}${indent(level + 1, options)}${body}${options.newline}${indent(level, options)}${close}`;
}

function renderArgument(nodes: readonly Node[], level: number, comma: boolean, options: FormatterOptions & { newline: string }, shellMode?: "sh" | "cmd" | "powershell"): string {
  const remaining = [...nodes];
  const leading: string[] = [];
  while (true) {
    const first = remaining[0];
    if (!isToken(first) || first.kind !== "line-comment") break;
    leading.push(first.text.trimEnd());
    remaining.shift();
  }
  const trailing = remaining.at(-1);
  const comment = isToken(trailing) && trailing.kind === "line-comment" ? trailing.text.trimEnd() : "";
  if (comment) remaining.pop();
  const content = renderInline(remaining, level, options, shellMode);
  return [...leading, content + (comma ? "," : "") + (comment ? ` ${comment}` : "")]
    .join(options.newline + indent(level, options));
}

function renderLiteral(token: Token, previous: Node | undefined, next: Node | undefined, level: number, options: FormatterOptions & { newline: string }, explicitMode?: "sh" | "cmd" | "powershell"): string {
  if (token.kind !== "string") return token.text;
  const mode = explicitMode ?? (isToken(previous) && previous.kind === "word" ? SHELL_STEPS.get(previous.text.toLowerCase()) : undefined);
  return mode && isStandaloneShellLiteral(next)
    ? formatShellLiteral(token.text, mode, level, options) ?? token.text
    : token.text;
}

function isStandaloneShellLiteral(next: Node | undefined): boolean {
  if (!next) return true;
  if (next.lineBreakBefore) return !(isToken(next) && (next.kind === "operator" || next.text === "."));
  if (isGroup(next)) return false;
  if (next.kind === "line-comment" || next.kind === "block-comment") return true;
  return [",", ";", ")", "]", "}"].includes(next.text);
}

function spacingBetween(previous: Node, current: Node, beforePrevious?: Node): string {
  const left = nodeText(previous);
  const right = nodeText(current);
  if (right === "," || right === ";" || right === "." || right === ":") return "";
  if (left === "." || left === "@") return "";
  if (["?.", "*.", ".&", "::", "..", "..<"].includes(left) || ["?.", "*.", ".&", "::", "..", "..<"].includes(right)) return "";
  if (["++", "--"].includes(right)) return "";
  if (["++", "--"].includes(left)) return isToken(current) && current.kind === "operator" ? " " : "";
  if (left === "," || left === ";" || left === ":") return " ";

  if (isGroup(current)) {
    if (current.groupKind === "paren") {
      if (isToken(previous) && (CONTROL_WORDS.has(previous.text) || ["return", "throw", "assert"].includes(previous.text))) return " ";
      if (isToken(previous) && previous.kind === "operator") return isPrefixOperator(previous.text, beforePrevious) ? "" : " ";
      return "";
    }
    if (current.groupKind === "bracket") {
      if (isToken(previous) && (previous.kind === "operator" || ["return", "throw", "in", "case", "assert"].includes(previous.text))) return " ";
      return "";
    }
    return " ";
  }
  if (isGroup(previous)) {
    if (current === undefined) return "";
    return isToken(current) && [".", ")", "]", "}", ",", ";"].includes(current.text) ? "" : " ";
  }
  if (!isToken(previous) || !isToken(current)) return " ";
  if (previous.kind === "operator") return isPrefixOperator(previous.text, beforePrevious) ? "" : " ";
  if (current.kind === "operator") return isPrefixOperator(current.text, previous) ? "" : " ";
  if (left === "." || right === ".") return "";
  return " ";
}

function isPrefixOperator(operator: string, previous?: Node): boolean {
  if (!["!", "~", "+", "-"].includes(operator)) return false;
  if (!previous) return true;
  if (!isToken(previous)) return false;
  if (previous.kind === "operator") return !["++", "--"].includes(previous.text);
  return ["(", "[", "{", ",", ":", ";"].includes(previous.text);
}

function splitArguments(nodes: readonly Node[]): Node[][] {
  const result: Node[][] = [[]];
  for (const node of nodes) {
    if (isToken(node) && node.text === ",") result.push([]);
    else if (isToken(node) && node.kind === "line-comment" && !node.lineBreakBefore
      && result.length > 1 && result.at(-1)!.length === 0) result[result.length - 2]!.push(node);
    else result.at(-1)!.push(node);
  }
  return result.filter((item) => item.length > 0);
}

function containsNamedArgument(nodes: readonly Node[]): boolean {
  for (let index = 0; index < nodes.length - 1; index++) {
    const key = nodes[index]!;
    const separator = nodes[index + 1]!;
    if (isToken(key) && isToken(separator) && key.kind === "word" && separator.text === ":") return true;
  }
  return false;
}

function nodeWidth(node: Node): number {
  if (isToken(node)) return node.text.length;
  return 2 + node.children.reduce((total, child) => total + nodeWidth(child), 0);
}

function nodeText(node: Node): string {
  if (isToken(node)) return node.text;
  return node.open.text;
}

function lastWord(nodes: readonly Node[]): string {
  for (let index = nodes.length - 1; index >= 0; index--) {
    const node = nodes[index]!;
    if (isToken(node) && node.kind === "word") return node.text;
  }
  return "";
}

function trimSemicolonOnly(nodes: readonly Node[]): readonly Node[] {
  return nodes.length === 1 && isToken(nodes[0]!) && nodes[0]!.text === ";" ? [] : nodes;
}

function openingKind(text: string): GroupKind | undefined {
  if (text === "{") return "curly";
  if (text === "(") return "paren";
  if (text === "[") return "bracket";
  return undefined;
}

function closingFor(kind: GroupKind): string {
  return kind === "curly" ? "}" : kind === "paren" ? ")" : "]";
}

function isClosing(text: string): boolean {
  return text === "}" || text === ")" || text === "]";
}

function isToken(node: Node | undefined): node is Token {
  return node !== undefined && !("groupKind" in node);
}

function isGroup(node: Node | undefined): node is GroupNode {
  return node !== undefined && "groupKind" in node;
}

function indent(level: number, options: FormatterOptions): string {
  return options.insertSpaces ? " ".repeat(level * options.tabSize) : "\t".repeat(level);
}

function formatShellLiteral(literal: string, mode: "sh" | "cmd" | "powershell", level: number, options: FormatterOptions & { newline: string }): string | undefined {
  const quote = literal[0];
  if (!quote || !["'", '"'].includes(quote)) return undefined;
  const delimiter = quote.repeat(literal.startsWith(quote.repeat(3)) ? 3 : 1);
  if (!literal.endsWith(delimiter) || literal.length < delimiter.length * 2) return undefined;
  let body = literal.slice(delimiter.length, -delimiter.length);
  if (body.trimStart().startsWith("#!") || body.includes("<<") || body.includes(delimiter)) return undefined;
  if (mode === "powershell" && (body.includes("<#") || body.includes("#>"))) return undefined;
  if (mode === "cmd" && body.split(/\r\n|\r|\n/).some((line) => /^\s*(?:::|rem(?:\s|$))/i.test(line))) return undefined;
  if (mode === "sh" && containsControlFlow(body, /^(?:if|then|else|elif|fi|for|select|while|until|do|done|case|esac|function|coproc)\b/i)) return undefined;
  if (mode === "cmd" && containsControlFlow(body, /^(?:if|for|goto|call|exit)\b/i)) return undefined;
  if (mode === "powershell" && containsControlFlow(body, /^(?:begin|break|catch|class|continue|do|else|elseif|end|exit|finally|for|foreach|function|if|param|process|return|switch|throw|trap|try|while)\b/i)) return undefined;
  if (body.includes("#")) return undefined;
  if (mode === "sh" && /\$\(\(|\[\[|\]\]|\(\(|[{}]/.test(body)) return undefined;
  if (mode === "cmd" && /[()<>]/.test(body)) return undefined;
  if (mode === "powershell" && /[{}]/.test(body)) return undefined;

  const commands = splitScript(body, mode);
  if (!commands || commands.length === 0) return undefined;
  const bodyIndent = indent(level + 1, options);
  const deeperIndent = indent(level + 2, options);
  const formatted: string[] = [];
  for (const entry of commands) {
    const wrapped = wrapCommand(entry.command, mode, bodyIndent, deeperIndent, options.printWidth ?? 100);
    if (!wrapped) return undefined;
    const last = wrapped.length - 1;
    wrapped[last] += entry.operator ? ` ${entry.operator} ${continuationMarker(mode)}` : "";
    formatted.push(...wrapped);
  }

  if (formatted.length === 0) return undefined;
  if (delimiter.length === 1 && formatted.length === 1
    && indent(level, options).length + literal.length + 12 <= (options.printWidth ?? 100)) return literal;
  const triple = quote.repeat(3);
  return `${triple}${options.newline}${bodyIndent}${formatted.join(`${options.newline}${bodyIndent}`)}${options.newline}${indent(level, options)}${triple}`;
}

interface ScriptCommand {
  readonly command: string;
  readonly operator: string;
}

function splitScript(body: string, mode: "sh" | "cmd" | "powershell"): ScriptCommand[] | undefined {
  const physicalLines = body.split(/\r\n|\r|\n/).map((line) => line.trim()).filter((line) => line.length > 0);
  if (physicalLines.length === 0) return undefined;
  const logicalLines: string[] = [];
  let pending = "";
  for (const line of physicalLines) {
    const marker = mode === "sh" ? "\\\\" : mode === "cmd" ? "^" : "`";
    const trimmedEnd = line.trimEnd();
    if (trimmedEnd.endsWith(marker)) {
      pending += `${trimmedEnd.slice(0, -marker.length).trimEnd()} `;
      continue;
    }
    logicalLines.push(`${pending}${trimmedEnd}`.trim());
    pending = "";
  }
  if (pending.trim()) return undefined;

  const result: ScriptCommand[] = [];
  for (const line of logicalLines) {
    const segments = splitLogicalLine(line, mode);
    if (!segments) return undefined;
    result.push(...segments);
  }
  return result;
}

function containsControlFlow(body: string, commandPattern: RegExp): boolean {
  return body.split(/\r\n|\r|\n|;|&&|\|\||(?<!\|)\|/).some((command) => commandPattern.test(command.trim()));
}

function splitLogicalLine(line: string, mode: "sh" | "cmd" | "powershell"): ScriptCommand[] | undefined {
  const operators = mode === "sh" ? ["&&", "||", "|", ";"] : mode === "cmd" ? ["&&", "||", "&"] : [";"];
  const commands: ScriptCommand[] = [];
  let quote = "";
  let parentheses = 0;
  let parameterDepth = 0;
  let start = 0;

  for (let index = 0; index < line.length; index++) {
    const character = line[index]!;
    if (quote) {
      if (character === quote && !isEscaped(line, index)) quote = "";
      else if (character === "\\") index++;
      continue;
    }
    if (character === "\\" && mode === "sh") {
      index++;
      continue;
    }
    if (character === "'" || character === '"' || (character === "`" && mode !== "powershell")) {
      quote = character;
      continue;
    }
    if (character === "$" && line[index + 1] === "{" && mode === "sh") {
      parameterDepth++;
      index++;
      continue;
    }
    if (character === "}" && parameterDepth > 0) {
      parameterDepth--;
      continue;
    }
    if (character === "(" && mode === "sh") parentheses++;
    else if (character === ")" && parentheses > 0) parentheses--;
    if (parentheses > 0 || parameterDepth > 0) continue;

    const operator = operators.find((candidate) => line.startsWith(candidate, index));
    if (!operator) continue;
    if (operator === "&" && /[<>]/.test(line[index - 1] ?? "")) continue;
    if (operator === "&" && line[index - 1] === "^") continue;
    const command = line.slice(start, index).trim();
    if (!command) return undefined;
    commands.push({ command, operator: operator === ";" ? "" : operator });
    index += operator.length - 1;
    start = index + 1;
  }

  if (quote || parentheses > 0 || parameterDepth > 0) return undefined;
  const remaining = line.slice(start).trim();
  if (!remaining && commands.length > 0) return undefined;
  if (remaining) commands.push({ command: remaining, operator: "" });
  return commands.length ? commands : undefined;
}

function wrapCommand(command: string, mode: "sh" | "cmd" | "powershell", bodyIndent: string, deeperIndent: string, printWidth: number): string[] | undefined {
  const width = Math.max(30, printWidth - deeperIndent.length - 3);
  if (bodyIndent.length + command.length <= printWidth) return [command];
  if (command.includes("#")) return [command];
  const boundaries = findWordBoundaries(command);
  if (boundaries.length === 0) return [command];
  const lines: string[] = [];
  const continuationIndent = deeperIndent.slice(bodyIndent.length);
  const namedOptions = boundaries.filter((offset) => command.startsWith("--", offset + 1));
  const wrapEachOption = namedOptions.length > 1;
  let start = 0;
  while (command.length - start > width || (wrapEachOption && namedOptions.some((offset) => offset > start))) {
    const limit = start + width;
    const candidates = boundaries.filter((offset) => offset > start && offset <= limit);
    const nextOption = boundaries.find((offset) => offset > start && command.startsWith("--", offset + 1));
    const split = wrapEachOption && nextOption !== undefined
      ? nextOption <= limit ? nextOption : candidates.at(-1) ?? nextOption
      : nextOption !== undefined && nextOption <= limit ? nextOption : candidates.at(-1);
    if (split === undefined) return [command];
    const line = command.slice(start, split).trimEnd();
    if (!line) return undefined;
    lines.push(`${lines.length === 0 ? "" : continuationIndent}${line} ${continuationMarker(mode)}`);
    start = split + 1;
    while (command[start] === " ") start++;
  }
  const remainder = command.slice(start).trim();
  if (!remainder) return undefined;
  if (lines.length > 0) lines.push(`${continuationIndent}${remainder}`);
  else lines.push(remainder);
  return lines;
}

function findWordBoundaries(command: string): number[] {
  const result: number[] = [];
  let quote = "";
  for (let index = 0; index < command.length; index++) {
    const character = command[index]!;
    if (quote) {
      if (character === quote && !isEscaped(command, index)) quote = "";
      else if (character === "\\") index++;
      continue;
    }
    if (character === "'" || character === '"' || character === "`") quote = character;
    else if (character === " ") result.push(index);
  }
  return result;
}

function continuationMarker(mode: "sh" | "cmd" | "powershell"): string {
  return mode === "sh" ? "\\\\" : mode === "cmd" ? "^" : "`";
}

function isEscaped(source: string, offset: number): boolean {
  let count = 0;
  for (let index = offset - 1; index >= 0 && source[index] === "\\"; index--) count++;
  return (count & 1) === 1;
}

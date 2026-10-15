export type TokenKind = "word" | "number" | "string" | "line-comment" | "block-comment" | "operator" | "punctuation";

export interface Token {
  readonly kind: TokenKind;
  readonly text: string;
  readonly start: number;
  readonly end: number;
  readonly lineBreakBefore: boolean;
  readonly terminated?: boolean;
}

const OPERATORS = [
  "<=>", "==~", "!#", "<<=", ">>=", "**=", "==", "!=", "<=", ">=", "=~", "..<", "..",
  "?.", "*.", ".&", "::", "->", "++", "--", ">>>", "<<", ">>", "**", "?:", "&&", "||",
  "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "?=", "?", "+", "-", "*", "/", "%",
  "=", "<", ">", "!", "~", "&", "|", "^", "\\"
].sort((left, right) => right.length - left.length);

const PUNCTUATION = new Set(["{", "}", "(", ")", "[", "]", ",", ":", ";", ".", "@"]);

export function tokenize(source: string): Token[] {
  const tokens: Token[] = [];
  let index = 0;
  let lineBreakBefore = false;

  while (index < source.length) {
    const character = source[index]!;
    if (/\s/.test(character)) {
      if (character === "\n" || character === "\r") lineBreakBefore = true;
      index++;
      continue;
    }

    const start = index;
    let kind: TokenKind;
    let end: number;
    let terminated: boolean | undefined;

    if (character === "/" && source[index + 1] === "/") {
      kind = "line-comment";
      end = index + 2;
      while (end < source.length && source[end] !== "\n" && source[end] !== "\r") end++;
    } else if (character === "/" && source[index + 1] === "*") {
      kind = "block-comment";
      end = scanBlockComment(source, index + 2);
      terminated = end <= source.length && source.slice(end - 2, end) === "*/";
    } else if (character === "\u0024" && source[index + 1] === "/") {
      kind = "string";
      end = scanDollarSlashy(source, index + 2);
      terminated = source.slice(end - 2, end) === "/\u0024";
    } else if (character === "'" || character === '"' || character === "`") {
      kind = "string";
      const triple = source[index + 1] === character && source[index + 2] === character;
      const width = triple ? 3 : 1;
      const result = scanQuoted(source, index + width, character, triple);
      end = result.end;
      terminated = result.terminated;
    } else if (character === "/" && isRegexStart(tokens)) {
      const regexEnd = scanSlashy(source, index + 1);
      if (regexEnd > index + 1) {
        kind = "string";
        end = regexEnd;
        terminated = source[regexEnd - 1] === "/";
      } else {
        kind = "operator";
        end = index + 1;
      }
    } else if (/[A-Za-z_$]/.test(character)) {
      kind = "word";
      end = index + 1;
      while (end < source.length && /[A-Za-z0-9_$]/.test(source[end]!)) end++;
    } else if (/\d/.test(character)) {
      kind = "number";
      end = index + 1;
      while (end < source.length && /[A-Za-z0-9_.]/.test(source[end]!)) end++;
    } else if (PUNCTUATION.has(character)) {
      kind = "punctuation";
      end = index + 1;
    } else {
      kind = "operator";
      end = OPERATORS.find((operator) => source.startsWith(operator, index))?.length
        ? index + OPERATORS.find((operator) => source.startsWith(operator, index))!.length
        : index + 1;
    }

    tokens.push({
      kind,
      text: source.slice(start, end),
      start,
      end,
      lineBreakBefore,
      ...(terminated === undefined ? {} : { terminated })
    });
    lineBreakBefore = false;
    index = end;
  }
  return tokens;
}

function scanQuoted(source: string, offset: number, quote: string, triple: boolean): { end: number; terminated: boolean } {
  let index = offset;
  while (index < source.length) {
    if (source[index] === "\\") {
      index += Math.min(2, source.length - index);
      continue;
    }
    if (source[index] === quote) {
      if (!triple) return { end: index + 1, terminated: true };
      if (source[index + 1] === quote && source[index + 2] === quote) return { end: index + 3, terminated: true };
    }
    index++;
  }
  return { end: source.length, terminated: false };
}

function scanBlockComment(source: string, offset: number): number {
  let depth = 1;
  let index = offset;
  while (index < source.length - 1) {
    if (source.startsWith("/*", index)) {
      depth++;
      index += 2;
    } else if (source.startsWith("*/", index)) {
      depth--;
      index += 2;
      if (depth === 0) return index;
    } else {
      index++;
    }
  }
  return source.length;
}

function scanDollarSlashy(source: string, offset: number): number {
  const end = source.indexOf("/$", offset);
  return end < 0 ? source.length : end + 2;
}

function scanSlashy(source: string, offset: number): number {
  for (let index = offset; index < source.length; index++) {
    if (source[index] === "\\") index++;
    else if (source[index] === "/") return index + 1;
    else if (source[index] === "\n" || source[index] === "\r") return offset;
  }
  return offset;
}

function isRegexStart(tokens: readonly Token[]): boolean {
  const previous = tokens.at(-1);
  if (!previous) return true;
  if (previous.kind === "operator") return true;
  return previous.kind === "punctuation" && ["(", "[", "{", ",", ":", ";"].includes(previous.text);
}

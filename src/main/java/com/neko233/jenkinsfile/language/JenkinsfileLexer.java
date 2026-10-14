package com.neko233.jenkinsfile.language;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.neko233.jenkinsfile.language.JenkinsfileTokenTypes.*;

/** A lossless Groovy lexer with Jenkins Declarative Pipeline keywords. */
public final class JenkinsfileLexer extends LexerBase {
    private static final Set<String> GROOVY_KEYWORDS = Set.of(
            "as", "assert", "break", "case", "catch", "class", "const", "continue", "def",
            "default", "do", "else", "enum", "extends", "final", "finally", "for", "goto",
            "if", "implements", "import", "in", "instanceof", "interface", "new", "package",
            "return", "super", "switch", "this", "throw", "throws", "trait", "try", "while",
            "true", "false", "null", "void", "public", "protected", "private", "static",
            "abstract", "native", "synchronized", "transient", "volatile", "strictfp", "threadsafe"
    );
    private static final Set<String> PIPELINE_KEYWORDS = Set.of(
            "pipeline", "agent", "any", "none", "label", "node", "docker", "dockerfile", "kubernetes",
            "stages", "stage", "steps", "post", "always", "success", "failure", "unstable", "changed",
            "aborted", "unsuccessful", "cleanup", "regression", "fixed", "environment", "options",
            "parameters", "triggers", "tools", "input", "when", "branch", "expression", "not", "allOf",
            "anyOf", "matrix", "axes", "axis", "exclude", "excludes", "script", "parallel", "sequential",
            "beforeAgent", "beforeInput", "beforeOptions", "buildingTag", "changelog", "changeset",
            "changeRequest", "tag", "triggeredBy", "credentials"
    );
    private static final Set<String> PIPELINE_STEPS = Set.of(
            "addBadge", "archive", "archiveArtifacts", "bat", "booleanParam", "build", "catchError", "cmd",
            "checkout", "choice", "cleanWs", "configFileProvider", "credentials", "deleteDir", "dir",
            "disableConcurrentBuilds", "dockerNode", "echo", "error", "fileExists", "findFiles", "git",
            "httpRequest", "input", "isUnix", "jdk", "junit", "library", "libraryResource", "load",
            "lock", "mail", "maven", "milestone", "password", "pollSCM", "powershell", "properties",
            "pwd", "pwsh", "readFile", "readJSON", "readTrusted", "readYaml", "retry", "sh", "sleep",
            "sshagent", "stash", "step", "string", "text", "timestamps", "timeout", "tool", "unarchive",
            "unstable", "unstash", "upstream", "waitUntil", "warnError", "withContext", "withCredentials",
            "withEnv", "wrap", "ws", "writeFile", "zip", "unzip"
    );
    private static final String[] OPERATORS = {
            "<=>", "==~", "<..<", "<<=", ">>=", "**=", "==", "!=", "<=", ">=", "..<", "..", "?.", "*.",
            ".&", "::", "->", "++", "--", ">>>", "<<", ">>",
            "**", "=~", "?:", "&&", "||", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "?=",
            "?", "+", "-", "*", "/", "%", "=", "<", ">", "!", "~", "&", "|", "^"
    };

    private CharSequence buffer = "";
    private int bufferEnd;
    private int tokenStart;
    private int tokenEnd;
    private IElementType tokenType;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.bufferEnd = endOffset;
        this.tokenStart = startOffset;
        this.tokenEnd = startOffset;
        this.tokenType = null;
        advance();
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return tokenType;
    }

    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    @Override
    public void advance() {
        tokenStart = tokenEnd;
        if (tokenStart >= bufferEnd) {
            tokenType = null;
            return;
        }

        char c = buffer.charAt(tokenStart);
        if (Character.isWhitespace(c)) {
            tokenEnd = tokenStart + 1;
            while (tokenEnd < bufferEnd && Character.isWhitespace(buffer.charAt(tokenEnd))) tokenEnd++;
            tokenType = WHITE_SPACE;
            return;
        }

        if (c == '/' && tokenStart + 1 < bufferEnd && buffer.charAt(tokenStart + 1) == '/') {
            tokenEnd = tokenStart + 2;
            while (tokenEnd < bufferEnd && buffer.charAt(tokenEnd) != '\n' && buffer.charAt(tokenEnd) != '\r') tokenEnd++;
            tokenType = LINE_COMMENT;
            return;
        }
        if (c == '/' && tokenStart + 1 < bufferEnd && buffer.charAt(tokenStart + 1) == '*') {
            tokenEnd = scanBlockComment(tokenStart + 2);
            tokenType = BLOCK_COMMENT;
            return;
        }
        if (c == '$' && tokenStart + 1 < bufferEnd && buffer.charAt(tokenStart + 1) == '/') {
            tokenEnd = scanDollarSlashy(tokenStart + 2);
            tokenType = STRING;
            return;
        }
        if (c == '\'' || c == '"' || c == '`') {
            tokenEnd = scanQuotedString(tokenStart, c);
            tokenType = STRING;
            return;
        }
        if (c == '/' && isRegexStart(tokenStart)) {
            int end = scanSlashyString(tokenStart + 1);
            if (end > tokenStart + 1) {
                tokenEnd = end;
                tokenType = REGEX;
                return;
            }
        }
        if (Character.isJavaIdentifierStart(c) || c == '$') {
            tokenEnd = tokenStart + 1;
            while (tokenEnd < bufferEnd) {
                char next = buffer.charAt(tokenEnd);
                if (!Character.isJavaIdentifierPart(next) && next != '$') break;
                tokenEnd++;
            }
            String word = buffer.subSequence(tokenStart, tokenEnd).toString();
            if (isNamedArgument(tokenEnd)) {
                tokenType = NAMED_ARGUMENT;
            } else if (GROOVY_KEYWORDS.contains(word)) {
                tokenType = KEYWORD;
            } else if (PIPELINE_KEYWORDS.contains(word)) {
                tokenType = PIPELINE_KEYWORD;
            } else if (isConstant(word)) {
                tokenType = CONSTANT;
            } else if (isMethodCall(word, tokenEnd)) {
                tokenType = METHOD_CALL;
            } else {
                tokenType = IDENTIFIER;
            }
            return;
        }
        if (Character.isDigit(c)) {
            tokenEnd = scanNumber(tokenStart);
            tokenType = NUMBER;
            return;
        }

        switch (c) {
            case '{' -> tokenType = LEFT_BRACE;
            case '}' -> tokenType = RIGHT_BRACE;
            case '(' -> tokenType = LEFT_PAREN;
            case ')' -> tokenType = RIGHT_PAREN;
            case '[' -> tokenType = LEFT_BRACKET;
            case ']' -> tokenType = RIGHT_BRACKET;
            case ',' -> tokenType = COMMA;
            case ':' -> tokenType = COLON;
            case ';' -> tokenType = SEMICOLON;
            case '.' -> tokenType = DOT;
            case '@' -> tokenType = AT;
            default -> tokenType = isOperator(c) ? OPERATOR : BAD_CHARACTER;
        }
        if (tokenType == OPERATOR) tokenEnd = scanOperator(tokenStart);
        else tokenEnd = tokenStart + 1;
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return bufferEnd;
    }

    private int scanBlockComment(int offset) {
        int depth = 1;
        int i = offset;
        while (i < bufferEnd - 1) {
            if (buffer.charAt(i) == '/' && buffer.charAt(i + 1) == '*') {
                depth++;
                i += 2;
            } else if (buffer.charAt(i) == '*' && buffer.charAt(i + 1) == '/') {
                depth--;
                i += 2;
                if (depth == 0) return i;
            } else {
                i++;
            }
        }
        return bufferEnd;
    }

    private int scanQuotedString(int start, char quote) {
        boolean triple = start + 2 < bufferEnd && buffer.charAt(start + 1) == quote && buffer.charAt(start + 2) == quote;
        int delimiterLength = triple ? 3 : 1;
        int i = start + delimiterLength;
        while (i < bufferEnd) {
            char c = buffer.charAt(i);
            if (c == '\\') {
                i = Math.min(bufferEnd, i + 2);
                continue;
            }
            if (c == quote) {
                if (!triple) return i + 1;
                if (i + 2 < bufferEnd && buffer.charAt(i + 1) == quote && buffer.charAt(i + 2) == quote) return i + 3;
            }
            i++;
        }
        return bufferEnd;
    }

    private int scanDollarSlashy(int offset) {
        int i = offset;
        while (i < bufferEnd - 1) {
            if (buffer.charAt(i) == '/' && buffer.charAt(i + 1) == '$') return i + 2;
            i++;
        }
        return bufferEnd;
    }

    private int scanSlashyString(int offset) {
        int i = offset;
        while (i < bufferEnd) {
            char c = buffer.charAt(i);
            if (c == '\\') {
                i = Math.min(bufferEnd, i + 2);
                continue;
            }
            if (c == '/') return i + 1;
            if (c == '\n' || c == '\r') return -1;
            i++;
        }
        return -1;
    }

    private int scanNumber(int start) {
        int i = start;
        if (i + 1 < bufferEnd && buffer.charAt(i) == '0'
                && (buffer.charAt(i + 1) == 'x' || buffer.charAt(i + 1) == 'X'
                || buffer.charAt(i + 1) == 'b' || buffer.charAt(i + 1) == 'B')) {
            i += 2;
            while (i < bufferEnd && (Character.isLetterOrDigit(buffer.charAt(i)) || buffer.charAt(i) == '_')) i++;
            return i;
        }
        while (i < bufferEnd && (Character.isDigit(buffer.charAt(i)) || buffer.charAt(i) == '_')) i++;
        if (i < bufferEnd && buffer.charAt(i) == '.' && i + 1 < bufferEnd
                && Character.isDigit(buffer.charAt(i + 1)) && !(i + 1 < bufferEnd && buffer.charAt(i + 1) == '.')) {
            i++;
            while (i < bufferEnd && (Character.isDigit(buffer.charAt(i)) || buffer.charAt(i) == '_')) i++;
        }
        if (i < bufferEnd && (buffer.charAt(i) == 'e' || buffer.charAt(i) == 'E')) {
            int exponent = i + 1;
            if (exponent < bufferEnd && (buffer.charAt(exponent) == '+' || buffer.charAt(exponent) == '-')) exponent++;
            int digits = exponent;
            while (exponent < bufferEnd && (Character.isDigit(buffer.charAt(exponent)) || buffer.charAt(exponent) == '_')) exponent++;
            if (exponent > digits) i = exponent;
        }
        if (i < bufferEnd && "GgIiLlFfDd".indexOf(buffer.charAt(i)) >= 0) i++;
        return i;
    }

    private int scanOperator(int start) {
        for (String operator : OPERATORS) {
            if (operator.contains(" ")) continue;
            if (start + operator.length() <= bufferEnd && buffer.subSequence(start, start + operator.length()).toString().equals(operator)) {
                return start + operator.length();
            }
        }
        return start + 1;
    }

    private boolean isNamedArgument(int end) {
        int next = skipWhitespace(end);
        return next < bufferEnd && buffer.charAt(next) == ':';
    }

    private boolean isMethodCall(String word, int end) {
        int next = skipWhitespace(end);
        if (next >= bufferEnd) return false;
        if (buffer.charAt(next) == '(') return true;
        return PIPELINE_STEPS.contains(word) && buffer.charAt(next) != ':'
                && buffer.charAt(next) != ',' && buffer.charAt(next) != ')' && buffer.charAt(next) != '}';
    }

    private int skipWhitespace(int offset) {
        int i = offset;
        while (i < bufferEnd && Character.isWhitespace(buffer.charAt(i))) i++;
        return i;
    }

    private static boolean isConstant(String word) {
        boolean hasLetter = false;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (!Character.isUpperCase(c)) return false;
            } else if (!Character.isDigit(c) && c != '_' && c != '$') {
                return false;
            }
        }
        return hasLetter;
    }

    private boolean isRegexStart(int offset) {
        int i = offset - 1;
        while (i >= 0 && Character.isWhitespace(buffer.charAt(i))) i--;
        if (i < 0) return true;
        char previous = buffer.charAt(i);
        if ("([{=,:;!&|?~".indexOf(previous) >= 0) return true;
        if (previous == '>' && i > 0 && buffer.charAt(i - 1) == '-') return true;
        int end = i + 1;
        while (i >= 0 && Character.isJavaIdentifierPart(buffer.charAt(i))) i--;
        String word = buffer.subSequence(i + 1, end).toString();
        return Set.of("return", "case", "assert", "in", "instanceof", "throw").contains(word);
    }

    private static boolean isOperator(char c) {
        return "+-*/%=&|^!?~<>".indexOf(c) >= 0;
    }
}

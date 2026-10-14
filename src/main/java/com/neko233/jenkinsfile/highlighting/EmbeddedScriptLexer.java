package com.neko233.jenkinsfile.highlighting;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Highlights common command syntax inside Jenkins Pipeline script literals. */
final class EmbeddedScriptLexer {
    enum Mode { POSIX, CMD, POWERSHELL }
    enum Kind { COMMAND, KEYWORD, VARIABLE, PARAMETER, STRING, COMMENT, OPERATOR, NUMBER }
    record Segment(int start, int end, @NotNull Kind kind) { }

    private static final Set<String> POSIX_KEYWORDS = Set.of(
            "if", "then", "else", "elif", "fi", "case", "esac", "for", "select", "while", "until",
            "do", "done", "function", "in", "time", "coproc", "break", "continue", "return", "export"
    );
    private static final Set<String> CMD_KEYWORDS = Set.of(
            "if", "else", "for", "in", "do", "goto", "call", "set", "setlocal", "endlocal", "exit",
            "defined", "errorlevel", "exist", "not", "equ", "neq", "lss", "leq", "gtr", "geq"
    );
    private static final Set<String> POWERSHELL_KEYWORDS = Set.of(
            "begin", "break", "catch", "class", "continue", "data", "do", "dynamicparam", "else", "elseif",
            "end", "exit", "filter", "finally", "for", "foreach", "from", "function", "if", "in", "param",
            "process", "return", "switch", "throw", "trap", "try", "until", "using", "while", "workflow"
    );
    private static final Set<String> POWERSHELL_OPERATORS = Set.of(
            "-and", "-or", "-not", "-band", "-bor", "-bxor", "-eq", "-ne", "-gt", "-ge", "-lt", "-le",
            "-like", "-notlike", "-match", "-notmatch", "-contains", "-notcontains", "-in", "-notin",
            "-replace", "-is", "-isnot", "-as", "-join", "-split", "-f", "-shl", "-shr"
    );

    private final String source;
    private final Mode mode;
    private final int baseOffset;
    private final int depth;
    private final List<Segment> segments = new ArrayList<>();
    private boolean commandExpected = true;
    private boolean assignmentName;
    private boolean assignmentValue;

    private EmbeddedScriptLexer(String source, Mode mode, int baseOffset, int depth) {
        this.source = source;
        this.mode = mode;
        this.baseOffset = baseOffset;
        this.depth = depth;
    }

    static @NotNull List<Segment> lex(@NotNull String source, @NotNull Mode mode, int baseOffset) {
        return new EmbeddedScriptLexer(source, mode, baseOffset, 0).scan();
    }

    private List<Segment> scan() {
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                if (c == '\n' || c == '\r') commandExpected = true;
                i++;
                continue;
            }

            int commentEnd = commentEnd(i);
            if (commentEnd > i) {
                add(i, commentEnd, Kind.COMMENT);
                i = commentEnd;
                continue;
            }

            if (mode == Mode.POWERSHELL && source.startsWith("<#", i)) {
                int end = source.indexOf("#>", i + 2);
                end = end < 0 ? source.length() : end + 2;
                add(i, end, Kind.COMMENT);
                i = end;
                continue;
            }

            int hereStringEnd = mode == Mode.POWERSHELL ? hereStringEnd(i) : -1;
            if (hereStringEnd > i) {
                add(i, hereStringEnd, Kind.STRING);
                i = hereStringEnd;
                consumeAssignmentValue();
                continue;
            }

            if (c == '\'' || c == '"') {
                int end = quotedEnd(i, c);
                add(i, end, Kind.STRING);
                scanInterpolations(i + 1, closingQuoteStart(i, end, c));
                i = end;
                consumeAssignmentValue();
                continue;
            }

            int variableEnd = variableEnd(i);
            if (variableEnd > i) {
                scanVariable(i, variableEnd);
                i = variableEnd;
                consumeAssignmentValue();
                continue;
            }

            int operatorEnd = operatorEnd(i);
            if (operatorEnd > i) {
                String operator = source.substring(i, operatorEnd);
                add(i, operatorEnd, Kind.OPERATOR);
                if (operator.equals("=") && assignmentName && mode != Mode.CMD) {
                    assignmentValue = true;
                    assignmentName = false;
                } else if (startsCommandAfter(operator)) {
                    commandExpected = true;
                    assignmentName = false;
                    assignmentValue = false;
                }
                i = operatorEnd;
                continue;
            }

            if (Character.isDigit(c)) {
                int end = i + 1;
                while (end < source.length() && (Character.isDigit(source.charAt(end)) || source.charAt(end) == '.')) end++;
                add(i, end, Kind.NUMBER);
                i = end;
                consumeAssignmentValue();
                continue;
            }

            int end = wordEnd(i);
            if (end == i) {
                i++;
                continue;
            }
            String word = source.substring(i, end);
            String lower = word.toLowerCase(Locale.ROOT);
            if (isKeyword(lower)) {
                add(i, end, Kind.KEYWORD);
                commandExpected = isCommandIntroducer(lower);
                assignmentName = false;
                consumeAssignmentValue();
            } else if (mode == Mode.POWERSHELL && POWERSHELL_OPERATORS.contains(lower)) {
                add(i, end, Kind.OPERATOR);
                commandExpected = lower.equals("-and") || lower.equals("-or");
                assignmentName = false;
                consumeAssignmentValue();
            } else if (isParameter(word)) {
                add(i, end, Kind.PARAMETER);
                assignmentName = false;
                consumeAssignmentValue();
            } else if (isAssignmentName(word, end)) {
                add(i, end, Kind.VARIABLE);
                assignmentName = true;
            } else if (assignmentValue) {
                assignmentValue = false;
                assignmentName = false;
            } else if (commandExpected) {
                add(i, end, Kind.COMMAND);
                commandExpected = false;
                assignmentName = false;
            } else {
                assignmentName = false;
            }
            i = end;
        }
        return List.copyOf(segments);
    }

    private int commentEnd(int start) {
        if (mode == Mode.CMD) {
            if (!isFirstNonWhitespaceOnLine(start)) return -1;
            if (source.startsWith("::", start)) return lineEnd(start);
            if (source.regionMatches(true, start, "rem", 0, 3)
                    && (start + 3 == source.length() || Character.isWhitespace(source.charAt(start + 3)))) {
                return lineEnd(start);
            }
            return -1;
        }
        if (source.charAt(start) != '#') return -1;
        return lineEnd(start);
    }

    private int hereStringEnd(int start) {
        if (start + 2 >= source.length() || source.charAt(start) != '@'
                || (source.charAt(start + 1) != '\'' && source.charAt(start + 1) != '"')
                || (source.charAt(start + 2) != '\n' && source.charAt(start + 2) != '\r')) return -1;
        String closing = source.charAt(start + 1) == '\'' ? "'@" : "\"@";
        int lineStart = start + 2;
        while (lineStart < source.length()) {
            int first = lineStart;
            while (first < source.length() && (source.charAt(first) == ' ' || source.charAt(first) == '\t')) first++;
            if (source.startsWith(closing, first)) return lineEnd(first + 2);
            lineStart = lineEnd(lineStart);
            if (lineStart < source.length() && source.charAt(lineStart) == '\r') lineStart++;
            if (lineStart < source.length() && source.charAt(lineStart) == '\n') lineStart++;
        }
        return source.length();
    }

    private int quotedEnd(int start, char quote) {
        int i = start + 1;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (mode == Mode.POWERSHELL && quote == '\'' && c == '\'' && i + 1 < source.length() && source.charAt(i + 1) == '\'') {
                i += 2;
                continue;
            }
            if (c == quote) return i + 1;
            if (quote == '"' && isEscape(c) && i + 1 < source.length()) i += 2;
            else i++;
        }
        return source.length();
    }

    private boolean isEscape(char c) {
        return switch (mode) {
            case POSIX -> c == '\\';
            case CMD -> c == '^';
            case POWERSHELL -> c == '`';
        };
    }

    private int closingQuoteStart(int start, int end, char quote) {
        return end > start + 1 && source.charAt(end - 1) == quote ? end - 1 : end;
    }

    private void scanInterpolations(int start, int end) {
        if (mode == Mode.POSIX && source.charAt(start - 1) == '\'') return;
        if (mode == Mode.POWERSHELL && source.charAt(start - 1) == '\'') return;
        for (int i = start; i < end;) {
            int variableEnd = variableEnd(i);
            if (variableEnd > i) {
                scanVariable(i, variableEnd);
                i = variableEnd;
            } else {
                i++;
            }
        }
    }

    private int variableEnd(int start) {
        if (mode == Mode.CMD) {
            char c = source.charAt(start);
            if (c == '%' && start + 2 < source.length()) {
                if (source.charAt(start + 1) == '%') return Math.min(source.length(), start + 3);
                int close = source.indexOf('%', start + 1);
                if (close > start + 1) return close + 1;
            }
            if (c == '!' && start + 2 < source.length()) {
                int close = source.indexOf('!', start + 1);
                if (close > start + 1) return close + 1;
            }
            return -1;
        }
        if (source.charAt(start) != '$' || start + 1 >= source.length()) return -1;
        char next = source.charAt(start + 1);
        if ((mode == Mode.POSIX || mode == Mode.POWERSHELL) && next == '(') {
            int close = matchingParen(start + 1);
            return close < 0 ? source.length() : close + 1;
        }
        if (next == '{') {
            int close = source.indexOf('}', start + 2);
            return close < 0 ? source.length() : close + 1;
        }
        if ("?#!@$*^-".indexOf(next) >= 0 || Character.isDigit(next)) return start + 2;
        if (!Character.isLetter(next) && next != '_') return -1;
        int end = start + 2;
        while (end < source.length()) {
            char c = source.charAt(end);
            if (Character.isLetterOrDigit(c) || c == '_'
                    || (mode == Mode.POWERSHELL && c == ':')) end++;
            else break;
        }
        return end;
    }

    private int matchingParen(int open) {
        int nesting = 1;
        char quote = 0;
        for (int i = open + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == quote && (i == 0 || source.charAt(i - 1) != '\\')) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') quote = c;
            else if (c == '(') nesting++;
            else if (c == ')' && --nesting == 0) return i;
        }
        return -1;
    }

    private void scanVariable(int start, int end) {
        if (depth < 8 && source.charAt(start) == '$' && start + 1 < end && source.charAt(start + 1) == '(') {
            add(start, start + 2, Kind.OPERATOR);
            String nested = source.substring(start + 2, end - 1);
            segments.addAll(new EmbeddedScriptLexer(nested, mode, baseOffset + start + 2, depth + 1).scan());
            add(end - 1, end, Kind.OPERATOR);
        } else {
            add(start, end, Kind.VARIABLE);
        }
    }

    private int operatorEnd(int start) {
        String[] operators = switch (mode) {
            case POSIX -> new String[]{"&&", "||", "|&", ">>", "<<", ";;", ";&", ">|", "<>"};
            case CMD -> new String[]{"&&", "||", ">>", "<>", ">&", "<&"};
            case POWERSHELL -> new String[]{"&&", "||", "??", "?.", "::", "..", "++", "--", "+=", "-=", "*=", "/=", "==", "!=", "<=", ">=", "<<", ">>"};
        };
        for (String operator : operators) if (source.startsWith(operator, start)) return start + operator.length();
        char c = source.charAt(start);
        String singles = switch (mode) {
            case POSIX -> ";|&()<>!=+%";
            case CMD -> "&|()<>^";
            case POWERSHELL -> ";|&()<>!=+%*/";
        };
        if (singles.indexOf(c) >= 0) return start + 1;
        return -1;
    }

    private int wordEnd(int start) {
        int i = start;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c) || c == '\'' || c == '"' || operatorEnd(i) > i) break;
            if (mode == Mode.CMD && (c == '%' || c == '!')) break;
            if (mode != Mode.CMD && c == '$') break;
            i++;
        }
        return i;
    }

    private boolean isKeyword(String lower) {
        return switch (mode) {
            case POSIX -> POSIX_KEYWORDS.contains(lower);
            case CMD -> CMD_KEYWORDS.contains(lower);
            case POWERSHELL -> POWERSHELL_KEYWORDS.contains(lower);
        };
    }

    private boolean isCommandIntroducer(String lower) {
        return switch (mode) {
            case POSIX -> Set.of("then", "else", "elif", "do", "in", "if", "while", "until", "for", "case").contains(lower);
            case CMD -> Set.of("do", "else", "if", "for", "call", "goto", "setlocal").contains(lower);
            case POWERSHELL -> Set.of("if", "else", "elseif", "foreach", "for", "while", "do", "try", "catch", "finally", "throw").contains(lower);
        };
    }

    private boolean isParameter(String word) {
        return (mode != Mode.CMD && word.startsWith("-") && word.length() > 1)
                || (mode == Mode.CMD && word.startsWith("/") && word.length() > 1);
    }

    private boolean isAssignmentName(String word, int end) {
        if (mode == Mode.CMD || !commandExpected || end >= source.length() || source.charAt(end) != '=') return false;
        if (word.isEmpty() || !(Character.isLetter(word.charAt(0)) || word.charAt(0) == '_')) return false;
        for (int i = 1; i < word.length(); i++) {
            char c = word.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return false;
        }
        return true;
    }

    private boolean startsCommandAfter(String operator) {
        if (operator.equals("=") && assignmentName && mode != Mode.CMD) return false;
        return switch (mode) {
            case POSIX -> !operator.equals("=");
            case CMD -> operator.contains("&") || operator.contains("|") || operator.equals("(");
            case POWERSHELL -> operator.contains(";") || operator.contains("&") || operator.contains("|")
                    || operator.equals("{");
        };
    }

    private boolean isFirstNonWhitespaceOnLine(int offset) {
        for (int i = offset - 1; i >= 0 && source.charAt(i) != '\n' && source.charAt(i) != '\r'; i--) {
            if (source.charAt(i) != ' ' && source.charAt(i) != '\t') return false;
        }
        return true;
    }

    private int lineEnd(int start) {
        int end = start;
        while (end < source.length() && source.charAt(end) != '\n' && source.charAt(end) != '\r') end++;
        return end;
    }

    private void consumeAssignmentValue() {
        if (assignmentValue) assignmentValue = false;
    }

    private void add(int start, int end, Kind kind) {
        if (end > start) segments.add(new Segment(baseOffset + start, baseOffset + end, kind));
    }
}

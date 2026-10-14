package com.neko233.jenkinsfile.highlighting;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Produces readable multiline Groovy literals for safely separable embedded scripts. */
final class EmbeddedShellFormatter {
    private static final int MAX_LINE_LENGTH = 100;
    private static final Set<String> COMPLEX_SHELL_WORDS = Set.of(
            "if", "then", "else", "elif", "fi", "for", "select", "while", "until", "do", "done",
            "case", "esac", "function", "coproc"
    );

    private EmbeddedShellFormatter() { }

    static @Nullable String formatLiteral(String source, JenkinsfileEmbeddedScriptAnnotator.ScriptLiteral literal) {
        if ((literal.quote() != '\'' && literal.quote() != '"')
                || (literal.delimiter() != 1 && literal.delimiter() != 3)) return null;

        if (hasEscapedGroovyLineBreak(literal.text())) return null;

        if (literal.mode() != EmbeddedScriptLexer.Mode.POSIX) {
            return EmbeddedCommandFormatter.formatLiteral(source, literal);
        }

        String body = literal.text();
        if (body.trim().startsWith("#!") || body.contains("<<")
                || body.contains("'''") && literal.quote() == '\''
                || body.contains("\"\"\"") && literal.quote() == '"') return null;

        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        String lineIndent = indentationAt(source, literal.stepStart());
        String bodyIndent = lineIndent + "    ";
        List<String> formatted = splitCommands(body, newline, bodyIndent);
        if (formatted == null) return null;

        String delimiter = String.valueOf(literal.quote()).repeat(3);
        return delimiter + newline + bodyIndent + String.join(newline + bodyIndent, formatted)
                + newline + lineIndent + delimiter;
    }

    private static boolean hasEscapedGroovyLineBreak(String body) {
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) != '\\') continue;
            int end = i + 1;
            while (end < body.length() && body.charAt(end) == '\\') end++;
            if (((end - i) & 1) == 1 && end < body.length()
                    && (body.charAt(end) == 'n' || body.charAt(end) == 'r' || body.charAt(end) == '\n' || body.charAt(end) == '\r')) {
                return true;
            }
            i = end - 1;
        }
        return false;
    }

    private static @Nullable List<String> splitCommands(String body, String newline, String bodyIndent) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        int parentheses = 0;
        int parameterExpansionDepth = 0;

        for (int i = 0; i < body.length();) {
            char c = body.charAt(i);

            if (c == '\\' && i + 1 < body.length() && body.charAt(i + 1) == '\\'
                    && i + 2 < body.length() && (body.charAt(i + 2) == '\n' || body.charAt(i + 2) == '\r')) {
                // Our canonical Groovy source spells a shell continuation as \\\\ followed by a newline.
                if (!current.isEmpty() && !Character.isWhitespace(current.charAt(current.length() - 1))) current.append(' ');
                i = skipLineBreakAndIndent(body, i + 2);
                continue;
            }
            if (c == '\\' && i + 1 < body.length() && (body.charAt(i + 1) == '\n' || body.charAt(i + 1) == '\r')) {
                return null;
            }

            if (quote != 0) {
                current.append(c);
                if (c == quote && (quote == '\'' || !isEscaped(body, i))) quote = 0;
                i++;
                if ((c == '\n' || c == '\r') && quote != 0) return null;
                continue;
            }

            if (c == '\\' && i + 1 < body.length()) {
                current.append(c).append(body.charAt(i + 1));
                i += 2;
                continue;
            }

            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                current.append(c);
                i++;
                continue;
            }

            if ((c == '(' && i + 1 < body.length() && body.charAt(i + 1) == '(')
                    || (c == '[' && i + 1 < body.length() && body.charAt(i + 1) == '[')) return null;

            if (c == '$' && i + 1 < body.length() && body.charAt(i + 1) == '{') {
                parameterExpansionDepth++;
                current.append("${");
                i += 2;
                continue;
            }
            if (c == '}' && parameterExpansionDepth > 0) {
                parameterExpansionDepth--;
                current.append(c);
                i++;
                continue;
            }

            if (isWordStart(c)) {
                int end = i + 1;
                while (end < body.length() && isWordPart(body.charAt(end))) end++;
                String word = body.substring(i, end);
                if (COMPLEX_SHELL_WORDS.contains(word) || word.equals("[[") || word.equals("]]")) return null;
                current.append(word);
                i = end;
                continue;
            }

            if (c == '{' || c == '}') return null;

            if (c == '#') {
                int previous = current.length() - 1;
                if (previous < 0 || Character.isWhitespace(current.charAt(previous))
                        || ";|&".indexOf(current.charAt(previous)) >= 0) {
                    int end = i;
                    while (end < body.length() && body.charAt(end) != '\n' && body.charAt(end) != '\r') end++;
                    current.append(body, i, end);
                    i = end;
                    continue;
                }
            }

            if (c == '(') parentheses++;
            else if (c == ')' && parentheses > 0) parentheses--;

            if (parentheses == 0 && parameterExpansionDepth == 0 && c == ';') {
                if ((i + 1 < body.length() && (body.charAt(i + 1) == ';' || body.charAt(i + 1) == '&'))
                        || (i > 0 && body.charAt(i - 1) == '&')) return null;
                addLine(lines, current);
                i++;
                continue;
            }

            if (parentheses == 0 && parameterExpansionDepth == 0 && ((c == '&' && i + 1 < body.length() && body.charAt(i + 1) == '&')
                    || (c == '|' && i + 1 < body.length() && body.charAt(i + 1) == '|')
                    || (c == '|' && (i + 1 >= body.length() || body.charAt(i + 1) != '|'))
                    || (c == '&' && (i + 1 >= body.length() || (body.charAt(i + 1) != '&'
                    && body.charAt(i + 1) != '>' && body.charAt(i + 1) != '<'))))) {
                if (current.toString().isBlank()) return null;
                String operator = c == '|' && i + 1 < body.length() && body.charAt(i + 1) == '&'
                        ? "|&" : body.substring(i, i + 1 + (i + 1 < body.length()
                        && body.charAt(i + 1) == c ? 1 : 0));
                trimRight(current);
                current.append(' ').append(operator);
                addLine(lines, current);
                i += operator.length();
                continue;
            }

            if ((c == '\n' || c == '\r') && parentheses == 0 && parameterExpansionDepth == 0) {
                addLine(lines, current);
                if (c == '\r' && i + 1 < body.length() && body.charAt(i + 1) == '\n') i++;
                i++;
                continue;
            }

            current.append(c);
            i++;
        }

        if (quote != 0 || parentheses != 0 || parameterExpansionDepth != 0) return null;
        addLine(lines, current);
        if (lines.isEmpty()) return null;

        List<String> wrapped = new ArrayList<>();
        for (String line : lines) {
            String formattedLine = wrapLongSimpleCommand(line, newline, bodyIndent);
            if (formattedLine == null) return null;
            wrapped.add(formattedLine);
        }
        return List.copyOf(wrapped);
    }

    private static String wrapLongSimpleCommand(String line, String newline, String bodyIndent) {
        if (line.length() <= MAX_LINE_LENGTH || line.indexOf('#') >= 0) return line;
        List<WordBoundary> boundaries = new ArrayList<>();
        char quote = 0;
        int parentheses = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote && (quote == '\'' || !isEscaped(line, i))) quote = 0;
                continue;
            }
            if (c == '\\' && i + 1 < line.length()) {
                i++;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                continue;
            }
            if (c == '(') parentheses++;
            else if (c == ')' && parentheses > 0) parentheses--;
            else if (Character.isWhitespace(c) && parentheses == 0 && !isEscaped(line, i)) {
                int wordStart = i;
                while (wordStart < line.length() && Character.isWhitespace(line.charAt(wordStart))) wordStart++;
                if (wordStart < line.length()) boundaries.add(new WordBoundary(i, wordStart));
            }
        }
        if (quote != 0 || boundaries.isEmpty()) return line;

        StringBuilder result = new StringBuilder();
        int segmentStart = 0;
        int boundaryIndex = 0;
        boolean hasNamedOptions = boundaries.stream().anyMatch(boundary -> line.startsWith("--", boundary.wordStart()));
        while (true) {
            WordBoundary chosen = null;
            if (hasNamedOptions) {
                for (int i = boundaryIndex; i < boundaries.size(); i++) {
                    WordBoundary boundary = boundaries.get(i);
                    if (boundary.wordStart() > segmentStart && line.startsWith("--", boundary.wordStart())) {
                        chosen = boundary;
                        boundaryIndex = i + 1;
                        break;
                    }
                }
            }
            if (chosen == null && line.length() - segmentStart > MAX_LINE_LENGTH) {
                int limit = segmentStart + MAX_LINE_LENGTH;
                for (int i = boundaryIndex; i < boundaries.size() && boundaries.get(i).offset() <= limit; i++) {
                    if (boundaries.get(i).wordStart() > segmentStart) chosen = boundaries.get(i);
                    boundaryIndex = i + 1;
                }
            }
            if (chosen == null && line.length() - segmentStart <= MAX_LINE_LENGTH) break;
            if (chosen == null) return line;
            result.append(line, segmentStart, chosen.offset()).append(' ').append("\\\\")
                    .append(newline).append(bodyIndent).append("    ");
            segmentStart = chosen.wordStart();
        }
        result.append(line.substring(segmentStart));
        return result.toString();
    }

    private static int skipLineBreakAndIndent(String source, int offset) {
        int next = offset;
        if (source.charAt(next) == '\r' && next + 1 < source.length() && source.charAt(next + 1) == '\n') next += 2;
        else next++;
        while (next < source.length() && (source.charAt(next) == ' ' || source.charAt(next) == '\t')) next++;
        return next;
    }

    private static boolean isWordStart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '!' || c == '$' || c == '%' || c == '@';
    }

    private static boolean isWordPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '/' || c == ':' || c == '$' || c == '%' || c == '@' || c == '=';
    }

    private static void addLine(List<String> lines, StringBuilder current) {
        String line = current.toString().trim();
        if (!line.isEmpty()) lines.add(line);
        current.setLength(0);
    }

    private static void trimRight(StringBuilder value) {
        while (!value.isEmpty() && Character.isWhitespace(value.charAt(value.length() - 1))) {
            value.setLength(value.length() - 1);
        }
    }

    private static boolean isEscaped(String value, int offset) {
        int slashes = 0;
        for (int i = offset - 1; i >= 0 && value.charAt(i) == '\\'; i--) slashes++;
        return (slashes & 1) != 0;
    }

    private static String indentationAt(String source, int offset) {
        int lineStart = Math.max(source.lastIndexOf('\n', Math.max(0, offset - 1)), source.lastIndexOf('\r', Math.max(0, offset - 1))) + 1;
        int end = lineStart;
        while (end < offset && (source.charAt(end) == ' ' || source.charAt(end) == '\t')) end++;
        return source.substring(lineStart, end);
    }

    private record WordBoundary(int offset, int wordStart) { }
}

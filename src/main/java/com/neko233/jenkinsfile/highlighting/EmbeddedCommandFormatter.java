package com.neko233.jenkinsfile.highlighting;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Formats safe, simple Windows command and PowerShell script literals. */
final class EmbeddedCommandFormatter {
    private static final int MAX_LINE_LENGTH = 100;
    private static final Set<String> POWERSHELL_CONTROL_WORDS = Set.of(
            "begin", "break", "catch", "class", "continue", "data", "do", "dynamicparam", "else", "elseif", "end",
            "exit", "finally", "filter", "for", "foreach", "function", "if", "param", "process", "return",
            "switch", "throw", "trap", "try", "until", "using", "while", "workflow"
    );
    private static final Set<String> CMD_CONTROL_WORDS = Set.of("else", "exit", "for", "goto", "if", "call");

    private EmbeddedCommandFormatter() { }

    static @Nullable String formatLiteral(String source, JenkinsfileEmbeddedScriptAnnotator.ScriptLiteral literal) {
        String body = literal.text();
        char quote = literal.quote();
        if (body.trim().startsWith("#!")
                || body.contains("'''") && quote == '\''
                || body.contains("\"\"\"") && quote == '"'
                || hasUnsupportedLiteralSyntax(body, literal.mode())) return null;

        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        String lineIndent = indentationAt(source, literal.stepStart());
        String bodyIndent = lineIndent + "    ";
        List<String> formatted = formatBody(body, literal.mode(), bodyIndent);
        if (formatted == null) return null;

        String delimiter = String.valueOf(quote).repeat(3);
        return delimiter + newline + bodyIndent + String.join(newline + bodyIndent, formatted)
                + newline + lineIndent + delimiter;
    }

    private static boolean hasUnsupportedLiteralSyntax(String body, EmbeddedScriptLexer.Mode mode) {
        if (mode == EmbeddedScriptLexer.Mode.CMD) {
            return body.lines().anyMatch(line -> {
                String trimmed = line.stripLeading();
                return trimmed.startsWith("::") || trimmed.matches("(?i)^rem(?:\\s|$).*");
            });
        }
        return body.contains("<#") || body.contains("#>") || body.contains("@\"") || body.contains("@'");
    }

    private static @Nullable List<String> formatBody(String body, EmbeddedScriptLexer.Mode mode, String bodyIndent) {
        if (!isSimpleScript(body, mode)) return null;
        List<String> logicalLines = mergeContinuations(body, mode);
        if (logicalLines == null) return null;

        List<String> formattedLines = new ArrayList<>();
        for (String logicalLine : logicalLines) {
            if (logicalLine.isBlank()) {
                formattedLines.add("");
                continue;
            }

            List<CommandSegment> segments = splitCommands(logicalLine, mode);
            if (segments == null) return null;
            for (CommandSegment segment : segments) {
                if (isControlFlowCommand(segment.command(), mode)) return null;
                List<String> wrapped = wrapLongCommand(segment.command(), mode, bodyIndent);
                if (wrapped == null) return null;
                formattedLines.addAll(wrapped);

                if (!segment.operator().isEmpty()) {
                    int last = formattedLines.size() - 1;
                    String line = formattedLines.get(last)
                            + (segment.operator().equals(";") ? ";" : " " + segment.operator());
                    if (mode == EmbeddedScriptLexer.Mode.CMD || !segment.operator().equals(";")) {
                        line += " " + continuationFor(mode);
                    }
                    formattedLines.set(last, line);
                }
            }
        }

        trimBlankEdges(formattedLines);
        if (formattedLines.isEmpty()) return null;
        return List.copyOf(formattedLines);
    }

    private static boolean isSimpleScript(String body, EmbeddedScriptLexer.Mode mode) {
        char quote = 0;
        int brackets = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (quote != 0) {
                if (mode == EmbeddedScriptLexer.Mode.POWERSHELL && quote == '\'' && c == '\''
                        && i + 1 < body.length() && body.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                if (c == quote && !isEscaped(body, i, mode)) quote = 0;
                else if (isEscape(c, mode) && i + 1 < body.length()) i++;
                continue;
            }

            if (isEscape(c, mode) && i + 1 < body.length()) {
                i++;
                continue;
            }
            if (isQuote(c, mode)) {
                quote = c;
                continue;
            }
            if (mode == EmbeddedScriptLexer.Mode.POWERSHELL && c == '#') return false;
            if (mode == EmbeddedScriptLexer.Mode.CMD && "()<>".indexOf(c) >= 0) return false;
            if (mode == EmbeddedScriptLexer.Mode.POWERSHELL && (c == '{' || c == '}')) return false;
            if (c == '[') brackets++;
            else if (c == ']' && --brackets < 0) return false;
        }
        if (quote != 0 || brackets != 0) return false;
        return true;
    }

    private static boolean isControlFlowCommand(String command, EmbeddedScriptLexer.Mode mode) {
        String trimmed = command.stripLeading();
        if (mode == EmbeddedScriptLexer.Mode.CMD) {
            while (trimmed.startsWith("@")) trimmed = trimmed.substring(1).stripLeading();
            if (trimmed.startsWith(":")) return true;
        }
        int wordEnd = 0;
        while (wordEnd < trimmed.length() && !Character.isWhitespace(trimmed.charAt(wordEnd))) wordEnd++;
        if (wordEnd == 0) return false;
        String firstWord = trimmed.substring(0, wordEnd).toLowerCase(Locale.ROOT);
        return mode == EmbeddedScriptLexer.Mode.CMD
                ? CMD_CONTROL_WORDS.contains(firstWord)
                : POWERSHELL_CONTROL_WORDS.contains(firstWord);
    }

    private static @Nullable List<String> mergeContinuations(String body, EmbeddedScriptLexer.Mode mode) {
        String marker = continuationFor(mode);
        String[] physicalLines = body.split("\\r\\n|\\r|\\n", -1);
        List<String> result = new ArrayList<>();
        StringBuilder joined = null;
        for (int i = 0; i < physicalLines.length; i++) {
            String line = physicalLines[i].trim();
            if (joined == null) joined = new StringBuilder(line);
            else joined.append(' ').append(line);

            if (hasContinuationAtEnd(physicalLines[i], marker, mode)) {
                if (i + 1 == physicalLines.length || !hasBalancedQuotes(physicalLines[i].trim(), mode)) return null;
                int markerOffset = joined.length() - 1;
                while (markerOffset >= 0 && Character.isWhitespace(joined.charAt(markerOffset))) markerOffset--;
                if (markerOffset < 0 || joined.charAt(markerOffset) != marker.charAt(0)) return null;
                joined.setLength(markerOffset);
                while (!joined.isEmpty() && Character.isWhitespace(joined.charAt(joined.length() - 1))) joined.setLength(joined.length() - 1);
                continue;
            }

            result.add(joined.toString().trim());
            joined = null;
        }
        if (joined != null) return null;
        trimBlankEdges(result);
        return result;
    }

    private static boolean hasContinuationAtEnd(String line, String marker, EmbeddedScriptLexer.Mode mode) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) end--;
        int count = 0;
        while (end - count > 0 && line.charAt(end - count - 1) == marker.charAt(0)) count++;
        return (count & 1) == 1 && hasBalancedQuotes(line.substring(0, end), mode);
    }

    private static boolean hasBalancedQuotes(String source, EmbeddedScriptLexer.Mode mode) {
        char quote = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (mode == EmbeddedScriptLexer.Mode.POWERSHELL && quote == '\'' && c == '\''
                        && i + 1 < source.length() && source.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                if (c == quote && !isEscaped(source, i, mode)) quote = 0;
                else if (isEscape(c, mode) && i + 1 < source.length()) i++;
            } else if (isQuote(c, mode)) quote = c;
        }
        return quote == 0;
    }

    private static @Nullable List<CommandSegment> splitCommands(String line, EmbeddedScriptLexer.Mode mode) {
        List<CommandSegment> segments = new ArrayList<>();
        char quote = 0;
        int parentheses = 0;
        int brackets = 0;
        int start = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (mode == EmbeddedScriptLexer.Mode.POWERSHELL && quote == '\'' && c == '\''
                        && i + 1 < line.length() && line.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                if (c == quote && !isEscaped(line, i, mode)) quote = 0;
                else if (isEscape(c, mode) && i + 1 < line.length()) i++;
                continue;
            }
            if (isEscape(c, mode) && i + 1 < line.length()) {
                i++;
                continue;
            }
            if (isQuote(c, mode)) {
                quote = c;
                continue;
            }
            if (c == '(') parentheses++;
            else if (c == ')' && parentheses > 0) parentheses--;
            else if (c == '[') brackets++;
            else if (c == ']' && brackets > 0) brackets--;
            if (parentheses != 0 || brackets != 0) continue;

            String operator = commandOperatorAt(line, i, mode);
            if (operator == null) continue;
            String command = line.substring(start, i).trim();
            if (command.isEmpty()) return null;
            segments.add(new CommandSegment(command, operator));
            i += operator.length() - 1;
            start = i + 1;
        }
        if (quote != 0 || parentheses != 0 || brackets != 0) return null;
        String remainder = line.substring(start).trim();
        if (remainder.isEmpty() && !segments.isEmpty()) return null;
        if (!remainder.isEmpty()) segments.add(new CommandSegment(remainder, ""));
        return segments.isEmpty() ? null : segments;
    }

    private static @Nullable String commandOperatorAt(String line, int offset, EmbeddedScriptLexer.Mode mode) {
        char c = line.charAt(offset);
        if (mode == EmbeddedScriptLexer.Mode.CMD) {
            if (c == '&') return offset + 1 < line.length() && line.charAt(offset + 1) == '&' ? "&&" : "&";
            if (c == '|') return offset + 1 < line.length() && line.charAt(offset + 1) == '|' ? "||" : "|";
            return null;
        }
        if (c == ';') return ";";
        if (c == '|') return offset + 1 < line.length() && line.charAt(offset + 1) == '|' ? "||" : "|";
        if (c == '&' && offset + 1 < line.length() && line.charAt(offset + 1) == '&') return "&&";
        return null;
    }

    private static @Nullable List<String> wrapLongCommand(String command, EmbeddedScriptLexer.Mode mode,
                                                           String bodyIndent) {
        if (command.length() <= MAX_LINE_LENGTH) return List.of(command);
        List<WordBoundary> boundaries = wordBoundaries(command, mode);
        if (boundaries == null || boundaries.isEmpty()) return List.of(command);
        boolean hasOptions = boundaries.stream().anyMatch(boundary -> isOption(command, boundary.wordStart(), mode));

        List<String> wrapped = new ArrayList<>();
        int segmentStart = 0;
        int boundaryIndex = 0;
        boolean firstSegment = true;
        while (true) {
            WordBoundary chosen = null;
            if (hasOptions) {
                for (int i = boundaryIndex; i < boundaries.size(); i++) {
                    WordBoundary boundary = boundaries.get(i);
                    if (boundary.wordStart() > segmentStart && isOption(command, boundary.wordStart(), mode)) {
                        chosen = boundary;
                        boundaryIndex = i + 1;
                        break;
                    }
                }
            }
            if (chosen == null && command.length() - segmentStart > MAX_LINE_LENGTH) {
                int limit = segmentStart + MAX_LINE_LENGTH;
                for (int i = boundaryIndex; i < boundaries.size() && boundaries.get(i).offset() <= limit; i++) {
                    if (boundaries.get(i).wordStart() > segmentStart) chosen = boundaries.get(i);
                    boundaryIndex = i + 1;
                }
            }
            if (chosen == null && command.length() - segmentStart <= MAX_LINE_LENGTH) break;
            if (chosen == null) return List.of(command);
            wrapped.add((firstSegment ? "" : "    ")
                    + command.substring(segmentStart, chosen.offset()).trim() + " " + continuationFor(mode));
            segmentStart = chosen.wordStart();
            firstSegment = false;
        }
        wrapped.add((firstSegment ? "" : "    ") + command.substring(segmentStart));
        return List.copyOf(wrapped);
    }

    private static List<WordBoundary> wordBoundaries(String line, EmbeddedScriptLexer.Mode mode) {
        List<WordBoundary> boundaries = new ArrayList<>();
        char quote = 0;
        int parentheses = 0;
        int brackets = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (mode == EmbeddedScriptLexer.Mode.POWERSHELL && quote == '\'' && c == '\''
                        && i + 1 < line.length() && line.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                if (c == quote && !isEscaped(line, i, mode)) quote = 0;
                else if (isEscape(c, mode) && i + 1 < line.length()) i++;
                continue;
            }
            if (isEscape(c, mode) && i + 1 < line.length()) {
                i++;
                continue;
            }
            if (isQuote(c, mode)) {
                quote = c;
                continue;
            }
            if (c == '(') parentheses++;
            else if (c == ')' && parentheses > 0) parentheses--;
            else if (c == '[') brackets++;
            else if (c == ']' && brackets > 0) brackets--;
            else if (Character.isWhitespace(c) && parentheses == 0 && brackets == 0) {
                int wordStart = i;
                while (wordStart < line.length() && Character.isWhitespace(line.charAt(wordStart))) wordStart++;
                if (wordStart < line.length()) boundaries.add(new WordBoundary(i, wordStart));
            }
        }
        if (quote != 0 || parentheses != 0 || brackets != 0) return null;
        return boundaries;
    }

    private static boolean isOption(String command, int start, EmbeddedScriptLexer.Mode mode) {
        char prefix = command.charAt(start);
        if (mode == EmbeddedScriptLexer.Mode.CMD) return (prefix == '/' || prefix == '-')
                && start + 1 < command.length() && Character.isLetter(command.charAt(start + 1));
        return prefix == '-' && start + 1 < command.length()
                && (Character.isLetter(command.charAt(start + 1)) || command.charAt(start + 1) == '-');
    }

    private static String continuationFor(EmbeddedScriptLexer.Mode mode) {
        return mode == EmbeddedScriptLexer.Mode.CMD ? "^" : "`";
    }

    private static boolean isQuote(char c, EmbeddedScriptLexer.Mode mode) {
        return c == '"' || (mode == EmbeddedScriptLexer.Mode.POWERSHELL && c == '\'');
    }

    private static boolean isEscape(char c, EmbeddedScriptLexer.Mode mode) {
        return mode == EmbeddedScriptLexer.Mode.CMD ? c == '^'
                : mode == EmbeddedScriptLexer.Mode.POWERSHELL ? c == '`' : c == '\\';
    }

    private static boolean isEscaped(String source, int offset, EmbeddedScriptLexer.Mode mode) {
        int escapes = 0;
        for (int i = offset - 1; i >= 0 && isEscape(source.charAt(i), mode); i--) escapes++;
        return (escapes & 1) != 0;
    }

    private static void trimBlankEdges(List<String> lines) {
        while (!lines.isEmpty() && lines.get(0).isBlank()) lines.remove(0);
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) lines.remove(lines.size() - 1);
    }

    private static String indentationAt(String source, int offset) {
        int lineStart = Math.max(source.lastIndexOf('\n', Math.max(0, offset - 1)),
                source.lastIndexOf('\r', Math.max(0, offset - 1))) + 1;
        int end = lineStart;
        while (end < offset && (source.charAt(end) == ' ' || source.charAt(end) == '\t')) end++;
        return source.substring(lineStart, end);
    }

    private record CommandSegment(String command, String operator) { }
    private record WordBoundary(int offset, int wordStart) { }
}

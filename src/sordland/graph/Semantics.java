package sordland.graph;

import java.util.*;
import java.util.regex.Pattern;

public final class Semantics {
    private Semantics() {
    }
    public enum CommandKind {
        EFFECT, UNKNOWN, COSMETIC, TERMINAL, PANEL
    }
    public record Command(CommandKind kind, String raw, String origin) {
    }
    public record Analysis(List<Command> commands) {
        public Analysis {
            commands = List.copyOf(commands);
        }
        public List<String> effects() {
            return ofKind(CommandKind.EFFECT);
        }
        public List<String> unknown() {
            return ofKind(CommandKind.UNKNOWN);
        }
        public List<String> cosmetic() {
            return ofKind(CommandKind.COSMETIC);
        }
        public boolean terminal() {
            return commands.stream().anyMatch(c -> c.kind() == CommandKind.TERMINAL);
        }
        private List<String> ofKind(CommandKind kind) {
            return commands.stream().filter(c -> c.kind() == kind).map(Command::raw).toList();
        }
    }
    private static final Pattern BASE_GAME = Pattern.compile("(?<![A-Za-z0-9_.])BaseGame\\.");
    private static final Pattern ASSIGNMENT = Pattern.compile("^(?:Variable\\s*\\[\\s*[\"'][^\"']+[\"']\\s*\\]|[A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)+)\\s*(?:[+*/%-]=|=(?!=)).+",
        Pattern.DOTALL);
    private static final Pattern CALL = Pattern.compile("^([A-Za-z_]\\w*)\\s*\\(.*\\)\\s*$", Pattern.DOTALL);
    private static final Pattern COMPLEX = Pattern.compile("(?m)(?:^|;)\\s*(?:if|elseif|else|for|while|repeat|until|function|return|goto)\\b");
    private static final Set<String> COSMETIC = Set.of("Continue", "WaitForMessage", "WaitforMessage", "AddConversant",
        "RemoveConversant", "PlaySceneMusic", "PlaySoundEffect", "SetRichPresenceData", "SetRichPresence", "LookAt",
        "LookAtModeOff", "FocusToken", "PlayCustomMapMusic", "PlayMusic", "PlayLoopedMusic", "DontPlayMapMusic", "DontPlayMapAmbience",
        "PrologueImage", "AnalyticsEvent");
    private static final Set<String> STATE_CALLS = Set.of("AddTokenStatus", "RemoveTokenStatus", "RemoveTokenStatusAllCities",
        "AssignConnection", "UpdateCharacterTitle", "UpdateCountryRelationship", "EnableNews", "UpdateProgress", "EnableCollectionItem",
        "DisableCollectionItem", "UpdateCityGdp", "UpdateCityPopulation", "AddPlayerCountryStatus", "RemovePlayerCountryStatus",
        "AssignComposition", "DisableToken", "EnableToken", "EnableCodexEntry", "UnlockAchievement", "UnlockSteamAchievement",
        "AddReport", "AddNews", "AddJournalEntry", "AdvanceTimeline");
    public static String conditionDisplay(String source) {
        return BASE_GAME.matcher(Objects.requireNonNullElse(source, "")).replaceAll("");
    }
    public static Analysis analyze(String script, String sequence) {
        var result = new ArrayList<Command>();
        classify(Objects.requireNonNullElse(script, ""), "User script", result);
        classify(Objects.requireNonNullElse(sequence, ""), "Sequence", result);
        return new Analysis(result);
    }
    private static void classify(String source, String origin, List<Command> result) {
        String withoutComments = removeComments(source).trim();
        if (withoutComments.isEmpty()) return;
        if (COMPLEX.matcher(withoutComments).find() || !balanced(withoutComments)) {
            result.add(new Command(CommandKind.UNKNOWN, source.trim(), origin));
            return;
        }
        for (String statement: statements(withoutComments)) {
            CommandKind kind = CommandKind.UNKNOWN;
            var call = CALL.matcher(statement);
            if (ASSIGNMENT.matcher(statement).matches()) kind = CommandKind.EFFECT;
            else if (call.matches()) {
                String function = call.group(1);
                if (function.equals("End") && statement.matches("End\\s*\\(\\s*\\)")) kind = CommandKind.TERMINAL;
                else if (COSMETIC.contains(function)) kind = CommandKind.COSMETIC;
                else if (function.equals("ShowPagedDecisionsPanel")) kind = CommandKind.PANEL;
                else if (STATE_CALLS.contains(function)) kind = CommandKind.EFFECT;
            }
            result.add(new Command(kind, statement, origin));
        }
    }
    private static boolean balanced(String source) {
        var brackets = new ArrayDeque<Character>();
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i<source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') quote = c;
            else if (c == '(' || c == '[' || c == '{') brackets.push(c);
            else if (c == ')' || c == ']' || c == '}') {
                if (brackets.isEmpty()) return false;
                char open = brackets.pop();
                if (c == ')' && open != '(' || c == ']' && open != '[' || c == '}' && open != '{') return false;
            }
        }
        return quote == 0 && brackets.isEmpty();
    }
    static List<String> statements(String source) {
        var result = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        int depth = 0;
        for (int i = 0; i<source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
                current.append(c);
            } else {
                if (c == '(' || c == '[' || c == '{') depth++;
                if (c == ')' || c == ']' || c == '}') depth--;
                if ((c == ';' || c == '\n' || c == '\r') && depth == 0) {
                    addStatement(current, result);
                } else current.append(c);
            }
        }
        addStatement(current, result);
        if (quote != 0 || depth != 0) return List.of(source.trim());
        return result;
    }
    private static void addStatement(StringBuilder current, List<String> result) {
        String value = current.toString().trim();
        if (!value.isEmpty()) result.add(value);
        current.setLength(0);
    }
    private static String removeComments(String source) {
        StringBuilder result = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i<source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                result.append(c);
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
                result.append(c);
            } else if (i + 1<source.length() &&((c == '-' && source.charAt(i + 1) == '-') ||(c == '/' && source.charAt(i + 1) == '/'))) {
                if (c == '-' && source.startsWith("--[[", i)) {
                    int end = source.indexOf("]]", i + 4);
                    i = end<0 ? source.length(): end + 1;
                    result.append(' ');
                } else {
                    while (i<source.length() && source.charAt(i) != '\n') i++;
                    result.append('\n');
                }
            } else result.append(c);
        }
        return result.toString();
    }
}

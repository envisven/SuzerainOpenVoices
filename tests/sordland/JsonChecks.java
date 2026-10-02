package sordland;

import sordland.data.Json;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static sordland.TestSupport.*;

final class JsonChecks {
    private JsonChecks() {
    }
    static void run() throws Exception {
        Map<String, Object> root = Json.object(parse("{\"text\":\"a\\n\\\"b\\u00e9\\uD83D\\uDE00\",\"negative\":-42,\"decimal\":1.25e2,\"array\":[true,false,null]}"));
        equal("a\n\"bé😀", root.get("text"), "UTF-8, escapes and surrogate pair survive parsing");
        equal(-42L, root.get("negative"), "Negative integer is exact");
        equal(new BigDecimal("1.25e2"), root.get("decimal"), "Decimal exponent is exact");
        equal(Arrays.asList(true, false, null), root.get("array"), "Arrays preserve null and booleans");
        equal(Json.pretty(root), Json.pretty(parse(Json.pretty(root))), "Pretty-print round trip preserves JSON values (JSON has no separate integer/decimal types)");
        Map<String, Object> numbered = new LinkedHashMap<>();
        numbered.put("10", "tenth");
        numbered.put("2", "second");
        numbered.put("$type", "ignored metadata");
        numbered.put("0", "first");
        equal(List.of("first", "second", "tenth"), Json.list(numbered), "Dump dictionaries preserve numeric source order");
        equal(List.of("a", "b"), Json.list(List.of("a", "b")), "Conventional JSON arrays are supported");
        for (String malformed: List.of("{\"x\":1,\"x\":2}", "[1,]", "{\"x\":}", "01", "1e", "1.", "true false", "\"unterminated",
            "\"bad\\x\"", "\"a\nb\"")) {
            boolean rejected = false;
            try {
                parse(malformed);
            } catch (IOException expected) {
                rejected = true;
            }
            check(rejected, "Malformed JSON rejected: " + malformed.replace('\n', ' '));
        }
    }
    private static Object parse(String text) throws IOException {
        Path directory = Paths.get("build", "test-fixtures");
        Files.createDirectories(directory);
        Path path = Files.createTempFile(directory, "json-", ".json");
        try {
            Files.writeString(path, text, StandardCharsets.UTF_8);
            return Json.parse(path);
        } finally {
            Files.deleteIfExists(path);
        }
    }
}

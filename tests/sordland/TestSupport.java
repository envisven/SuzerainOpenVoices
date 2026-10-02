package sordland;

import java.util.Objects;

final class TestSupport {
    private TestSupport() {
    }
    private static int checks;
    static void check(boolean success, String message) {
        checks++;
        if (!success) throw new AssertionError(message);
    }
    static void equal(Object expected, Object actual, String message) {
        check(Objects.equals(expected, actual), message + ": expected " + expected + ", got " + actual);
    }
    static int count() {
        return checks;
    }
}

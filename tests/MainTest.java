import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * End-to-end tests for Main: we write two files, run Main.run(...) and check
 * the exact bytes it prints, exactly like the grader would.
 *
 * Run (from the project folder):
 *   javac -d out src/*.java tests/*.java
 *   java -cp out MainTest
 */
public class MainTest {

    static Path folder;
    static int passed = 0;

    public static void main(String[] args) throws IOException {
        folder = Files.createTempDirectory("maintest");

        testReadingTable();
        testPartAExamples();
        testBytesAreKeptExactly();
        testErrors();
        testPartBExamples();
        testRandomLines();
        testRandomHighlight();

        System.out.println("MainTest: all " + passed + " checks passed.");
    }

    // ------------------------------------------------------------------
    // Section 1 of the problem statement: the reading table
    // ------------------------------------------------------------------
    static void testReadingTable() throws IOException {
        checkLines("", List.of());
        checkLines("\n", List.of(""));
        checkLines("a", List.of("a"));
        checkLines("a\n", List.of("a"));
        checkLines("a\n\nb", List.of("a", "", "b"));
        checkLines("a\r\nb\r\n", List.of("a\r", "b\r"));
    }

    static void checkLines(String content, List<String> expected) throws IOException {
        Path file = write("read.txt", content.getBytes(StandardCharsets.UTF_8));
        List<String> actual = new ArrayList<>();
        for (Line line : Main.readLines(file.toString())) {
            actual.add(new String(line.toBytes(), StandardCharsets.UTF_8));
        }
        expect(actual.equals(expected), "readLines(" + show(content) + ") gave " + actual);
    }

    // ------------------------------------------------------------------
    // Part A examples
    // ------------------------------------------------------------------
    static void testPartAExamples() throws IOException {
        // Paper example: any valid, minimal, delete-first answer is accepted.
        RunResult r = run("lines", "a\nb\nc\na\nb\nb\na\n", "c\nb\na\nb\na\nc\n");
        checkLinesOutput(r, "a\nb\nc\na\nb\nb\na\n", "c\nb\na\nb\na\nc\n");
        expect(countEdits(r.stdout) == 5, "paper example should have 5 edits");

        // a b c -> a x c has only one correct answer.
        expectOutput(run("lines", "a\nb\nc\n", "a\nx\nc\n"), " a\n-b\n+x\n c\n");

        // Identical files: keep lines only.
        expectOutput(run("lines", "x\ny\n", "x\ny\n"), " x\n y\n");

        // Two empty files: no output.
        expectOutput(run("lines", "", ""), "");

        // One empty file.
        expectOutput(run("lines", "", "p\nq\n"), "+p\n+q\n");
        expectOutput(run("lines", "p\nq\n", ""), "-p\n-q\n");

        // A missing final newline does not matter ("a" and "a\n" are the same lines).
        expectOutput(run("lines", "a", "a\n"), " a\n");

        // A file with one empty line vs an empty file.
        expectOutput(run("lines", "\n", ""), "-\n");
    }

    // ------------------------------------------------------------------
    // \r and invalid UTF-8 must be copied exactly
    // ------------------------------------------------------------------
    static void testBytesAreKeptExactly() throws IOException {
        // "a\r" and "a" are different lines.
        expectOutput(run("lines", "a\r\nb\n", "a\nb\n"), "-a\r\n+a\n b\n");

        // Bytes that are not valid UTF-8.
        byte[] fileA = {(byte) 0xff, (byte) 0xfe, '\n', 'x', '\n'};
        byte[] fileB = {(byte) 0xff, (byte) 0xfe, '\n', 'y', '\n'};
        byte[] expected = {' ', (byte) 0xff, (byte) 0xfe, '\n', '-', 'x', '\n', '+', 'y', '\n'};
        RunResult r = runBytes("lines", fileA, fileB);
        expect(Arrays.equals(r.stdout, expected), "invalid UTF-8 bytes were not copied exactly");

        // Two different invalid lines must not be treated as equal.
        byte[] c = {(byte) 0x80, '\n'};
        byte[] d = {(byte) 0x81, '\n'};
        RunResult r2 = runBytes("lines", c, d);
        expect(countEdits(r2.stdout) == 2, "0x80 and 0x81 lines must be different");
    }

    // ------------------------------------------------------------------
    // Errors: missing files and wrong arguments
    // ------------------------------------------------------------------
    static void testErrors() throws IOException {
        Path good = write("good.txt", "a\n".getBytes(StandardCharsets.UTF_8));
        String missing = folder.resolve("does-not-exist.txt").toString();

        for (String command : new String[] {"lines", "highlight"}) {
            RunResult r1 = runArgs(command, missing, good.toString());
            expect(r1.exitCode == 2 && r1.stdout.length == 0 && !r1.stderr.isEmpty(),
                    command + ": missing file A must give exit 2, empty stdout, message on stderr");
            RunResult r2 = runArgs(command, good.toString(), missing);
            expect(r2.exitCode == 2 && r2.stdout.length == 0 && !r2.stderr.isEmpty(),
                    command + ": missing file B must give exit 2");
            RunResult r3 = runArgs(command, folder.toString(), good.toString());
            expect(r3.exitCode == 2 && r3.stdout.length == 0, command + ": a folder is not a readable file");
        }

        RunResult bad1 = runArgs("compare", good.toString(), good.toString());
        expect(bad1.exitCode == 2 && bad1.stdout.length == 0, "unknown command must give exit 2");
        RunResult bad2 = runArgs("lines", good.toString());
        expect(bad2.exitCode == 2 && bad2.stdout.length == 0, "missing argument must give exit 2");

        RunResult ok = runArgs("lines", good.toString(), good.toString());
        expect(ok.exitCode == 0, "normal run must give exit 0");
    }

    // ------------------------------------------------------------------
    // Part B examples from the problem statement
    // ------------------------------------------------------------------
    static void testPartBExamples() throws IOException {
        // The port example. "? 12-13 | 11-12" and "? 11-12 | 11-12" are both correct,
        // so we check the rules instead of one exact string.
        RunResult r = run("highlight", "server:\n  port = 8000\n", "server:\n  port = 8080\n");
        String out = new String(r.stdout, StandardCharsets.UTF_8);
        expect(out.startsWith(" server:\n-  port = 8000\n+  port = 8080\n? "), "port example layout: " + show(out));
        checkHighlightOutput(r.stdout, "server:\n  port = 8000\n", "server:\n  port = 8080\n");

        // Pure insertion with an unpaired line (only one correct answer).
        expectOutput(run("highlight", "a = 1\nb = 2\n", "a = 10\n"), "-a = 1\n-b = 2\n+a = 10\n? . | 5-6\n");

        // Emoji count as one character each.
        expectOutput(run("highlight", "hi \uD83D\uDE00\n", "hi \uD83D\uDE03\n"),
                "-hi \uD83D\uDE00\n+hi \uD83D\uDE03\n? 3-4 | 3-4\n");

        // \r counts as one character.
        expectOutput(run("highlight", "ab\r\n", "ab\n"), "-ab\r\n+ab\n? 2-3 | .\n");

        // Touching changes become one range, several ranges are separated by commas.
        expectOutput(run("highlight", "abcdef\n", "aXcdeY\n"), "-abcdef\n+aXcdeY\n? 1-2,5-6 | 1-2,5-6\n");
        expectOutput(run("highlight", "aaaa\n", "abba\n"), "-aaaa\n+abba\n? 1-3 | 1-3\n");

        // Unpaired '+' lines get no '?' line. An empty line paired with a non-empty one.
        expectOutput(run("highlight", "k\n\nk\n", "k\nxy\nz\nk\n"), " k\n-\n+xy\n? . | 0-2\n+z\n k\n");

        // Identical files: no '?' lines at all.
        expectOutput(run("highlight", "same\n", "same\n"), " same\n");
    }

    // ------------------------------------------------------------------
    // Random files: lines output must be valid, minimal and delete-first
    // ------------------------------------------------------------------
    static void testRandomLines() throws IOException {
        Random random = new Random(7);
        String[] words = {"a", "b", "c", "", "}", "a\r", "return x;"};
        for (int t = 0; t < 1500; t++) {
            String a = randomFile(random, words, random.nextInt(25));
            String b = randomFile(random, words, random.nextInt(25));
            checkLinesOutput(run("lines", a, b), a, b);
        }
    }

    // ------------------------------------------------------------------
    // Random highlight runs: both checks of the grader
    // ------------------------------------------------------------------
    static void testRandomHighlight() throws IOException {
        Random random = new Random(99);
        String[] pieces = {"a", "b", "c", " ", "=", "\uD83D\uDE00", "\u00e9", "\r"};
        for (int t = 0; t < 1500; t++) {
            StringBuilder a = new StringBuilder();
            StringBuilder b = new StringBuilder();
            int lines = 1 + random.nextInt(4);
            for (int l = 0; l < lines; l++) {
                a.append(randomText(random, pieces, random.nextInt(12))).append('\n');
                b.append(randomText(random, pieces, random.nextInt(12))).append('\n');
            }
            RunResult r = run("highlight", a.toString(), b.toString());
            checkHighlightOutput(r.stdout, a.toString(), b.toString());
        }
    }

    // ==================================================================
    // Checkers (they do what the grader says it does)
    // ==================================================================

    /** Valid (rebuilds A and B), minimal, and all '-' before '+' in every block. */
    static void checkLinesOutput(RunResult r, String fileA, String fileB) {
        expect(r.exitCode == 0, "exit code should be 0");
        List<String> outLines = splitOutput(new String(r.stdout, StandardCharsets.UTF_8));
        List<String> rebuiltA = new ArrayList<>();
        List<String> rebuiltB = new ArrayList<>();
        char previous = ' ';
        for (String line : outLines) {
            char prefix = line.charAt(0);
            String text = line.substring(1);
            if (prefix == ' ') {
                rebuiltA.add(text);
                rebuiltB.add(text);
            } else if (prefix == '-') {
                expect(previous != '+', "a '-' line comes after a '+' line in the same block");
                rebuiltA.add(text);
            } else if (prefix == '+') {
                rebuiltB.add(text);
            } else {
                expect(false, "unknown prefix in lines output: " + show(line));
            }
            previous = prefix;
        }
        List<String> linesA = splitFile(fileA);
        List<String> linesB = splitFile(fileB);
        expect(rebuiltA.equals(linesA), "output does not rebuild A");
        expect(rebuiltB.equals(linesB), "output does not rebuild B");
        int minimum = linesA.size() + linesB.size() - 2 * lcs(linesA, linesB);
        expect(countEdits(r.stdout) == minimum, "not minimal: " + countEdits(r.stdout) + " vs " + minimum);
    }

    /**
     * Checks a highlight output:
     *  - without the '?' lines it is exactly the 'lines' output
     *  - a '?' line comes right after every paired '+' line, and only there
     *  - removing the highlighted characters makes both lines equal
     *  - the number of highlighted characters is the minimum
     */
    static void checkHighlightOutput(byte[] stdout, String fileA, String fileB) throws IOException {
        List<String> outLines = splitOutput(new String(stdout, StandardCharsets.UTF_8));

        // Remove the '?' lines and compare with the lines command.
        StringBuilder withoutRanges = new StringBuilder();
        for (String line : outLines) {
            if (line.charAt(0) != '?') withoutRanges.append(line).append('\n');
        }
        RunResult linesRun = run("lines", fileA, fileB);
        expect(withoutRanges.toString().equals(new String(linesRun.stdout, StandardCharsets.UTF_8)),
                "highlight output without '?' lines differs from lines output");

        // Walk the blocks.
        int i = 0;
        while (i < outLines.size()) {
            if (outLines.get(i).charAt(0) == ' ') {
                i++;
                continue;
            }
            List<String> minus = new ArrayList<>();
            while (i < outLines.size() && outLines.get(i).charAt(0) == '-') {
                minus.add(outLines.get(i).substring(1));
                i++;
            }
            int plusCount = 0;
            while (i < outLines.size() && outLines.get(i).charAt(0) == '+') {
                String plus = outLines.get(i).substring(1);
                i++;
                if (plusCount < minus.size()) {
                    expect(i < outLines.size() && outLines.get(i).charAt(0) == '?', "missing '?' after a paired '+' line");
                    checkRangeLine(outLines.get(i), minus.get(plusCount), plus);
                    i++;
                } else {
                    expect(i >= outLines.size() || outLines.get(i).charAt(0) != '?', "'?' after an unpaired '+' line");
                }
                plusCount++;
            }
            expect(i >= outLines.size() || outLines.get(i).charAt(0) != '?', "unexpected '?' line");
        }
    }

    static void checkRangeLine(String rangeLine, String oldLine, String newLine) {
        expect(rangeLine.startsWith("? ") && rangeLine.contains(" | "), "bad '?' line: " + show(rangeLine));
        String[] sides = rangeLine.substring(2).split(" \\| ", -1);
        expect(sides.length == 2, "bad '?' line: " + show(rangeLine));
        int[] a = oldLine.codePoints().toArray();
        int[] b = newLine.codePoints().toArray();
        boolean[] markA = parseRanges(sides[0], a.length);
        boolean[] markB = parseRanges(sides[1], b.length);

        List<Integer> restA = new ArrayList<>();
        List<Integer> restB = new ArrayList<>();
        int highlighted = 0;
        for (int k = 0; k < a.length; k++) {
            if (markA[k]) highlighted++; else restA.add(a[k]);
        }
        for (int k = 0; k < b.length; k++) {
            if (markB[k]) highlighted++; else restB.add(b[k]);
        }
        expect(restA.equals(restB), "removing highlights does not give equal lines: " + show(rangeLine)
                + " for " + show(oldLine) + " / " + show(newLine));
        List<Integer> listA = new ArrayList<>();
        List<Integer> listB = new ArrayList<>();
        for (int x : a) listA.add(x);
        for (int x : b) listB.add(x);
        int minimum = a.length + b.length - 2 * lcs(listA, listB);
        expect(highlighted == minimum, "highlighted " + highlighted + " characters, minimum is " + minimum);
    }

    /** Parse "3-5,9-12" or "." and check the format rules (ordered, not touching, no leading zeros). */
    static boolean[] parseRanges(String text, int length) {
        boolean[] marked = new boolean[length];
        if (text.equals(".")) return marked;
        expect(text.matches("(0|[1-9][0-9]*)-(0|[1-9][0-9]*)(,(0|[1-9][0-9]*)-(0|[1-9][0-9]*))*"), "bad ranges: " + text);
        int lastEnd = -1;
        for (String range : text.split(",")) {
            String[] parts = range.split("-");
            int start = Integer.parseInt(parts[0]);
            int end = Integer.parseInt(parts[1]);
            expect(start < end && end <= length, "range out of bounds: " + range);
            expect(start > lastEnd, "ranges overlap, touch, or are out of order: " + text);
            for (int k = start; k < end; k++) marked[k] = true;
            lastEnd = end;
        }
        return marked;
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    static class RunResult {
        int exitCode;
        byte[] stdout;
        String stderr;
    }

    static RunResult run(String command, String fileA, String fileB) throws IOException {
        return runBytes(command, fileA.getBytes(StandardCharsets.UTF_8), fileB.getBytes(StandardCharsets.UTF_8));
    }

    static RunResult runBytes(String command, byte[] fileA, byte[] fileB) throws IOException {
        Path a = write("a.txt", fileA);
        Path b = write("b.txt", fileB);
        return runArgs(command, a.toString(), b.toString());
    }

    static RunResult runArgs(String... args) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        RunResult result = new RunResult();
        result.exitCode = Main.run(args, out, new PrintStream(err, true, StandardCharsets.UTF_8));
        result.stdout = out.toByteArray();
        result.stderr = err.toString(StandardCharsets.UTF_8);
        return result;
    }

    static Path write(String name, byte[] content) throws IOException {
        Path file = folder.resolve(name);
        Files.write(file, content);
        return file;
    }

    static void expectOutput(RunResult r, String expected) {
        String actual = new String(r.stdout, StandardCharsets.UTF_8);
        expect(r.exitCode == 0, "exit code should be 0");
        expect(actual.equals(expected), "expected " + show(expected) + " but got " + show(actual));
    }

    static int countEdits(byte[] stdout) {
        int edits = 0;
        for (String line : splitOutput(new String(stdout, StandardCharsets.ISO_8859_1))) {
            if (line.charAt(0) == '-' || line.charAt(0) == '+') edits++;
        }
        return edits;
    }

    /** Output lines (every output line ends with '\n'). */
    static List<String> splitOutput(String out) {
        List<String> lines = new ArrayList<>();
        if (out.isEmpty()) return lines;
        expect(out.endsWith("\n"), "output must end with a newline");
        for (String line : out.substring(0, out.length() - 1).split("\n", -1)) {
            expect(!line.isEmpty(), "empty output line (every line needs a prefix)");
            lines.add(line);
        }
        return lines;
    }

    /** Same rules as Main.readLines, written differently on purpose. */
    static List<String> splitFile(String content) {
        List<String> lines = new ArrayList<>(Arrays.asList(content.split("\n", -1)));
        if (lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
        return lines;
    }

    static <T> int lcs(List<T> a, List<T> b) {
        int[][] table = new int[a.size() + 1][b.size() + 1];
        for (int i = 1; i <= a.size(); i++) {
            for (int j = 1; j <= b.size(); j++) {
                if (a.get(i - 1).equals(b.get(j - 1))) table[i][j] = table[i - 1][j - 1] + 1;
                else table[i][j] = Math.max(table[i - 1][j], table[i][j - 1]);
            }
        }
        return table[a.size()][b.size()];
    }

    static String randomFile(Random random, String[] words, int lines) {
        StringBuilder sb = new StringBuilder();
        for (int l = 0; l < lines; l++) {
            sb.append(words[random.nextInt(words.length)]).append('\n');
        }
        if (lines > 0 && random.nextInt(4) == 0) sb.setLength(sb.length() - 1);   // sometimes no final newline
        return sb.toString();
    }

    static String randomText(Random random, String[] pieces, int length) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < length; k++) sb.append(pieces[random.nextInt(pieces.length)]);
        return sb.toString();
    }

    static String show(String s) {
        return "\"" + s.replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    static void expect(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        passed++;
    }
}

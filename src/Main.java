import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assignment 1: Myers' diff.
 *
 *   java Main lines     A B    Part A: minimal line diff of file A to file B
 *   java Main highlight A B    Part B: the same diff, plus changed-character ranges
 *
 * Output lines:
 *   " line"   keep   (the line is in both files)
 *   "-line"   delete (the line is only in A)
 *   "+line"   insert (the line is only in B)
 *   "? old | new"   (highlight only) changed character ranges of a line pair
 */
public class Main {

    public static void main(String[] args) {
        int exitCode;
        try {
            exitCode = run(args, System.out, System.err);
        } catch (IOException e) {
            System.err.println("error while writing output: " + e.getMessage());
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    /**
     * Does all the work and returns the exit code.
     * (It is separate from main so the tests can call it directly.)
     */
    static int run(String[] args, OutputStream stdout, PrintStream stderr) throws IOException {
        boolean known = args.length == 3 && (args[0].equals("lines") || args[0].equals("highlight"));
        if (!known) {
            stderr.println("usage: Main lines|highlight A_PATH B_PATH");
            return 2;
        }
        String command = args[0];

        // Read both files BEFORE printing anything, so a bad file means empty stdout.
        List<Line> linesA;
        List<Line> linesB;
        try {
            linesA = readLines(args[1]);
            linesB = readLines(args[2]);
        } catch (IOException | InvalidPathException e) {
            stderr.println("error: cannot read file: " + e.getMessage());
            return 2;
        }

        boolean[] deleted = new boolean[linesA.size()];
        boolean[] inserted = new boolean[linesB.size()];
        diffLines(linesA, linesB, deleted, inserted);

        BufferedOutputStream out = new BufferedOutputStream(stdout, 1 << 16);
        if (command.equals("lines")) {
            printLines(linesA, linesB, deleted, inserted, out);
        } else {
            printHighlight(linesA, linesB, deleted, inserted, out);
        }
        out.flush();
        return 0;
    }

    // =====================================================================
    // Reading a file into lines (problem statement, section 1)
    // =====================================================================

    /**
     * Read the file as raw bytes and split it on the newline byte '\n'.
     * - A final '\n' does not create an extra empty line (the last empty piece is dropped).
     * - '\r' is kept as part of the line.
     * Examples:  ""  -> []     "\n" -> [""]     "a" or "a\n" -> ["a"]
     */
    static List<Line> readLines(String path) throws IOException {
        byte[] data = Files.readAllBytes(Path.of(path));
        List<Line> lines = new ArrayList<>();
        int start = 0;                        // where the current line starts
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                lines.add(new Line(data, start, i));
                start = i + 1;
            }
        }
        if (start < data.length) {            // last piece has no '\n' after it, but it is not empty
            lines.add(new Line(data, start, data.length));
        }
        return lines;
    }

    // =====================================================================
    // Part A: line diff
    // =====================================================================

    /**
     * Fill deleted[] / inserted[] for the lines.
     *
     * Step 1: give every distinct line a number (id), so Myers compares ints.
     *         Line.equals compares the exact bytes, so this also works for bytes
     *         that are not valid UTF-8.
     *
     * Step 2: a line that never appears in the other file can never be kept, so it
     *         is deleted/inserted for sure. We mark it right away and leave it out of
     *         the Myers search. This does not change the answer (it is still minimal)
     *         but makes very different files much faster.
     *
     * Step 3: run Myers on the remaining lines and copy the flags back.
     */
    static void diffLines(List<Line> linesA, List<Line> linesB, boolean[] deleted, boolean[] inserted) {
        // Step 1
        Map<Line, Integer> ids = new HashMap<>(2 * (linesA.size() + linesB.size()));
        int[] idsA = toIds(linesA, ids);
        int[] idsB = toIds(linesB, ids);

        // Step 2: which ids appear in A, and which in B?
        boolean[] inA = new boolean[ids.size()];
        boolean[] inB = new boolean[ids.size()];
        for (int id : idsA) {
            inA[id] = true;
        }
        for (int id : idsB) {
            inB[id] = true;
        }

        // positionA[t] = where the t-th remaining line of A is in the full file (same for B)
        int[] positionA = new int[idsA.length];
        int[] positionB = new int[idsB.length];
        int countA = 0;
        int countB = 0;
        for (int i = 0; i < idsA.length; i++) {
            if (inB[idsA[i]]) {
                positionA[countA++] = i;
            } else {
                deleted[i] = true;            // this line is not in B at all
            }
        }
        for (int j = 0; j < idsB.length; j++) {
            if (inA[idsB[j]]) {
                positionB[countB++] = j;
            } else {
                inserted[j] = true;           // this line is not in A at all
            }
        }

        int[] a = new int[countA];
        int[] b = new int[countB];
        for (int t = 0; t < countA; t++) {
            a[t] = idsA[positionA[t]];
        }
        for (int t = 0; t < countB; t++) {
            b[t] = idsB[positionB[t]];
        }

        // Step 3
        boolean[] smallDeleted = new boolean[countA];
        boolean[] smallInserted = new boolean[countB];
        Myers.diff(a, b, smallDeleted, smallInserted);
        for (int t = 0; t < countA; t++) {
            if (smallDeleted[t]) {
                deleted[positionA[t]] = true;
            }
        }
        for (int t = 0; t < countB; t++) {
            if (smallInserted[t]) {
                inserted[positionB[t]] = true;
            }
        }
    }

    private static int[] toIds(List<Line> lines, Map<Line, Integer> ids) {
        int[] result = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            Integer id = ids.get(line);
            if (id == null) {
                id = ids.size();          // a new line gets the next free number
                ids.put(line, id);
            }
            result[i] = id;
        }
        return result;
    }

    /**
     * Print the edit script. We walk through A (index i) and B (index j) together:
     * first all deleted lines, then all inserted lines, then one kept line.
     * Because deletions are always printed before insertions, every change block
     * follows the delete-first rule automatically.
     */
    static void printLines(List<Line> linesA, List<Line> linesB,
                           boolean[] deleted, boolean[] inserted, OutputStream out) throws IOException {
        int i = 0;
        int j = 0;
        while (i < linesA.size() || j < linesB.size()) {
            while (i < linesA.size() && deleted[i]) {
                writeLine(out, '-', linesA.get(i));
                i++;
            }
            while (j < linesB.size() && inserted[j]) {
                writeLine(out, '+', linesB.get(j));
                j++;
            }
            if (i < linesA.size() && j < linesB.size()) {
                writeLine(out, ' ', linesA.get(i));   // kept line: same in A and B
                i++;
                j++;
            }
        }
    }

    /** One output line: prefix character, the line's exact bytes, then '\n'. */
    static void writeLine(OutputStream out, char prefix, Line line) throws IOException {
        out.write(prefix);
        line.writeTo(out);
        out.write('\n');
    }

    // =====================================================================
    // Part B: highlight
    // =====================================================================

    /**
     * Same walk as printLines, but we collect each change block first.
     * In a block, the 1st '-' line is paired with the 1st '+' line, the 2nd with
     * the 2nd, and so on. After each paired '+' line we print the "?" line.
     */
    static void printHighlight(List<Line> linesA, List<Line> linesB,
                               boolean[] deleted, boolean[] inserted, OutputStream out) throws IOException {
        int i = 0;
        int j = 0;
        while (i < linesA.size() || j < linesB.size()) {
            // Collect one change block (it may be empty).
            List<Line> blockDeleted = new ArrayList<>();
            List<Line> blockInserted = new ArrayList<>();
            while (i < linesA.size() && deleted[i]) {
                blockDeleted.add(linesA.get(i));
                i++;
            }
            while (j < linesB.size() && inserted[j]) {
                blockInserted.add(linesB.get(j));
                j++;
            }

            for (Line line : blockDeleted) {
                writeLine(out, '-', line);
            }
            for (int p = 0; p < blockInserted.size(); p++) {
                writeLine(out, '+', blockInserted.get(p));
                if (p < blockDeleted.size()) {                 // this '+' line has a partner
                    String ranges = rangeLine(blockDeleted.get(p), blockInserted.get(p));
                    out.write(ranges.getBytes(StandardCharsets.US_ASCII));
                }
            }

            if (i < linesA.size() && j < linesB.size()) {
                writeLine(out, ' ', linesA.get(i));
                i++;
                j++;
            }
        }
    }

    /**
     * Build "? <old ranges> | <new ranges>\n" for one line pair.
     * We run Myers again, this time on the characters (Unicode code points).
     * The changed characters are exactly the true flags, so the ranges are
     * just the runs of true values.
     */
    static String rangeLine(Line oldLine, Line newLine) {
        // codePoints() so that an emoji counts as one character (not two Java chars)
        int[] a = oldLine.toText().codePoints().toArray();
        int[] b = newLine.toText().codePoints().toArray();
        boolean[] deletedChars = new boolean[a.length];
        boolean[] insertedChars = new boolean[b.length];
        Myers.diff(a, b, deletedChars, insertedChars);
        return "? " + ranges(deletedChars) + " | " + ranges(insertedChars) + "\n";
    }

    /**
     * Turn flags into ranges.
     * Example: flags  F T T F F T  ->  "1-3,5-6"   (end is not included)
     * No changed characters -> "."
     * A run of true values becomes ONE range, so touching ranges are merged automatically.
     */
    static String ranges(boolean[] changed) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < changed.length) {
            if (!changed[i]) {
                i++;
                continue;
            }
            int start = i;
            while (i < changed.length && changed[i]) {
                i++;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(start).append('-').append(i);
        }
        if (sb.length() == 0) {
            return ".";
        }
        return sb.toString();
    }
}

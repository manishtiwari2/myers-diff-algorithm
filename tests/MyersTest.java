import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Tests for Myers.diff on its own.
 *
 * For each pair (a, b) we check:
 *   1. VALID:   the items that are kept in a, in order, equal the items kept in b.
 *               (So deleting the '-' items from a and inserting the '+' items gives b.)
 *   2. MINIMAL: number of edits == n + m - 2 * LCS(a, b), where LCS comes from the
 *               classic dynamic-programming table (slow, but obviously correct).
 *
 * Run (from the project folder):
 *   javac -d out src/*.java tests/*.java
 *   java -cp out MyersTest
 */
public class MyersTest {

    static int checks = 0;

    public static void main(String[] args) {
        // The example from the paper / problem statement: 5 edits.
        int edits = check(letters("abcabba"), letters("cbabac"));
        if (edits != 5) {
            throw new AssertionError("paper example should need 5 edits, got " + edits);
        }

        // Small edge cases.
        check(letters(""), letters(""));
        check(letters(""), letters("abc"));
        check(letters("abc"), letters(""));
        check(letters("same"), letters("same"));
        check(letters("abc"), letters("axc"));
        check(letters("a"), letters("b"));
        check(letters("x"), letters("yyyyy"));
        check(letters("aaaaab"), letters("baaaaa"));

        // Lots of random pairs, with different sizes and alphabets.
        Random random = new Random(12345);
        for (int t = 0; t < 300000; t++) {
            int alphabet = 1 + random.nextInt(5);         // small alphabet = many matches
            int maxLen = (t % 3 == 0) ? 40 : 12;
            int[] a = randomArray(random, random.nextInt(maxLen + 1), alphabet);
            int[] b = randomArray(random, random.nextInt(maxLen + 1), alphabet);
            check(a, b);
        }
        // Very different lengths.
        for (int t = 0; t < 20000; t++) {
            int alphabet = 1 + random.nextInt(4);
            int[] a = randomArray(random, random.nextInt(60), alphabet);
            int[] b = randomArray(random, random.nextInt(4), alphabet);
            check(a, b);
            check(b, a);
        }
        // b is a with a few random edits (like real files).
        for (int t = 0; t < 20000; t++) {
            int[] a = randomArray(random, random.nextInt(80), 20);
            int[] b = mutate(random, a, 1 + random.nextInt(6), 20);
            check(a, b);
        }

        System.out.println("MyersTest: all " + checks + " checks passed.");
    }

    /** Runs Myers on (a, b), checks it, and returns the number of edits. */
    static int check(int[] a, int[] b) {
        boolean[] deleted = new boolean[a.length];
        boolean[] inserted = new boolean[b.length];
        Myers.diff(a, b, deleted, inserted);

        List<Integer> keptA = new ArrayList<>();
        List<Integer> keptB = new ArrayList<>();
        int edits = 0;
        for (int i = 0; i < a.length; i++) {
            if (deleted[i]) edits++; else keptA.add(a[i]);
        }
        for (int j = 0; j < b.length; j++) {
            if (inserted[j]) edits++; else keptB.add(b[j]);
        }
        if (!keptA.equals(keptB)) {
            fail("kept items differ, so the diff is not valid", a, b);
        }
        int minimum = a.length + b.length - 2 * lcs(a, b);
        if (edits != minimum) {
            fail("not minimal: " + edits + " edits, minimum is " + minimum, a, b);
        }
        checks++;
        return edits;
    }

    /** Length of the longest common subsequence (dynamic programming). */
    static int lcs(int[] a, int[] b) {
        int[][] table = new int[a.length + 1][b.length + 1];
        for (int i = 1; i <= a.length; i++) {
            for (int j = 1; j <= b.length; j++) {
                if (a[i - 1] == b[j - 1]) {
                    table[i][j] = table[i - 1][j - 1] + 1;
                } else {
                    table[i][j] = Math.max(table[i - 1][j], table[i][j - 1]);
                }
            }
        }
        return table[a.length][b.length];
    }

    static int[] letters(String s) {
        return s.codePoints().toArray();
    }

    static int[] randomArray(Random random, int length, int alphabet) {
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = random.nextInt(alphabet);
        }
        return result;
    }

    /** Copy of a with a few random deletes, inserts and replacements. */
    static int[] mutate(Random random, int[] a, int changes, int alphabet) {
        List<Integer> list = new ArrayList<>();
        for (int x : a) list.add(x);
        for (int c = 0; c < changes; c++) {
            int kind = random.nextInt(3);
            if (kind == 0 && !list.isEmpty()) {
                list.remove(random.nextInt(list.size()));
            } else if (kind == 1) {
                list.add(random.nextInt(list.size() + 1), random.nextInt(alphabet));
            } else if (!list.isEmpty()) {
                list.set(random.nextInt(list.size()), random.nextInt(alphabet));
            }
        }
        int[] result = new int[list.size()];
        for (int i = 0; i < result.length; i++) result[i] = list.get(i);
        return result;
    }

    static void fail(String message, int[] a, int[] b) {
        throw new AssertionError(message + "\n  a = " + Arrays.toString(a) + "\n  b = " + Arrays.toString(b));
    }
}

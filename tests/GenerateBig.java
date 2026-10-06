import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Writes big test files into out/big/ so we can time the program.
 * (out/ is in .gitignore, so these files are never committed.)
 *
 * Run (from the project folder):
 *   javac -d out src/*.java tests/*.java
 *   java -cp out GenerateBig
 */
public class GenerateBig {

    static final int LINES = 500_000;
    static final Path FOLDER = Path.of("out", "big");

    public static void main(String[] args) throws IOException {
        Files.createDirectories(FOLDER);
        Random random = new Random(2026);

        // 1. Mostly the same file, with a few thousand random edits.
        List<String> base = uniqueLines(random, LINES);
        save("few", base, mutate(random, base, 2_000, false));

        // 2. Many edits (50,000).
        save("many", base, mutate(random, base, 50_000, false));

        // 3. Two completely different files.
        save("different", uniqueLines(random, LINES), uniqueLines(random, LINES));

        // 4. Source-code-like lines: few distinct lines, lots of repeats.
        List<String> code = codeLines(random, LINES);
        save("code", code, mutate(random, code, 5_000, true));

        // 5. Highlight heavy: every 5th line has one character changed.
        List<String> changed = new ArrayList<>(base);
        for (int i = 0; i < changed.size(); i += 5) {
            changed.set(i, changed.get(i) + "!");
        }
        save("highlight", base, changed);

        System.out.println("Wrote test files to " + FOLDER.toAbsolutePath());
    }

    static List<String> uniqueLines(Random random, int count) {
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add("line " + i + " value=" + random.nextInt(1_000_000));
        }
        return lines;
    }

    static List<String> codeLines(Random random, int count) {
        String[] vocabulary = {"}", "", "    return x;", "    i++;", "{", "if (x > 0) {", "    // comment",
                "int x = 0;", "    y = y + 1;", "else {", "for (int i = 0; i < n; i++) {", "    print(x);"};
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(vocabulary[random.nextInt(vocabulary.length)]);
        }
        return lines;
    }

    /** Copy of the list with random deletes, inserts and edits. */
    static List<String> mutate(Random random, List<String> original, int changes, boolean codeLike) {
        // Choose positions first, then build the new list in one pass (fast).
        boolean[] touched = new boolean[original.size()];
        for (int c = 0; c < changes; c++) {
            touched[random.nextInt(original.size())] = true;
        }
        List<String> result = new ArrayList<>(original.size() + changes);
        for (int i = 0; i < original.size(); i++) {
            if (!touched[i]) {
                result.add(original.get(i));
                continue;
            }
            int kind = random.nextInt(3);
            String fresh = codeLike ? "    call" + random.nextInt(50) + "();" : "new line " + random.nextInt(1_000_000);
            if (kind == 0) {
                // delete: add nothing
            } else if (kind == 1) {
                result.add(fresh);                 // insert before
                result.add(original.get(i));
            } else {
                result.add(original.get(i) + "x"); // edit
            }
        }
        return result;
    }

    static void save(String name, List<String> a, List<String> b) throws IOException {
        Files.write(FOLDER.resolve(name + "_a.txt"), a, StandardCharsets.UTF_8);
        Files.write(FOLDER.resolve(name + "_b.txt"), b, StandardCharsets.UTF_8);
    }
}

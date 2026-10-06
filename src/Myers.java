/**
 * Myers' diff algorithm, linear-space version
 * (Eugene Myers, "An O(ND) Difference Algorithm and Its Variations", 1986, section 4b).
 * Main uses it for lines (Part A) and for the characters of a line pair (Part B).
 */
public class Myers {

    // a and b are int arrays (not Strings) so the same code works for everything.
    // For Part A, Main gives every line a number (an id). For Part B, the ints are
    // the characters (code points) of a line.
    private final int[] a;              // first sequence (old file)
    private final int[] b;              // second sequence (new file)

    // The answer is two boolean arrays instead of a list of edits. Each recursive
    // call only has to mark its own part, so there are no lists to build and join.
    // Main prints the diff straight from these flags, and Part B turns runs of
    // true flags directly into ranges.
    private final boolean[] deleted;    // deleted[i] = true means a[i] is removed
    private final boolean[] inserted;   // inserted[j] = true means b[j] is added

    // The two V arrays. V[k] = the furthest x reached on diagonal k (k = x - y).
    // forward[] is for the search that starts at (0,0), backward[] for the search
    // that starts at the end (n,m).
    // The index is k + offset because k can be negative, but an array index cannot.
    // They are created only once and reused by every recursive call, so we never
    // create or copy arrays inside the loops.
    private final int[] forward;
    private final int[] backward;
    private final int offset;

    // The only public method. It fills deleted[] and inserted[] (both must start
    // all false) with the smallest possible number of true flags.
    public static void diff(int[] a, int[] b, boolean[] deleted, boolean[] inserted) {
        Myers myers = new Myers(a, b, deleted, inserted);
        myers.compare(0, a.length, 0, b.length);   // diff the whole of a against the whole of b
    }

    // Array size 2 * max + 3: k can go from -max to +max, and we also read k-1 and
    // k+1, so we need room for -max-1 .. max+1.
    private Myers(int[] a, int[] b, boolean[] deleted, boolean[] inserted) {
        this.a = a;
        this.b = b;
        this.deleted = deleted;
        this.inserted = inserted;
        int max = a.length + b.length;     // the most edits we could ever need
        this.offset = max + 1;             // shifts k (which can be negative) to a valid index
        this.forward = new int[2 * max + 3];
        this.backward = new int[2 * max + 3];
    }

    // compare diffs the part a[aLo..aHi) against b[bLo..bHi) (hi is not included).
    // It is divide and conquer: find the middle snake, then solve the part before it
    // and the part after it in the same way.
    // This is the "linear space" idea: the simple version from class saves a copy of
    // V for every d so it can walk back, which needs about D*D memory (too much for
    // 500,000 lines). Here we only keep two V arrays, so the memory is O(n + m).
    private void compare(int aLo, int aHi, int bLo, int bHi) {

        // Skip equal items at the start: they are always kept, so there is nothing to
        // search. This is also needed for the recursion to stop. Without it, some
        // inputs (like "ab" vs "a") give an empty snake at the end, and the same
        // problem would be solved again forever.
        while (aLo < aHi && bLo < bHi && a[aLo] == b[bLo]) {
            aLo++;
            bLo++;
        }

        // Skip equal items at the end: they are also always kept. This one is only
        // for speed (real files often have the same ending).
        while (aLo < aHi && bLo < bHi && a[aHi - 1] == b[bHi - 1]) {
            aHi--;
            bHi--;
        }

        // If one side is now empty, everything on the other side changed and no
        // search is needed.
        if (aLo == aHi) {
            // nothing left in a, so every remaining item of b is an insert
            for (int j = bLo; j < bHi; j++) {
                inserted[j] = true;
            }
            return;
        }
        if (bLo == bHi) {
            // nothing left in b, so every remaining item of a is a delete
            for (int i = aLo; i < aHi; i++) {
                deleted[i] = true;
            }
            return;
        }

        // The snake itself is a run of equal items, so they are kept (their flags
        // stay false). We only need to solve the part before it and the part after it.
        int[] snake = middleSnake(aLo, aHi, bLo, bHi);
        int xStart = snake[0], yStart = snake[1], xEnd = snake[2], yEnd = snake[3];
        compare(aLo, xStart, bLo, yStart);   // the part before the snake
        compare(xEnd, aHi, yEnd, bHi);       // the part after the snake
    }

    // The edit graph: x walks along a (0..n), y walks along b (0..m).
    // Start at (0,0), end at (n,m).
    //      move right    (x+1)       = delete a[x]        cost 1
    //      move down     (y+1)       = insert b[y]        cost 1
    //      move diagonal (x+1, y+1)  = a[x] equals b[y]   cost 0  (a run of these is a "snake")
    // Diagonal k is the line x - y = k. A shortest path = the fewest edits.
    //
    // middleSnake searches forward from (0,0) and backward from (n,m) at the same
    // time, one round (d) at a time. When the two searches overlap, the snake found
    // there lies in the middle of a shortest path: the "middle snake".
    // It returns {xStart, yStart, xEnd, yEnd} of that snake (absolute positions).
    private int[] middleSnake(int aLo, int aHi, int bLo, int bHi) {
        int n = aHi - aLo;                  // length of the a part
        int m = bHi - bLo;                  // length of the b part

        // delta = the diagonal where the end point (n,m) lies.
        int delta = n - m;

        // Every edit changes k by 1, and we go from k = 0 to k = delta, so the total
        // number of edits D is odd when delta is odd and even when delta is even.
        // This tells us where the two searches can meet:
        // odd -> in the forward search (D = 2d - 1),
        // even -> in the backward search (D = 2d).
        boolean deltaIsOdd = (delta % 2 != 0);

        // D is never more than n + m (delete everything, insert everything), and each
        // search only needs half of D. (n + m + 1) / 2 is that half, rounded up.
        int maxD = (n + m + 1) / 2;

        // Fake starting values, so that round d = 0 starts exactly at the corner
        // (k = 0 reads V[1], so x = 0 and y = 0). They must be set every time, because
        // the arrays still hold old values from the previous call.
        forward[offset + 1] = 0;
        backward[offset + 1] = 0;

        for (int d = 0; d <= maxD; d++) {   // d = number of edits used so far

            // ---------- Forward search: from (0,0) towards (n,m) ----------

            // k goes up by 2: each edit changes k by exactly 1, so after d edits we can
            // only be on every second diagonal: -d, -d+2, ..., d.
            for (int k = -d; k <= d; k += 2) {

                // Down or right: we reach diagonal k either from k+1 (move down = insert,
                // x stays the same) or from k-1 (move right = delete, x + 1). We take the
                // one that got further. At k = -d there is no k-1, and at k = d there is
                // no k+1.
                // On a tie we move right, because that reaches one step further (V[k-1] + 1).
                // Keeping the furthest point on every diagonal is what keeps the diff
                // minimal. That is why the test uses a strict "<".
                int x;
                if (k == -d || (k != d && forward[offset + k - 1] < forward[offset + k + 1])) {
                    x = forward[offset + k + 1];          // move down
                } else {
                    x = forward[offset + k - 1] + 1;      // move right
                }
                int y = x - k;                            // because k = x - y
                int startX = x;                           // remember where the snake begins
                int startY = y;

                // Follow the snake: walk diagonally (for free) while the items are equal.
                // x < n and y < m stop it at the edge of the grid.
                while (x < n && y < m && a[aLo + x] == b[bLo + y]) {
                    x++;
                    y++;
                }
                forward[offset + k] = x;                  // save the furthest x on diagonal k

                // The backward search counts from the end: x' = n - x and y' = m - y.
                // So x' - y' = (n - m) - (x - y) = delta - k. It is the same line.
                int backK = delta - k;

                // The backward search has only finished d - 1 rounds so far, so only its
                // diagonals -(d - 1) .. d - 1 have real values.
                if (deltaIsOdd && backK >= -(d - 1) && backK <= d - 1) {

                    // backward[] says how far the backward search got from the end, so its
                    // real x is n - backward[...]. If our x has reached it
                    // (x >= n - backward), the two searches overlap on this diagonal.
                    if (x + backward[offset + backK] >= n) {
                        // Found it: the snake we just followed is the middle snake.
                        return new int[] {aLo + startX, bLo + startY, aLo + x, bLo + y};
                    }
                }
            }

            // ---------- Backward search: from (n,m) towards (0,0) ----------

            // The same code as the forward search, but it reads a and b from the end
            // (a[aHi-1-x], b[bHi-1-y]). Here x and y mean "how many items from the end".
            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d && backward[offset + k - 1] < backward[offset + k + 1])) {
                    x = backward[offset + k + 1];         // move up (towards the start)
                } else {
                    x = backward[offset + k - 1] + 1;     // move left (towards the start)
                }
                int y = x - k;
                int startX = x;                           // remember where the snake begins
                int startY = y;

                // follow the snake backwards while the items are equal
                while (x < n && y < m && a[aHi - 1 - x] == b[bHi - 1 - y]) {
                    x++;
                    y++;
                }
                backward[offset + k] = x;                 // save how far we got on diagonal k

                // The forward search has already finished round d, so all of its diagonals
                // -d .. d have real values.
                int forwardK = delta - k;
                if (!deltaIsOdd && forwardK >= -d && forwardK <= d) {
                    if (forward[offset + forwardK] + x >= n) {
                        // x and y count from the end here, so we convert them back to
                        // normal positions (aHi - x, bHi - y) before returning the snake.
                        return new int[] {aHi - x, bHi - y, aHi - startX, bHi - startY};
                    }
                }
            }
        }

        // Never reached: the two searches always meet by d = maxD. It is only a safety net.
        throw new IllegalStateException("middle snake not found");
    }
}

/**
 * Myers' diff algorithm, linear-space version.
 * (Eugene Myers, "An O(ND) Difference Algorithm and Its Variations", 1986, section 4b.)
 *
 * Input:  two sequences of ints, a and b.
 *         (Main turns lines into ints. For Part B the ints are characters.)
 * Output: deleted[i] = true  if a[i] is NOT kept (it is deleted)
 *         inserted[j] = true if b[j] is NOT kept (it is inserted)
 * The number of true flags is the minimum possible (fewest deletions + insertions).
 *
 * ---------------------------------------------------------------------------
 * The edit graph (same picture as in class)
 * ---------------------------------------------------------------------------
 * x walks along a (0..n), y walks along b (0..m). Start at (0,0), end at (n,m).
 *   move right    (x+1)       = delete a[x]          cost 1
 *   move down     (y+1)       = insert b[y]          cost 1
 *   move diagonal (x+1, y+1)  = a[x] equals b[y]     cost 0  (a "snake" is a run of these)
 * Diagonal k is the line x - y = k.
 * V[k] = the furthest x we can reach on diagonal k using d edits.
 *
 * ---------------------------------------------------------------------------
 * Why "linear space"?
 * ---------------------------------------------------------------------------
 * The simple version saves a copy of V for every d, so it can walk back and
 * print the path. That needs about D*D memory, which is too much for big files.
 *
 * Instead we search from BOTH ends at the same time:
 *   - forward  from (0,0), using array forward[]
 *   - backward from (n,m), using array backward[]
 * When the two searches overlap, we have found the "middle snake": a piece of
 * a shortest path that lies in the middle. Then we solve the part before it and
 * the part after it the same way (recursion). Only two V arrays are needed.
 */
public class Myers {

    private final int[] a;              // first sequence (old file)
    private final int[] b;              // second sequence (new file)
    private final boolean[] deleted;    // answer: deleted[i] = true means a[i] is removed
    private final boolean[] inserted;   // answer: inserted[j] = true means b[j] is added

    // The two V arrays. Index = k + offset, because k can be negative.
    // They are created once and reused by every recursive call.
    private final int[] forward;
    private final int[] backward;
    private final int offset;

    /** Fill deleted[] and inserted[] (both must start all false). */
    public static void diff(int[] a, int[] b, boolean[] deleted, boolean[] inserted) {
        Myers myers = new Myers(a, b, deleted, inserted);
        myers.compare(0, a.length, 0, b.length);   // diff the whole of a against the whole of b
    }

    private Myers(int[] a, int[] b, boolean[] deleted, boolean[] inserted) {
        this.a = a;
        this.b = b;
        this.deleted = deleted;
        this.inserted = inserted;
        int max = a.length + b.length;     // the most edits we could ever need
        this.offset = max + 1;             // shifts k (which can be negative) to a valid index
        this.forward = new int[2 * max + 3];   // big enough for every k from -max-1 to max+1
        this.backward = new int[2 * max + 3];
    }

    /**
     * Diff the part a[aLo..aHi) against b[bLo..bHi)  (hi is not included).
     */
    private void compare(int aLo, int aHi, int bLo, int bHi) {
        // 1. Equal items at the start are always kept. Skip them.
        //    This step is also important for the recursion: without it, some inputs
        //    (like "ab" vs "a") would give an empty snake and repeat forever.
        while (aLo < aHi && bLo < bHi && a[aLo] == b[bLo]) {
            aLo++;
            bLo++;
        }
        // 2. Equal items at the end are always kept. Skip them (this makes it faster).
        while (aLo < aHi && bLo < bHi && a[aHi - 1] == b[bHi - 1]) {
            aHi--;
            bHi--;
        }

        // 3. If one side is empty, everything on the other side changed.
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

        // 4. Find the middle snake, then solve the part before it and the part after it.
        //    The snake itself is all equal items, so they are kept (flags stay false).
        int[] snake = middleSnake(aLo, aHi, bLo, bHi);
        int xStart = snake[0], yStart = snake[1], xEnd = snake[2], yEnd = snake[3];
        compare(aLo, xStart, bLo, yStart);   // the part before the snake
        compare(xEnd, aHi, yEnd, bHi);       // the part after the snake
    }

    /**
     * Search forward from the top-left corner and backward from the bottom-right
     * corner until the two searches meet.
     * Returns {xStart, yStart, xEnd, yEnd} of the middle snake (absolute positions).
     */
    private int[] middleSnake(int aLo, int aHi, int bLo, int bHi) {
        int n = aHi - aLo;                  // length of the a part
        int m = bHi - bLo;                  // length of the b part
        int delta = n - m;                  // the diagonal where (n,m) lies
        // The total number of edits D is always odd when delta is odd, and even when
        // delta is even. So we know which search will meet the other one first.
        boolean deltaIsOdd = (delta % 2 != 0);
        int maxD = (n + m + 1) / 2;         // each search needs at most half the edits

        // Fake starting values so that round d = 0 starts exactly at the corner.
        // We must set them every time, because the arrays still hold old values
        // from the previous call.
        forward[offset + 1] = 0;
        backward[offset + 1] = 0;

        for (int d = 0; d <= maxD; d++) {   // d = number of edits used so far

            // ---------- Forward search: from (0,0) towards (n,m) ----------
            // After d edits we can only be on every second diagonal: -d, -d+2, ..., d.
            for (int k = -d; k <= d; k += 2) {
                // Come from diagonal k+1 (move down) or k-1 (move right)?
                // Take the one that got further.
                // (On a tie we move right, because right reaches one step further.)
                int x;
                if (k == -d || (k != d && forward[offset + k - 1] < forward[offset + k + 1])) {
                    x = forward[offset + k + 1];          // move down
                } else {
                    x = forward[offset + k - 1] + 1;      // move right
                }
                int y = x - k;                            // because k = x - y
                int startX = x;                           // remember where the snake begins
                int startY = y;

                // Follow the snake: walk diagonally while the items are equal.
                while (x < n && y < m && a[aLo + x] == b[bLo + y]) {
                    x++;
                    y++;
                }
                forward[offset + k] = x;                  // save the furthest x on diagonal k

                // Overlap check (only when delta is odd).
                // Forward diagonal k is the same line as backward diagonal (delta - k).
                int backK = delta - k;
                // The backward search has only finished d-1 rounds, so only its
                // diagonals -(d-1) .. d-1 have real values.
                if (deltaIsOdd && backK >= -(d - 1) && backK <= d - 1) {
                    // backward[] stores how far the backward search got, counted from the end.
                    // So its real x is n - backward[...]. If our x has reached it, they meet.
                    if (x + backward[offset + backK] >= n) {
                        // Found it: the snake we just followed is the middle snake.
                        return new int[] {aLo + startX, bLo + startY, aLo + x, bLo + y};
                    }
                }
            }

            // ---------- Backward search: from (n,m) towards (0,0) ----------
            // Same code, but we read a and b from the end (a[aHi-1-x], b[bHi-1-y]).
            // Here x and y mean "how many items from the end".
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

                // Follow the snake backwards while the items are equal.
                while (x < n && y < m && a[aHi - 1 - x] == b[bHi - 1 - y]) {
                    x++;
                    y++;
                }
                backward[offset + k] = x;                 // save how far we got on diagonal k

                // Overlap check (only when delta is even).
                // The forward search has already finished round d, so -d .. d are valid.
                int forwardK = delta - k;
                if (!deltaIsOdd && forwardK >= -d && forwardK <= d) {
                    if (forward[offset + forwardK] + x >= n) {
                        // Convert "distance from the end" back to normal positions.
                        return new int[] {aHi - x, bHi - y, aHi - startX, bHi - startY};
                    }
                }
            }
        }
        // Never happens: the searches always meet by d = maxD.
        throw new IllegalStateException("middle snake not found");
    }
}

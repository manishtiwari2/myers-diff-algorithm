import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * One line of a file.
 *
 * It does not copy the bytes. It only remembers where the line is inside the
 * file's byte array: data[start .. end)  (end is not included, '\n' is not part of it).
 * This saves a lot of memory on files with 500,000 lines.
 *
 * Two lines are equal when their bytes are exactly equal, so a Line can be used
 * as a key in a HashMap. (That is why equals and hashCode are written below.)
 */
public class Line {

    private final byte[] data;
    private final int start;
    private final int end;
    private final int hash;    // computed once, because HashMap asks for it often

    public Line(byte[] data, int start, int end) {
        this.data = data;
        this.start = start;
        this.end = end;
        int h = 0;
        for (int i = start; i < end; i++) {
            h = 31 * h + data[i];   // same formula that String.hashCode uses
        }
        this.hash = h;
    }

    /** Write the exact bytes of the line (without '\n'). */
    public void writeTo(OutputStream out) throws IOException {
        out.write(data, start, end - start);
    }

    /** The line as text. Only used in Part B, where files are always valid UTF-8. */
    public String toText() {
        return new String(data, start, end - start, StandardCharsets.UTF_8);
    }

    /** A copy of the line's bytes (used by the tests). */
    public byte[] toBytes() {
        return Arrays.copyOfRange(data, start, end);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Line)) {
            return false;
        }
        Line that = (Line) other;
        return Arrays.equals(this.data, this.start, this.end, that.data, that.start, that.end);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}

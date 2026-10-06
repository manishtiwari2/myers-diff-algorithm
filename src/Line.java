import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * One line of a file: the bytes data[start .. end)  ('\n' is not part of it).
 */
public class Line {

    // A Line does not copy its bytes. It only remembers where the line is inside the
    // file's byte array. With 500,000 lines, copying every line would use a lot more memory.
    private final byte[] data;   // the whole file (shared by all its lines)
    private final int start;     // first byte of this line
    private final int end;       // one past the last byte of this line

    // The hash is stored because the HashMap asks for it often, so we compute it once.
    private final int hash;

    // The hash uses the same formula as String.hashCode: h = 31 * h + next byte.
    // Equal lines always get the same hash.
    public Line(byte[] data, int start, int end) {
        this.data = data;
        this.start = start;
        this.end = end;
        int h = 0;
        for (int i = start; i < end; i++) {
            h = 31 * h + data[i];
        }
        this.hash = h;
    }

    // Writes the exact bytes of the line, including '\r' and bytes that are not
    // valid UTF-8.
    public void writeTo(OutputStream out) throws IOException {
        out.write(data, start, end - start);
    }

    // Turns the line into text. Only used in Part B, to get its characters. The rules
    // say highlight files are always valid UTF-8, so this is safe there.
    public String toText() {
        return new String(data, start, end - start, StandardCharsets.UTF_8);
    }

    // A copy of the line's bytes (only used by my own test code, not by the program).
    public byte[] toBytes() {
        return Arrays.copyOfRange(data, start, end);
    }

    // equals and hashCode are needed because a Line is used as a key in a HashMap (to
    // give lines ids). By default Java only says two objects are equal if they are the
    // same object. We want two lines to be equal when they have exactly the same bytes,
    // even if they come from different files.
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Line)) {
            return false;
        }
        Line that = (Line) other;
        // compare this line's bytes with the other line's bytes
        return Arrays.equals(this.data, this.start, this.end, that.data, that.start, that.end);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}

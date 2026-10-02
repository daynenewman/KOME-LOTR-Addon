package kome.common.data;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Validated cell annotations owned by one raster snapshot, never a competing border authority. */
public final class KOMETileExclusions {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_ZONES = 65535;
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");
    private static final Pattern COORDINATE = Pattern.compile("0|[1-9][0-9]{0,9}");
    private final char[] cells; // Allocated only when at least one cell is explicitly classified.
    private final Zone[] zones;
    public final int classifiedCells;
    public final String diagnostic;

    /** Open vocabulary: type is metadata, not a permanent unit traversal policy. */
    public static final class Zone {
        public final String id, type, reason;
        final String diagnostic;
        private Zone(String id, String type, String reason) {
            this.id = id; this.type = type; this.reason = reason;
            diagnostic = "Explicit exclusion: " + id + ":" + type + " (" + reason + ")";
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Zone)) return false;
            Zone z = (Zone) other;
            return id.equals(z.id) && type.equals(z.type) && reason.equals(z.reason);
        }
        @Override public int hashCode() { return Objects.hash(id, type, reason); }
        @Override public String toString() { return id + ":" + type + " (" + reason + ")"; }
    }

    private KOMETileExclusions(char[] cells, Zone[] zones, int count, String diagnostic) {
        this.cells = cells; this.zones = zones; classifiedCells = count; this.diagnostic = diagnostic;
    }
    static KOMETileExclusions notLoaded() {
        return new KOMETileExclusions(null, new Zone[1], 0, "Exclusion metadata not loaded (geometry-only snapshot)");
    }
    Zone at(int index) { return cells == null ? null : zones[cells[index]]; }
    public int zoneCount() { return zones.length - 1; }
    public long cellIndexBytes() { return cells == null ? 0L : 2L * cells.length; }

    static byte[] readBounded(InputStream input, int limit, String resource) throws IOException {
        if (input == null) throw new IOException("Missing " + resource + " resource");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096]; int count;
        while ((count = input.read(buffer)) != -1) {
            if ((long) bytes.size() + count > limit) throw new IOException(resource + " exceeds byte limit");
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }
    static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder text = new StringBuilder(64);
            for (byte b : hash) text.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return text.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    static KOMETileExclusions load(InputStream input, String maskHash, int width, int height,
            int[] tileCells) throws IOException {
        byte[] bytes = readBounded(input, MAX_BYTES, "tile exclusion metadata");
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) { throw new IOException("Invalid exclusion UTF-8", e); }
        BufferedReader reader = new BufferedReader(new StringReader(text));
        List<String> lines = new ArrayList<String>(); String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            lines.add(line);
        }
        if (lines.size() < 4 || !lines.get(0).equals("schema=1")
                || !lines.get(1).equals("width=" + width) || !lines.get(2).equals("height=" + height)
                || !lines.get(3).equals("mask_sha256=" + maskHash))
            throw new IOException("Exclusion schema, dimensions or mask SHA-256 mismatch");
        Map<String, Integer> indices = new LinkedHashMap<String, Integer>();
        List<Zone> zones = new ArrayList<Zone>(); zones.add(null);
        boolean runsStarted = false; char[] cells = null; int classified = 0;
        boolean[] used = new boolean[MAX_ZONES + 1];
        for (int n = 4; n < lines.size(); n++) {
            String[] parts = lines.get(n).split("\t", -1);
            if (parts.length == 4 && parts[0].equals("zone") && !runsStarted) {
                String id = parts[1], type = parts[2], reason = parts[3];
                if (!KEY.matcher(id).matches() || !KEY.matcher(type).matches()
                        || reason.trim().isEmpty() || reason.length() > 256
                        || reason.chars().anyMatch(c -> Character.isISOControl(c))
                        || indices.containsKey(id) || indices.size() >= MAX_ZONES)
                    throw new IOException("Invalid or duplicate exclusion zone at record " + (n + 1));
                indices.put(id, zones.size()); zones.add(new Zone(id, type, reason));
            } else if (parts.length == 5 && parts[0].equals("run")) {
                runsStarted = true;
                int y = number(parts[1]), start = number(parts[2]), end = number(parts[3]);
                Integer zone = indices.get(parts[4]);
                if (y < 0 || y >= height || start < 0 || start >= end || end > width || zone == null)
                    throw new IOException("Invalid exclusion run or unknown zone at record " + (n + 1));
                if (cells == null) cells = new char[tileCells.length];
                for (int x = start; x < end; x++) {
                    int index = y * width + x;
                    if ((tileCells[index] & 0xFFFF) != 0)
                        throw new IOException("Exclusion overlaps active tile at " + x + "," + y);
                    if (cells[index] != 0) throw new IOException("Overlapping exclusion runs at " + x + "," + y);
                    cells[index] = (char) zone.intValue(); classified++;
                }
                used[zone] = true;
            } else throw new IOException("Invalid exclusion record " + (n + 1));
        }
        for (int z = 1; z < zones.size(); z++)
            if (!used[z]) throw new IOException("Exclusion zone has no cells: " + zones.get(z).id);
        return new KOMETileExclusions(cells, zones.toArray(new Zone[0]), classified,
            "Validated exclusions: zones=" + (zones.size() - 1) + " cells=" + classified);
    }
    private static int number(String value) throws IOException {
        if (!COORDINATE.matcher(value).matches()) throw new IOException("Invalid exclusion coordinate: " + value);
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { throw new IOException("Exclusion coordinate overflow", e); }
    }
}

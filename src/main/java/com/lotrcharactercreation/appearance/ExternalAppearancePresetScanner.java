package com.lotrcharactercreation.appearance;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;

import com.lotrcharactercreation.race.PlayerRace;

/** Discovers external preset metadata without loading any client classes. */
public final class ExternalAppearancePresetScanner {

    private static final int MAX_FILENAME_STEM_LENGTH = 64;
    private static final Pattern VALID_FILENAME_STEM = Pattern.compile("[a-z0-9_-]+");
    private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };

    private ExternalAppearancePresetScanner() {}

    public static List<AppearancePreset> scan(File root, Logger logger) {
        try {
            if (root == null || !root.isDirectory()) {
                return Collections.emptyList();
            }
        } catch (SecurityException exception) {
            warn(logger, "Could not access custom skin root " + root + ": " + exception.getMessage());
            return Collections.emptyList();
        }

        File canonicalRoot;
        try {
            canonicalRoot = root.getCanonicalFile();
        } catch (IOException | SecurityException exception) {
            warn(logger, "Could not resolve custom skin root " + root + ": " + exception.getMessage());
            return Collections.emptyList();
        }

        List<Candidate> candidates = new ArrayList<Candidate>();
        for (File raceDirectory : listChildren(canonicalRoot, logger)) {
            if (!raceDirectory.isDirectory()) {
                continue;
            }
            if (!isSafeEntry(canonicalRoot, raceDirectory, logger)) {
                continue;
            }

            PlayerRace race = PlayerRace.findBySerializedId(raceDirectory.getName());
            if (race == null) {
                warn(logger, "Skipping unknown custom skin race directory: " + raceDirectory.getName());
                continue;
            }
            scanSexDirectories(canonicalRoot, raceDirectory, race, candidates, logger);
        }

        Collections.sort(candidates, new Comparator<Candidate>() {

            @Override
            public int compare(Candidate first, Candidate second) {
                return first.relativePath.compareTo(second.relativePath);
            }
        });
        return createUniquePresets(candidates, logger);
    }

    public static boolean hasExpectedDimensions(PlayerRace race, int width, int height) {
        if (race == PlayerRace.ORC || race == PlayerRace.URUK_HAI) {
            return width == 64 && height == 32;
        }
        return race == PlayerRace.MAN || race == PlayerRace.DWARF || race == PlayerRace.ELF || race == PlayerRace.HOBBIT
            ? width == 64 && height == 64
            : false;
    }

    private static void scanSexDirectories(File root, File raceDirectory, PlayerRace race, List<Candidate> candidates,
        Logger logger) {
        for (File sexDirectory : listChildren(raceDirectory, logger)) {
            if (!sexDirectory.isDirectory()) {
                continue;
            }
            if (!isSafeEntry(root, sexDirectory, logger)) {
                continue;
            }

            PlayerSex sex = PlayerSex.findBySerializedId(sexDirectory.getName());
            if (!AppearancePresetRegistry.isSexValidForRace(race, sex)) {
                warn(
                    logger,
                    "Skipping invalid custom skin sex directory for " + race.getSerializedId()
                        + ": "
                        + sexDirectory.getName());
                continue;
            }
            scanGroupDirectories(root, sexDirectory, race, sex, candidates, logger);
        }
    }

    private static void scanGroupDirectories(File root, File sexDirectory, PlayerRace race, PlayerSex sex,
        List<Candidate> candidates, Logger logger) {
        for (File groupDirectory : listChildren(sexDirectory, logger)) {
            if (!groupDirectory.isDirectory()) {
                continue;
            }
            if (!isSafeEntry(root, groupDirectory, logger)) {
                continue;
            }

            String groupToken = groupDirectory.getName();
            GroupParseResult parsedGroup = parseGroup(race, groupToken);
            if (!parsedGroup.valid) {
                warn(logger, "Skipping unknown custom skin group for " + race.getSerializedId() + ": " + groupToken);
                continue;
            }
            scanPngFiles(root, groupDirectory, race, sex, groupToken, parsedGroup.groupId, candidates, logger);
        }
    }

    private static void scanPngFiles(File root, File groupDirectory, PlayerRace race, PlayerSex sex, String groupToken,
        String groupId, List<Candidate> candidates, Logger logger) {
        for (File file : listChildren(groupDirectory, logger)) {
            if (file.isDirectory()) {
                warn(logger, "Skipping unexpected nested custom skin directory: " + relativeTo(root, file));
                continue;
            }
            if (!file.getName()
                .endsWith(".png")) {
                if (file.getName()
                    .toLowerCase(Locale.ROOT)
                    .endsWith(".png")) {
                    warn(logger, "Custom skin filenames must use lowercase .png: " + relativeTo(root, file));
                }
                continue;
            }
            if (!isSafeEntry(root, file, logger)) {
                continue;
            }

            String stem = file.getName()
                .substring(
                    0,
                    file.getName()
                        .length() - 4);
            if (stem.isEmpty() || stem.length() > MAX_FILENAME_STEM_LENGTH
                || !VALID_FILENAME_STEM.matcher(stem)
                    .matches()) {
                warn(logger, "Skipping invalid custom skin filename: " + relativeTo(root, file));
                continue;
            }

            int[] dimensions = readPngDimensions(file);
            if (dimensions == null) {
                warn(logger, "Skipping malformed custom skin PNG: " + relativeTo(root, file));
                continue;
            }
            if (!hasExpectedDimensions(race, dimensions[0], dimensions[1])) {
                String expected = race == PlayerRace.ORC || race == PlayerRace.URUK_HAI ? "64x32" : "64x64";
                warn(
                    logger,
                    "Skipping custom skin with dimensions " + dimensions[0]
                        + "x"
                        + dimensions[1]
                        + " (expected "
                        + expected
                        + "): "
                        + relativeTo(root, file));
                continue;
            }

            String relativePath = race.getSerializedId() + "/"
                + sex.getSerializedId()
                + "/"
                + groupToken
                + "/"
                + file.getName();
            String id = "custom_" + race
                .getSerializedId() + "_" + sex.getSerializedId() + "_" + groupToken + "_" + stem;
            candidates.add(new Candidate(id, race, sex, groupId, displayName(stem), relativePath));
        }
    }

    private static GroupParseResult parseGroup(PlayerRace race, String groupToken) {
        if (race == PlayerRace.MAN) {
            ManAppearanceGroup group = ManAppearanceGroup.findBySerializedId(groupToken);
            return group == null ? GroupParseResult.INVALID : new GroupParseResult(group.getSerializedId());
        }
        if (race == PlayerRace.ELF) {
            ElfAppearanceGroup group = ElfAppearanceGroup.findBySerializedId(groupToken);
            return group == null ? GroupParseResult.INVALID : new GroupParseResult(group.getSerializedId());
        }
        if (race == PlayerRace.DWARF) {
            for (DwarfAppearanceGroup group : DwarfAppearanceGroup.values()) {
                if (group.getSerializedId()
                    .equals(groupToken)) {
                    return new GroupParseResult(group.getSerializedId());
                }
            }
            return GroupParseResult.INVALID;
        }
        if (race == PlayerRace.HOBBIT) {
            return "default".equals(groupToken) ? new GroupParseResult(null) : GroupParseResult.INVALID;
        }
        if (race == PlayerRace.ORC) {
            OrcAppearanceGroup group = OrcAppearanceGroup.findBySerializedId(groupToken);
            return group == null ? GroupParseResult.INVALID : new GroupParseResult(group.getSerializedId());
        }
        if (race == PlayerRace.URUK_HAI) {
            UrukHaiAppearanceGroup group = UrukHaiAppearanceGroup.findBySerializedId(groupToken);
            return group == null ? GroupParseResult.INVALID : new GroupParseResult(group.getSerializedId());
        }
        return GroupParseResult.INVALID;
    }

    private static List<AppearancePreset> createUniquePresets(List<Candidate> candidates, Logger logger) {
        Map<String, Candidate> uniqueCandidates = new HashMap<String, Candidate>();
        Set<String> conflictingIds = new HashSet<String>();
        for (Candidate candidate : candidates) {
            Candidate previous = uniqueCandidates.get(candidate.id);
            if (previous == null && !conflictingIds.contains(candidate.id)) {
                uniqueCandidates.put(candidate.id, candidate);
            } else {
                uniqueCandidates.remove(candidate.id);
                conflictingIds.add(candidate.id);
                String previousPath = previous == null ? "another conflicting path" : previous.relativePath;
                warn(
                    logger,
                    "Skipping conflicting custom appearance preset ID " + candidate.id
                        + ": "
                        + previousPath
                        + " and "
                        + candidate.relativePath);
            }
        }

        List<AppearancePreset> presets = new ArrayList<AppearancePreset>();
        for (Candidate candidate : candidates) {
            if (uniqueCandidates.get(candidate.id) != candidate) {
                continue;
            }
            presets.add(
                new AppearancePreset(
                    candidate.id,
                    candidate.race,
                    candidate.sex,
                    candidate.groupId,
                    AppearanceSourceType.EXTERNAL,
                    candidate.displayName,
                    null,
                    candidate.relativePath));
        }
        return Collections.unmodifiableList(presets);
    }

    private static File[] listChildren(File directory, Logger logger) {
        File[] children;
        try {
            children = directory.listFiles();
        } catch (SecurityException exception) {
            warn(logger, "Could not read custom skin directory " + directory + ": " + exception.getMessage());
            return new File[0];
        }
        if (children == null) {
            warn(logger, "Could not read custom skin directory: " + directory);
            return new File[0];
        }
        return children;
    }

    private static boolean isSafeEntry(File root, File entry, Logger logger) {
        try {
            if (Files.isSymbolicLink(entry.toPath())) {
                warn(logger, "Skipping symbolic link in custom skins: " + relativeTo(root, entry));
                return false;
            }
            if (!entry.getCanonicalFile()
                .toPath()
                .startsWith(root.toPath())) {
                warn(logger, "Skipping custom skin path outside the configured root: " + entry);
                return false;
            }
            return true;
        } catch (IOException | SecurityException exception) {
            warn(logger, "Could not validate custom skin path " + entry + ": " + exception.getMessage());
            return false;
        }
    }

    private static int[] readPngDimensions(File file) {
        byte[] header = new byte[24];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < header.length) {
                int count = input.read(header, offset, header.length - offset);
                if (count < 0) {
                    return null;
                }
                offset += count;
            }
        } catch (IOException | SecurityException exception) {
            return null;
        }

        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (header[index] != PNG_SIGNATURE[index]) {
                return null;
            }
        }
        if (readInt(header, 8) != 13 || header[12] != 'I'
            || header[13] != 'H'
            || header[14] != 'D'
            || header[15] != 'R') {
            return null;
        }

        int width = readInt(header, 16);
        int height = readInt(header, 20);
        return width > 0 && height > 0 ? new int[] { width, height } : null;
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 24 | (bytes[offset + 1] & 0xFF) << 16
            | (bytes[offset + 2] & 0xFF) << 8
            | bytes[offset + 3] & 0xFF;
    }

    private static String displayName(String stem) {
        StringBuilder displayName = new StringBuilder();
        for (String word : stem.split("[_-]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (displayName.length() > 0) {
                displayName.append(' ');
            }
            displayName.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                displayName.append(word.substring(1));
            }
        }
        return displayName.toString();
    }

    private static String relativeTo(File root, File entry) {
        try {
            return root.toPath()
                .relativize(
                    entry.getCanonicalFile()
                        .toPath())
                .toString()
                .replace(File.separatorChar, '/');
        } catch (IOException | IllegalArgumentException | SecurityException exception) {
            return entry.toString();
        }
    }

    private static void warn(Logger logger, String message) {
        if (logger != null) {
            logger.warn(message);
        }
    }

    private static final class Candidate {

        private final String id;
        private final PlayerRace race;
        private final PlayerSex sex;
        private final String groupId;
        private final String displayName;
        private final String relativePath;

        private Candidate(String id, PlayerRace race, PlayerSex sex, String groupId, String displayName,
            String relativePath) {
            this.id = id;
            this.race = race;
            this.sex = sex;
            this.groupId = groupId;
            this.displayName = displayName;
            this.relativePath = relativePath;
        }
    }

    private static final class GroupParseResult {

        private static final GroupParseResult INVALID = new GroupParseResult(false, null);

        private final boolean valid;
        private final String groupId;

        private GroupParseResult(String groupId) {
            this(true, groupId);
        }

        private GroupParseResult(boolean valid, String groupId) {
            this.valid = valid;
            this.groupId = groupId;
        }
    }
}

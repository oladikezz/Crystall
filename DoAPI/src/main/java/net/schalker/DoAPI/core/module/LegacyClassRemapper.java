package net.schalker.DoAPI.core.module;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

final class LegacyClassRemapper {

    private static final String LEGACY_ROOT = "net.schalker.SMPS.";
    private static final String CURRENT_ROOT = "net.schalker.DoAPI.";

    private static final byte[] MARKER_SLASH = ascii("net/schalker/SMPS/");
    private static final byte[] MARKER_DOT = ascii("net.schalker.SMPS.");

    private static final byte[][] FIND = {
            ascii("net/schalker/SMPS/core"),
            ascii("net/schalker/SMPS/api"),
            ascii("net/schalker/SMPS/SMPS"),
            ascii("net.schalker.SMPS.core"),
            ascii("net.schalker.SMPS.api"),
            ascii("net.schalker.SMPS.SMPS")
    };

    private static final byte[][] REPLACE = {
            ascii("net/schalker/DoAPI/core"),
            ascii("net/schalker/DoAPI/api"),
            ascii("net/schalker/DoAPI/DoAPI"),
            ascii("net.schalker.DoAPI.core"),
            ascii("net.schalker.DoAPI.api"),
            ascii("net.schalker.DoAPI.DoAPI")
    };

    private static final byte[] LEGACY_PLUGIN_NAME = ascii("SMPS");
    private static final byte[] CURRENT_PLUGIN_NAME = ascii("DoAPI");

    private LegacyClassRemapper() {
    }

    static String mapClassName(String name) {
        if (name.startsWith(LEGACY_ROOT + "core.") || name.startsWith(LEGACY_ROOT + "api.")) {
            return CURRENT_ROOT + name.substring(LEGACY_ROOT.length());
        }
        if (name.equals(LEGACY_ROOT + "SMPS")) {
            return CURRENT_ROOT + "DoAPI";
        }
        return name;
    }

    static boolean isLegacyName(String name) {
        return !mapClassName(name).equals(name);
    }

    static boolean hasLegacyCoreReference(byte[] classBytes) {
        for (byte[] pattern : FIND) {
            if (indexOf(classBytes, pattern) >= 0) {
                return true;
            }
        }
        return false;
    }

    static byte[] remap(byte[] classBytes) {
        if (classBytes.length < 10) {
            return classBytes;
        }
        if (indexOf(classBytes, MARKER_SLASH) < 0 && indexOf(classBytes, MARKER_DOT) < 0) {
            return classBytes;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(classBytes.length + 256);
        out.write(classBytes, 0, 8);

        int constantPoolCount = readU2(classBytes, 8);
        writeU2(out, constantPoolCount);

        int position = 10;
        int index = 1;
        boolean changed = false;

        while (index < constantPoolCount) {
            if (position >= classBytes.length) {
                return classBytes;
            }
            int tag = classBytes[position] & 0xFF;

            switch (tag) {
                case 1 -> {
                    int length = readU2(classBytes, position + 1);
                    int from = position + 3;
                    int to = from + length;
                    if (to > classBytes.length) {
                        return classBytes;
                    }
                    byte[] value = Arrays.copyOfRange(classBytes, from, to);
                    byte[] mapped = mapUtf8(value);
                    out.write(1);
                    writeU2(out, mapped.length);
                    out.write(mapped, 0, mapped.length);
                    if (mapped != value) {
                        changed = true;
                    }
                    position = to;
                }
                case 5, 6 -> {
                    out.write(classBytes, position, 9);
                    position += 9;
                    index++;
                }
                case 3, 4, 9, 10, 11, 12, 17, 18 -> {
                    out.write(classBytes, position, 5);
                    position += 5;
                }
                case 15 -> {
                    out.write(classBytes, position, 4);
                    position += 4;
                }
                case 7, 8, 16, 19, 20 -> {
                    out.write(classBytes, position, 3);
                    position += 3;
                }
                default -> {
                    return classBytes;
                }
            }
            index++;
        }

        if (!changed) {
            return classBytes;
        }

        out.write(classBytes, position, classBytes.length - position);
        return out.toByteArray();
    }

    private static byte[] mapUtf8(byte[] value) {
        if (Arrays.equals(value, LEGACY_PLUGIN_NAME)) {
            return CURRENT_PLUGIN_NAME;
        }

        byte[] result = value;
        for (int i = 0; i < FIND.length; i++) {
            result = replaceAll(result, FIND[i], REPLACE[i]);
        }
        return result;
    }

    private static byte[] replaceAll(byte[] source, byte[] find, byte[] replacement) {
        int at = indexOf(source, find);
        if (at < 0) {
            return source;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(source.length + 32);
        int cursor = 0;
        while (at >= 0) {
            out.write(source, cursor, at - cursor);
            out.write(replacement, 0, replacement.length);
            cursor = at + find.length;
            at = indexOf(source, find, cursor);
        }
        out.write(source, cursor, source.length - cursor);
        return out.toByteArray();
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        return indexOf(haystack, needle, 0);
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        int limit = haystack.length - needle.length;
        outer:
        for (int i = Math.max(from, 0); i <= limit; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static int readU2(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static void writeU2(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static byte[] ascii(String text) {
        return text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
}

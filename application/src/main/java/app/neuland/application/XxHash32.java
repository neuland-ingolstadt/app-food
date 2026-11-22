package app.neuland.application;

import java.nio.charset.StandardCharsets;

/** Minimal implementation of the xxHash32 algorithm (seed 0 by default). */
final class XxHash32 {

    private static final int PRIME1 = 0x9E3779B1;
    private static final int PRIME2 = 0x85EBCA77;
    private static final int PRIME3 = 0xC2B2AE3D;
    private static final int PRIME4 = 0x27D4EB2F;
    private static final int PRIME5 = 0x165667B1;

    private XxHash32() {
    }

    static int hash(String input) {
        return hash(input.getBytes(StandardCharsets.UTF_8), 0);
    }

    static int hash(byte[] data, int seed) {
        int length = data.length;
        int offset = 0;
        int h32;

        if (length >= 16) {
            int v1 = seed + PRIME1 + PRIME2;
            int v2 = seed + PRIME2;
            int v3 = seed;
            int v4 = seed - PRIME1;
            int limit = length - 16;
            while (offset <= limit) {
                v1 = round(v1, readIntLE(data, offset));
                v2 = round(v2, readIntLE(data, offset + 4));
                v3 = round(v3, readIntLE(data, offset + 8));
                v4 = round(v4, readIntLE(data, offset + 12));
                offset += 16;
            }
            h32 = Integer.rotateLeft(v1, 1) + Integer.rotateLeft(v2, 7)
                    + Integer.rotateLeft(v3, 12) + Integer.rotateLeft(v4, 18);
        } else {
            h32 = seed + PRIME5;
        }

        h32 += length;

        while (offset + 4 <= length) {
            h32 += readIntLE(data, offset) * PRIME3;
            h32 = Integer.rotateLeft(h32, 17) * PRIME4;
            offset += 4;
        }
        while (offset < length) {
            h32 += (data[offset] & 0xFF) * PRIME5;
            h32 = Integer.rotateLeft(h32, 11) * PRIME1;
            offset++;
        }

        h32 ^= h32 >>> 15;
        h32 *= PRIME2;
        h32 ^= h32 >>> 13;
        h32 *= PRIME3;
        h32 ^= h32 >>> 16;
        return h32;
    }

    private static int round(int acc, int input) {
        acc += input * PRIME2;
        acc = Integer.rotateLeft(acc, 13);
        return acc * PRIME1;
    }

    private static int readIntLE(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | (data[offset + 1] & 0xFF) << 8
                | (data[offset + 2] & 0xFF) << 16
                | (data[offset + 3] & 0xFF) << 24;
    }
}

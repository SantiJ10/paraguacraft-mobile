package net.paraguacraft.mobile;

import java.security.MessageDigest;
import java.util.UUID;

/** UUID v3 OfflinePlayer:{nick} — idéntico a vanilla / launcher PC. */
public final class OfflineUuid {
    private OfflineUuid() {}

    public static String of(String username) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(("OfflinePlayer:" + username).getBytes("UTF-8"));
            digest[6] = (byte) ((digest[6] & 0x0f) | 0x30);
            digest[8] = (byte) ((digest[8] & 0x3f) | 0x80);
            long msb = 0;
            long lsb = 0;
            for (int i = 0; i < 8; i++) msb = (msb << 8) | (digest[i] & 0xff);
            for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (digest[i] & 0xff);
            return new UUID(msb, lsb).toString();
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()).toString();
        }
    }
}

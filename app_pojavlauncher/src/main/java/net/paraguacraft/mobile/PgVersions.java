package net.paraguacraft.mobile;

import java.util.Locale;

/** Solo versiones de Minecraft estables (sin snapshot, beta, alpha, pre, rc). */
public final class PgVersions {
    private PgVersions() {}

    public static boolean isReleaseId(String id) {
        if (id == null || id.isEmpty()) return false;
        String s = id.toLowerCase(Locale.ROOT);
        if (s.contains("snapshot") || s.contains("experimental") || s.contains("combat")
                || s.contains("april") || s.contains("pending")) return false;
        if (s.contains("-pre") || s.contains(" pre") || s.endsWith("pre") || s.contains("preview")) return false;
        if (s.contains("-rc") || s.contains(" rc") || s.contains("release-candidate")) return false;
        if (s.contains("alpha") || s.contains("beta") || s.contains("infdev") || s.contains("indev")) return false;
        if (s.matches(".*\\d+w\\d+[a-z]?.*")) return false;
        return s.startsWith("1.") || s.matches("\\d+\\.\\d+.*");
    }
}

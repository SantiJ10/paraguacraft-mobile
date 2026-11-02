package net.paraguacraft.mobile;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Lee el final de latestlog.txt después de un cierre del juego y traduce los errores
 * más comunes a un mensaje en castellano, para que el jugador sepa qué pasó sin abrir el log.
 */
public final class CrashDiagnosis {
    private static final int TAIL_BYTES = 256 * 1024;

    private CrashDiagnosis() {}

    private static final String[][] RULES = {
            {"OutOfMemoryError", "Al juego le faltó memoria RAM. Subí la RAM en Ajustes → Java, o usá un perfil Optimized / menos mods."},
            {"GC overhead limit exceeded", "Al juego le faltó memoria RAM. Subí la RAM en Ajustes → Java, o usá menos mods."},
            {"UnsupportedClassVersionError", "Esta versión necesita otro Java. Borrá el Java elegido a mano en el perfil (dejalo en automático) y volvé a iniciar."},
            {"has been compiled by a more recent version of the Java Runtime", "Esta versión necesita otro Java. Dejá el Java del perfil en automático y volvé a iniciar."},
            {"Mod resolution failed", "Hay mods que no son compatibles entre sí o les falta una dependencia. Revisá el log para ver cuál."},
            {"Incompatible mods found", "Hay mods incompatibles entre sí. Revisá el log para ver cuáles y desactivá uno."},
            {"requires any version of", "A un mod le falta otro mod del que depende. Revisá el log para ver cuál instalar."},
            {"MissingModsException", "A un mod le falta otro mod del que depende. Revisá el log para ver cuál instalar."},
            {"ModLoadingException", "Un mod falló al cargar. Probá desactivar el último mod que agregaste."},
            {"zip END header not found", "Un archivo descargado está dañado. Reinstalá la versión (o el modpack) para que se vuelva a bajar."},
            {"ZipException", "Un archivo descargado está dañado. Reinstalá la versión (o el modpack) para que se vuelva a bajar."},
            {"BackendCreationException", "El renderizador no pudo iniciar los gráficos. Probá otro renderizador en Ajustes → Video."},
            {"GLFW error", "El renderizador no pudo crear la ventana. Probá otro renderizador en Ajustes → Video."},
            {"Failed to create window", "El renderizador no pudo crear la ventana. Probá otro renderizador en Ajustes → Video."},
            {"sodium", "Sodium/Embeddium a veces no es compatible con el renderizador del celular. Probá desactivarlo o usar otro renderizador."},
            {"UnknownHostException", "No hay conexión a internet o falló el DNS. Revisá la conexión y volvé a intentar."},
            {"No space left on device", "No queda espacio en el celular. Usá Ajustes → Varios → Liberar espacio."},
    };

    /** @return mensaje en castellano o null si no se reconoce el error. */
    public static String diagnose() {
        String tail = readTail(new File(Tools.DIR_GAME_HOME, "latestlog.txt"));
        if (tail == null || tail.isEmpty()) return null;
        String lower = tail.toLowerCase();
        for (String[] rule : RULES) {
            if (lower.contains(rule[0].toLowerCase())) {
                String line = findLine(tail, rule[0]);
                return rule[1] + (line != null ? "\n\nDetalle: " + line : "");
            }
        }
        String exception = findLastException(tail);
        return exception != null ? "Error encontrado en el log:\n" + exception : null;
    }

    /** Mensaje completo para mostrar en el diálogo de cierre. */
    public static String buildExitMessage(String baseMessage) {
        String diag = null;
        try {
            diag = diagnose();
        } catch (Throwable ignored) {}
        if (diag == null) return baseMessage;
        return baseMessage + "\n\n" + diag;
    }

    private static String findLine(String text, String needle) {
        String lowerNeedle = needle.toLowerCase();
        String[] lines = text.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].toLowerCase().contains(lowerNeedle)) return trim(lines[i]);
        }
        return null;
    }

    private static String findLastException(String text) {
        String[] lines = text.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String l = lines[i];
            if ((l.contains("Exception") || l.contains("Error:")) && !l.trim().startsWith("at ")) {
                return trim(l);
            }
        }
        return null;
    }

    private static String trim(String line) {
        String t = line.trim();
        return t.length() > 300 ? t.substring(0, 300) + "…" : t;
    }

    private static String readTail(File file) {
        if (!file.isFile()) return null;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long len = raf.length();
            long start = Math.max(0, len - TAIL_BYTES);
            byte[] buf = new byte[(int) (len - start)];
            raf.seek(start);
            raf.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}

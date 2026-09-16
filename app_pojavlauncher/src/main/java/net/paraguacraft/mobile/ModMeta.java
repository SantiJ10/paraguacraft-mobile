package net.paraguacraft.mobile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Lee nombre, descripción e icono de un jar de mod / zip de pack. */
public final class ModMeta {
    public final String title;
    public final String subtitle;
    public final Bitmap icon;

    public ModMeta(String title, String subtitle, Bitmap icon) {
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
    }

    public static ModMeta fromFile(File file) {
        String fallback = file.getName();
        if (fallback.endsWith(".disabled")) fallback = fallback.substring(0, fallback.length() - ".disabled".length());
        String folder = file.getParentFile() != null ? file.getParentFile().getName() : "";
        if (!file.getName().toLowerCase().endsWith(".jar") && !file.getName().toLowerCase().contains(".jar.")) {
            return new ModMeta(prettyZipName(fallback), folder, iconFromZip(file, "pack.png"));
        }
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry fabric = zip.getEntry("fabric.mod.json");
            if (fabric != null) {
                JSONObject j = new JSONObject(read(zip, fabric));
                String name = j.optString("name", fallback);
                String id = j.optString("id", "");
                Bitmap ic = iconFromZip(zip, j.optString("icon", "assets/" + id + "/icon.png"));
                if (ic == null) ic = firstIcon(zip);
                return new ModMeta(name, folder + (id.isEmpty() ? "" : " · " + id), ic);
            }
            ZipEntry modsToml = zip.getEntry("META-INF/mods.toml");
            if (modsToml != null) {
                String toml = read(zip, modsToml);
                String name = findToml(toml, "displayName");
                if (name == null) name = findToml(toml, "modId");
                if (name == null) name = fallback;
                Bitmap ic = iconFromZip(zip, "logo.png");
                if (ic == null) ic = firstIcon(zip);
                return new ModMeta(name, folder, ic);
            }
            ZipEntry mcmod = zip.getEntry("mcmod.info");
            if (mcmod != null) {
                String raw = read(zip, mcmod).trim();
                JSONObject j;
                if (raw.startsWith("[")) {
                    org.json.JSONArray arr = new org.json.JSONArray(raw);
                    j = arr.length() > 0 ? arr.getJSONObject(0) : new JSONObject();
                } else {
                    j = new JSONObject(raw);
                }
                String name = j.optString("name", fallback);
                Bitmap ic = iconFromZip(zip, j.optString("logoFile", "logo.png"));
                return new ModMeta(name, folder, ic);
            }
            return new ModMeta(prettyZipName(fallback), folder, firstIcon(zip));
        } catch (Exception e) {
            return new ModMeta(prettyZipName(fallback), folder, null);
        }
    }

    private static String prettyZipName(String n) {
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        return n.replace('_', ' ').replace('-', ' ');
    }

    private static String findToml(String toml, String key) {
        for (String line : toml.split("\n")) {
            String t = line.trim();
            if (t.startsWith(key)) {
                int eq = t.indexOf('=');
                if (eq < 0) continue;
                String v = t.substring(eq + 1).trim().replace("\"", "");
                if (!v.isEmpty()) return v;
            }
        }
        return null;
    }

    private static Bitmap firstIcon(ZipFile zip) {
        String[] names = {"logo.png", "icon.png", "pack.png"};
        for (String n : names) {
            Bitmap b = iconFromZip(zip, n);
            if (b != null) return b;
        }
        return null;
    }

    private static Bitmap iconFromZip(File zipFile, String entry) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            return iconFromZip(zip, entry);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap iconFromZip(ZipFile zip, String entry) {
        if (entry == null || entry.isEmpty()) return null;
        try {
            ZipEntry e = zip.getEntry(entry);
            if (e == null) return null;
            try (InputStream in = zip.getInputStream(e)) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.ARGB_8888;
                return BitmapFactory.decodeStream(in, null, o);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String read(ZipFile zip, ZipEntry e) throws Exception {
        try (InputStream in = zip.getInputStream(e)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        }
    }
}

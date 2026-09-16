package net.paraguacraft.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Base64;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Resource pack de marca, alineado con el launcher de PC:
 * atlas 256×256 (1.8–1.19), wide 1024×256 (1.20+), pack_format exacto.
 */
public final class BrandPack {
    public static final String PACK_NAME = "ParaguacraftBrandPack";
    private static final String TAG = "PgBrand";
    private static final String STAMP = "v3";

    private BrandPack() {}

    public static void inject(Context ctx, MinecraftProfile profile) {
        if (profile == null) return;
        inject(ctx, Tools.getGameDirPath(profile), mcFromProfile(profile));
    }

    public static void inject(Context ctx, File gameDir, String mc) {
        if (ctx == null || gameDir == null) return;
        if (mc == null || mc.isEmpty()) mc = "1.21.11";
        try {
            int major = mcMajor(mc);
            boolean classic = major < 6;
            boolean folderLegacy = major < 16;
            boolean zipModern = major >= 26 || (major == 21 && mcMinor(mc) >= 5);
            File rp = classic ? new File(gameDir, "texturepacks") : new File(gameDir, "resourcepacks");
            //noinspection ResultOfMethodCallIgnored
            rp.mkdirs();
            File packDir = new File(rp, PACK_NAME);
            File stamp = new File(packDir, ".paraguacraft_brand");
            String want = STAMP + "|" + mc + "|" + packFormat(mc);
            if (stamp.isFile() && want.equals(Tools.read(stamp.getAbsolutePath()).trim())
                    && new File(packDir, "assets/minecraft/textures/gui/title/minecraft.png").isFile()) {
                enableInOptions(gameDir, mc, zipModern, major < 13);
                return;
            }
            writePackTree(ctx, packDir, mc);
            Tools.write(stamp.getAbsolutePath(), want);
            if (classic || zipModern) {
                zipDirectory(packDir, new File(rp, PACK_NAME + ".zip"));
            } else if (folderLegacy) {
                File zip = new File(rp, PACK_NAME + ".zip");
                if (zip.isFile()) //noinspection ResultOfMethodCallIgnored
                    zip.delete();
            }
            enableInOptions(gameDir, mc, zipModern, major < 13);
        } catch (Exception e) {
            Log.w(TAG, "No se pudo inyectar BrandPack", e);
        }
    }

    public static String profileIconDataUri(Context ctx) {
        try (InputStream in = ctx.getAssets().open("paraguacraft/brand/pack.png")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return "data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "default";
        }
    }

    private static String mcFromProfile(MinecraftProfile p) {
        if (p.name != null && p.name.startsWith("Optimized ")) {
            return p.name.substring("Optimized ".length()).trim();
        }
        if (p.lastVersionId != null) {
            String id = p.lastVersionId;
            if (id.contains("1.8.9")) return "1.8.9";
            if (id.contains("1.12.2")) return "1.12.2";
            if (id.contains("1.18.2")) return "1.18.2";
            if (id.contains("1.20.1")) return "1.20.1";
            if (id.contains("1.21.11")) return "1.21.11";
            if (id.contains("26.2")) return "26.2";
        }
        return "1.21.11";
    }

    static int mcMajor(String mc) {
        if (mc.startsWith("26")) return 26;
        String[] p = mc.split("\\.");
        if (p.length >= 2) {
            try { return Integer.parseInt(p[1]); } catch (Exception ignored) {}
        }
        return 20;
    }

    static int mcMinor(String mc) {
        String[] p = mc.split("\\.");
        if (p.length >= 3) {
            try { return Integer.parseInt(p[2]); } catch (Exception ignored) {}
        }
        return 0;
    }

    static int packFormat(String mc) {
        int major = mcMajor(mc);
        int minor = mcMinor(mc);
        if (major >= 26) return minor >= 2 ? 88 : 84;
        if (major <= 5) return 1;
        if (major <= 8) return 1;
        if (major <= 10) return 2;
        if (major <= 12) return 3;
        if (major <= 14) return 4;
        if (major == 15) return 5;
        if (major == 16) return minor <= 1 ? 5 : 6;
        if (major == 17) return 7;
        if (major == 18) return 8;
        if (major == 19) return minor <= 2 ? 9 : (minor == 3 ? 12 : 13);
        if (major == 20) {
            if (minor <= 1) return 15;
            if (minor == 2) return 18;
            if (minor <= 4) return 22;
            return 32;
        }
        if (major == 21) {
            if (minor <= 1) return 34;
            if (minor <= 3) return 42;
            if (minor == 4) return 46;
            if (minor == 5) return 55;
            if (minor == 6) return 63;
            if (minor <= 8) return 64;
            if (minor <= 10) return 69;
            return 75;
        }
        return 15;
    }

    private static String packMcmeta(String mc) {
        int fmt = packFormat(mc);
        int major = mcMajor(mc);
        int minor = mcMinor(mc);
        if (fmt >= 65 || major >= 26 || (major == 21 && minor >= 9)) {
            return "{\"pack\":{\"pack_format\":" + fmt + ",\"description\":\"Marca Oficial Paraguacraft\",\"min_format\":[" + fmt + ",0],\"max_format\":[" + fmt + ",0]}}";
        }
        if (fmt >= 18) {
            return "{\"pack\":{\"pack_format\":" + fmt + ",\"description\":\"Marca Oficial Paraguacraft\",\"supported_formats\":[" + fmt + "," + fmt + "]}}";
        }
        return "{\"pack\":{\"pack_format\":" + fmt + ",\"description\":\"Marca Oficial Paraguacraft\"}}";
    }

    private static void writePackTree(Context ctx, File packDir, String mc) throws Exception {
        File titleDir = new File(packDir, "assets/minecraft/textures/gui/title");
        //noinspection ResultOfMethodCallIgnored
        titleDir.mkdirs();
        Tools.write(new File(packDir, "pack.mcmeta").getAbsolutePath(), packMcmeta(mc));
        Bitmap packIcon = loadAssetBitmap(ctx, "paraguacraft/brand/pack.png");
        if (packIcon != null) {
            savePng(packIcon, new File(packDir, "pack.png"));
            packIcon.recycle();
        }
        Bitmap minecraft = bakeMinecraftPng(ctx, mc);
        savePng(minecraft, new File(titleDir, "minecraft.png"));
        minecraft.recycle();
        copyAsset(ctx, "paraguacraft/brand/paraguacraft_startup.png", new File(titleDir, "mojangstudios.png"));
        copyAsset(ctx, "paraguacraft/brand/paraguacraft_startup.png", new File(titleDir, "mojang.png"));
        Bitmap edition = Bitmap.createBitmap(128, 16, Bitmap.Config.ARGB_8888);
        edition.eraseColor(0);
        savePng(edition, new File(titleDir, "edition.png"));
        edition.recycle();
    }

    private static Bitmap bakeMinecraftPng(Context ctx, String mc) {
        int major = mcMajor(mc);
        if (major >= 20) {
            Bitmap src = loadAssetBitmap(ctx, "paraguacraft/brand/paraguacraft_main_menu.png");
            if (src == null) src = loadAssetBitmap(ctx, "paraguacraft/brand/minecraft.png");
            Bitmap out = bakeWide1024(src);
            if (src != null) src.recycle();
            return out;
        }
        if (major >= 16) {
            Bitmap src = loadAssetBitmap(ctx, "paraguacraft/brand/paraguacraft_main_menu.png");
            if (src == null) src = loadAssetBitmap(ctx, "paraguacraft/brand/minecraft.png");
            Bitmap out = bakeSplit256(src);
            if (src != null) src.recycle();
            return out;
        }
        Bitmap src = loadAssetBitmap(ctx, "paraguacraft/brand/paraguacraft_legacy.png");
        if (src == null) src = loadAssetBitmap(ctx, "paraguacraft/brand/minecraft.png");
        Bitmap out = bakeLegacy256(src);
        if (src != null) src.recycle();
        return out;
    }

    private static Bitmap loadAssetBitmap(Context ctx, String asset) {
        try (InputStream in = ctx.getAssets().open(asset)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            o.inScaled = false;
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            Log.w(TAG, "asset " + asset, e);
            return null;
        }
    }

    private static void savePng(Bitmap bmp, File dest) throws Exception {
        try (FileOutputStream out = new FileOutputStream(dest)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private static Bitmap bakeWide1024(Bitmap src) {
        Bitmap canvas = Bitmap.createBitmap(1024, 256, Bitmap.Config.ARGB_8888);
        canvas.eraseColor(0);
        if (src == null) return canvas;
        Bitmap cropped = cropOpaque(src);
        float scale = Math.min(1024f / cropped.getWidth(), 256f / cropped.getHeight());
        int nw = Math.max(1, Math.round(cropped.getWidth() * scale));
        int nh = Math.max(1, Math.round(cropped.getHeight() * scale));
        drawScaled(canvas, cropped, (1024 - nw) / 2, (256 - nh) / 2, nw, nh);
        if (cropped != src) cropped.recycle();
        return canvas;
    }

    private static Bitmap bakeSplit256(Bitmap src) {
        final int halfW = 155, renderH = 44, row2 = 45;
        Bitmap canvas = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        canvas.eraseColor(0);
        if (src == null) return canvas;
        Bitmap cropped = cropOpaque(src);
        float scale = Math.min((halfW * 2f) / cropped.getWidth(), (float) renderH / cropped.getHeight());
        int nw = Math.max(1, Math.round(cropped.getWidth() * scale));
        int nh = Math.max(1, Math.round(cropped.getHeight() * scale));
        Bitmap strip = Bitmap.createBitmap(halfW * 2, renderH, Bitmap.Config.ARGB_8888);
        strip.eraseColor(0);
        drawScaled(strip, cropped, (halfW * 2 - nw) / 2, (renderH - nh) / 2, nw, nh);
        blit(canvas, strip, 0, 0, 0, 0, halfW, renderH);
        blit(canvas, strip, halfW, 0, 0, row2, halfW, renderH);
        strip.recycle();
        if (cropped != src) cropped.recycle();
        return canvas;
    }

    /** 1.8.9 / 1.12: dos renglones del logo lado a lado, luego el atlas 155×44. */
    private static Bitmap bakeLegacy256(Bitmap src) {
        final int halfW = 155, renderH = 44, row2 = 45, gap = 2;
        Bitmap canvas = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        canvas.eraseColor(0);
        if (src == null) return canvas;
        Bitmap cropped = cropOpaque(src);
        int w = cropped.getWidth();
        int h = cropped.getHeight();
        int splitY = h / 2;
        if (h >= 8) {
            int s0 = h / 4, s1 = 3 * h / 4;
            int minA = Integer.MAX_VALUE;
            int[] rowA = new int[h];
            for (int y = 0; y < h; y++) {
                int c = 0;
                for (int x = 0; x < w; x++) if (((cropped.getPixel(x, y) >>> 24) & 0xFF) > 10) c++;
                rowA[y] = c;
            }
            for (int y = s0; y < s1; y++) minA = Math.min(minA, rowA[y]);
            List<Integer> gaps = new ArrayList<>();
            for (int y = s0; y < s1; y++) if (rowA[y] == minA) gaps.add(y);
            if (!gaps.isEmpty()) splitY = gaps.get(gaps.size() / 2);
        }
        Bitmap top = cropOpaque(Bitmap.createBitmap(cropped, 0, 0, w, Math.max(1, splitY)));
        Bitmap bot = cropOpaque(Bitmap.createBitmap(cropped, 0, splitY, w, Math.max(1, h - splitY)));
        int tw = top.getWidth(), th = top.getHeight(), bw = bot.getWidth(), bh = bot.getHeight();
        int newTw = Math.max(1, Math.round(tw * (renderH / (float) th)));
        int newBw = Math.max(1, Math.round(bw * (renderH / (float) bh)));
        int newTh = renderH, newBh = renderH;
        if (newTw + gap + newBw > halfW * 2) {
            float fit = (halfW * 2 - gap) / (float) (newTw + newBw);
            newTw = Math.max(1, Math.round(newTw * fit));
            newBw = Math.max(1, Math.round(newBw * fit));
            newTh = Math.max(1, Math.round(renderH * fit));
            newBh = Math.max(1, Math.round(renderH * fit));
        }
        Bitmap strip = Bitmap.createBitmap(halfW * 2, renderH, Bitmap.Config.ARGB_8888);
        strip.eraseColor(0);
        int startX = (halfW * 2 - (newTw + gap + newBw)) / 2;
        drawScaled(strip, top, startX, (renderH - newTh) / 2, newTw, newTh);
        drawScaled(strip, bot, startX + newTw + gap, (renderH - newBh) / 2, newBw, newBh);
        blit(canvas, strip, 0, 0, 0, 0, halfW, renderH);
        blit(canvas, strip, halfW, 0, 0, row2, halfW, renderH);
        strip.recycle();
        top.recycle();
        bot.recycle();
        if (cropped != src) cropped.recycle();
        return canvas;
    }

    private static void drawScaled(Bitmap dest, Bitmap src, int x, int y, int w, int h) {
        Canvas c = new Canvas(dest);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        c.drawBitmap(src, new Rect(0, 0, src.getWidth(), src.getHeight()), new Rect(x, y, x + w, y + h), p);
    }

    private static void blit(Bitmap dest, Bitmap src, int sx, int sy, int dx, int dy, int w, int h) {
        Canvas c = new Canvas(dest);
        c.drawBitmap(src, new Rect(sx, sy, sx + w, sy + h), new Rect(dx, dy, dx + w, dy + h), null);
    }

    private static Bitmap cropOpaque(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int minX = w, minY = h, maxX = 0, maxY = 0;
        boolean found = false;
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            src.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                if (((row[x] >>> 24) & 0xFF) > 10) {
                    found = true;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x + 1);
                    maxY = Math.max(maxY, y + 1);
                }
            }
        }
        if (!found) return src;
        return Bitmap.createBitmap(src, minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
    }

    private static void copyAsset(Context ctx, String asset, File dest) throws Exception {
        try (InputStream in = ctx.getAssets().open(asset); FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Exception e) {
            Log.w(TAG, "copy " + asset, e);
        }
    }

    private static void zipDirectory(File sourceDir, File zipFile) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zipWalk(sourceDir, sourceDir, zos);
        }
    }

    private static void zipWalk(File root, File current, ZipOutputStream zos) throws Exception {
        File[] files = current.listFiles();
        if (files == null) return;
        byte[] buf = new byte[8192];
        for (File f : files) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                zipWalk(root, f, zos);
                continue;
            }
            String rel = root.toURI().relativize(f.toURI()).getPath();
            zos.putNextEntry(new ZipEntry(rel));
            try (FileInputStream in = new FileInputStream(f)) {
                int n;
                while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
            }
            zos.closeEntry();
        }
    }

    private static void enableInOptions(File gameDir, String mc, boolean modernZip, boolean noFilePrefix) {
        MCOptionUtils.load(gameDir.getAbsolutePath());
        int major = mcMajor(mc);
        if (major < 6) {
            MCOptionUtils.set("texturePack", PACK_NAME + ".zip");
            MCOptionUtils.set("texturepack", PACK_NAME + ".zip");
            MCOptionUtils.save();
            return;
        }
        String brand = noFilePrefix
                ? PACK_NAME
                : (modernZip ? "file/" + PACK_NAME + ".zip" : "file/" + PACK_NAME);
        String existing = MCOptionUtils.get("resourcePacks");
        List<String> packs = parsePacks(existing);
        List<String> out = new ArrayList<>();
        if (!noFilePrefix) out.add("vanilla");
        out.add(brand);
        for (String p : packs) {
            if (isSystemPack(p)) continue;
            out.add(p);
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < out.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(out.get(i).replace("\"", "\\\"")).append('"');
        }
        sb.append(']');
        MCOptionUtils.set("resourcePacks", sb.toString());
        MCOptionUtils.save();
    }

    private static boolean isSystemPack(String inner) {
        return "vanilla".equals(inner)
                || inner.contains("ParaguacraftBrandPack")
                || inner.contains("Pack_Graficos_Minimos")
                || inner.contains("paraguacraft-pvp");
    }

    private static List<String> parsePacks(String line) {
        List<String> packs = new ArrayList<>();
        if (line == null) return packs;
        String body = line.trim();
        if (body.startsWith("[")) body = body.substring(1);
        if (body.endsWith("]")) body = body.substring(0, body.length() - 1);
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) {
                if (inQ) {
                    packs.add(cur.toString());
                    cur.setLength(0);
                    inQ = false;
                } else inQ = true;
            } else if (inQ) {
                cur.append(c);
            }
        }
        return packs;
    }
}

package net.paraguacraft.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/** Skins / capas: LocalSkin + CustomSkinLoader, igual que el launcher de PC. */
public final class SkinHelper {
    public static final String CSL_JSON = "{\n"
            + "  \"version\": \"14.19\",\n"
            + "  \"buildNumber\": 0,\n"
            + "  \"loadlist\": [\n"
            + "    {\"name\":\"Mojang\",\"type\":\"MojangAPI\"},\n"
            + "    {\"name\":\"ElyBy\",\"type\":\"ElyByAPI\",\"root\":\"http://skinsystem.ely.by/\"},\n"
            + "    {\"name\":\"LocalSkin\",\"type\":\"Legacy\",\"checkPNG\":false,"
            + "\"skin\":\"LocalSkin/skins/{USERNAME}.png\",\"cape\":\"LocalSkin/capes/{USERNAME}.png\"}\n"
            + "  ]\n"
            + "}\n";

    private SkinHelper() {}

    public static File globalSkin() {
        return new File(Tools.DIR_GAME_HOME, "paraguacraft_offline_skin.png");
    }

    public static File globalCape() {
        return new File(Tools.DIR_GAME_HOME, "paraguacraft_offline_cape.png");
    }

    public static File userSkinFile(String username) {
        return new File(Tools.DIR_GAME_HOME, "skins/" + safeName(username) + ".png");
    }

    public static File userCapeFile(String username) {
        return new File(Tools.DIR_GAME_HOME, "capes/" + safeName(username) + ".png");
    }

    private static String safeName(String username) {
        if (username == null || username.isEmpty()) return "Player";
        return username.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    /** Activa la skin/capa de esa cuenta (icono + CSL). No reutiliza la de otro usuario. */
    public static void activateAccount(Context ctx, MinecraftAccount acc) {
        if (acc == null || acc.username == null || acc.username.isEmpty()) return;
        String nick = acc.username;
        File skin = userSkinFile(nick);
        File cape = userCapeFile(nick);
        try {
            boolean local = acc.isLocal();
            File skinsDir = new File(Tools.DIR_GAME_HOME, "skins");
            String[] existing = skinsDir.isDirectory() ? skinsDir.list() : null;
            boolean noneYet = existing == null || existing.length == 0;
            if (local && !skin.isFile() && globalSkin().isFile() && noneYet) {
                copyFile(globalSkin(), skin);
            }
            if (local && !cape.isFile() && globalCape().isFile()) {
                File capesDir = new File(Tools.DIR_GAME_HOME, "capes");
                String[] existingCapes = capesDir.isDirectory() ? capesDir.list() : null;
                boolean noCapes = existingCapes == null || existingCapes.length == 0;
                if (noCapes) copyFile(globalCape(), cape);
            }
            if (local) {
                injectIntoDir(OptimizedInstaller.activeGameDir(), nick, skin, cape);
                File vanilla = new File(Tools.DIR_GAME_NEW);
                if (!vanilla.equals(OptimizedInstaller.activeGameDir())) {
                    injectIntoDir(vanilla, nick, skin, cape);
                }
                Bitmap head = headFromSkinFile(skin);
                if (head != null) writeHeadCache(nick, head);
            } else {
                File staleHead = new File(Tools.DIR_CACHE, nick + ".png");
                if (staleHead.isFile()) staleHead.delete();
            }
        } catch (Exception ignored) {}
    }

    public static String activeUsername(Context ctx) {
        try {
            MinecraftAccount acc = PojavProfile.getCurrentProfileContent(ctx, null);
            if (acc != null && acc.username != null && !acc.username.isEmpty()
                    && !acc.username.startsWith("Demo.")) {
                return acc.username;
            }
        } catch (Exception ignored) {}
        File accDir = new File(Tools.DIR_ACCOUNT_NEW);
        String[] names = accDir.list();
        if (names != null) {
            for (String n : names) {
                if (n.endsWith(".json") && !n.startsWith("Demo.")) {
                    return n.substring(0, n.length() - 5);
                }
            }
        }
        return "Player";
    }

    public static MinecraftAccount activeAccount(Context ctx) {
        try {
            return PojavProfile.getCurrentProfileContent(ctx, null);
        } catch (Exception e) {
            return null;
        }
    }

    public static Bitmap decodePng(File file) {
        if (file == null || !file.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        o.inScaled = false;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), o);
    }

    public static void copyFile(File src, File dest) throws Exception {
        dest.getParentFile().mkdirs();
        try (FileChannel in = new FileInputStream(src).getChannel();
             FileChannel out = new FileOutputStream(dest).getChannel()) {
            out.transferFrom(in, 0, in.size());
        }
    }

    public static void writeCslConfig(File gameDir) {
        File cfgDir = new File(gameDir, "CustomSkinLoader");
        new File(cfgDir, "LocalSkin/skins").mkdirs();
        new File(cfgDir, "LocalSkin/capes").mkdirs();
        File json = new File(cfgDir, "CustomSkinLoader.json");
        try {
            String existing = json.isFile() ? Tools.read(json.getAbsolutePath()) : "";
            if (existing.contains("ElyBy") && existing.contains("LocalSkin/capes")) return;
            Tools.write(json.getAbsolutePath(), CSL_JSON);
        } catch (Exception ignored) {}
    }

    public static void injectSkin(Context ctx, File png) throws Exception {
        if (png == null || !png.isFile()) return;
        String nick = activeUsername(ctx);
        File dest = userSkinFile(nick);
        if (!png.equals(dest)) copyFile(png, dest);
        copyFile(dest, globalSkin());
        injectIntoDir(OptimizedInstaller.activeGameDir(), nick, dest, userCapeFile(nick));
        File vanilla = new File(Tools.DIR_GAME_NEW);
        if (!vanilla.equals(OptimizedInstaller.activeGameDir())) {
            injectIntoDir(vanilla, nick, dest, userCapeFile(nick));
        }
        Bitmap head = headFromSkinFile(dest);
        if (head != null) writeHeadCache(nick, head);
    }

    public static void injectCape(Context ctx, File png) throws Exception {
        if (png == null || !png.isFile()) return;
        String nick = activeUsername(ctx);
        File dest = userCapeFile(nick);
        if (!png.equals(dest)) copyFile(png, dest);
        copyFile(dest, globalCape());
        injectIntoDir(OptimizedInstaller.activeGameDir(), nick, userSkinFile(nick), dest);
        File vanilla = new File(Tools.DIR_GAME_NEW);
        if (!vanilla.equals(OptimizedInstaller.activeGameDir())) {
            injectIntoDir(vanilla, nick, userSkinFile(nick), dest);
        }
    }

    public static void ensureForLaunch(Context ctx, File gameDir) {
        if (gameDir == null) return;
        try {
            MinecraftAccount acc = activeAccount(ctx);
            if (acc != null && !acc.isLocal() && !acc.isDemo()) {
                writeCslConfig(gameDir);
                return;
            }
            String nick = activeUsername(ctx);
            injectIntoDir(gameDir, nick, userSkinFile(nick), userCapeFile(nick));
        } catch (Exception ignored) {}
    }

    private static void injectIntoDir(File gameDir, String nick, File skin, File cape) throws Exception {
        writeCslConfig(gameDir);
        if (skin != null && skin.isFile()) {
            copyFile(skin, new File(gameDir, "CustomSkinLoader/LocalSkin/skins/" + nick + ".png"));
            copyFile(skin, new File(gameDir, "CustomSkinLoader/LocalSkin/" + nick + ".png"));
        }
        if (cape != null && cape.isFile()) {
            copyFile(cape, new File(gameDir, "CustomSkinLoader/LocalSkin/capes/" + nick + ".png"));
        }
    }

    public static void applyUrl(Context ctx, String url) throws Exception {
        File dest = globalSkin();
        dest.getParentFile().mkdirs();
        DownloadUtils.downloadFile(url.replace("http://", "https://"), dest);
        injectSkin(ctx, dest);
    }

    public static void applyCapeUrl(Context ctx, String url) throws Exception {
        File dest = globalCape();
        dest.getParentFile().mkdirs();
        DownloadUtils.downloadFile(url.replace("http://", "https://"), dest);
        injectCape(ctx, dest);
    }

    public static JSONObject fetchMineskinPage(int page) throws Exception {
        String body = DownloadUtils.downloadString("https://api.mineskin.org/get/list/" + Math.max(1, page));
        return new JSONObject(body);
    }

    public static JSONObject lookupPlayer(String nick) throws Exception {
        String enc = java.net.URLEncoder.encode(nick.trim(), "UTF-8");
        String mojang = DownloadUtils.downloadString("https://api.mojang.com/users/profiles/minecraft/" + enc);
        JSONObject idJson = new JSONObject(mojang);
        String id = idJson.optString("id", "");
        if (id.isEmpty()) throw new IllegalStateException("Jugador no encontrado");
        String session = DownloadUtils.downloadString(
                "https://sessionserver.mojang.com/session/minecraft/profile/" + id);
        JSONObject profile = new JSONObject(session);
        JSONArray props = profile.optJSONArray("properties");
        JSONObject out = new JSONObject();
        out.put("username", profile.optString("name", nick));
        out.put("uuid", id);
        if (props != null) {
            for (int i = 0; i < props.length(); i++) {
                JSONObject p = props.getJSONObject(i);
                if (!"textures".equals(p.optString("name"))) continue;
                String raw = new String(android.util.Base64.decode(p.getString("value"), android.util.Base64.DEFAULT),
                        StandardCharsets.UTF_8);
                JSONObject tex = new JSONObject(raw).optJSONObject("textures");
                if (tex == null) break;
                JSONObject skin = tex.optJSONObject("SKIN");
                JSONObject cape = tex.optJSONObject("CAPE");
                if (skin != null) out.put("skinUrl", skin.optString("url"));
                if (cape != null) out.put("capeUrl", cape.optString("url"));
            }
        }
        if (!out.has("skinUrl")) {
            out.put("skinUrl", "https://minotar.net/skin/" + enc);
        }
        out.put("previewUrl", "https://minotar.net/body/" + enc + "/160");
        return out;
    }

    public static JSONArray premiumCapes(MinecraftAccount acc) throws Exception {
        if (acc == null || acc.isLocal() || acc.accessToken == null || "0".equals(acc.accessToken)) {
            return new JSONArray();
        }
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                new java.net.URL("https://api.minecraftservices.com/minecraft/profile").openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + acc.accessToken);
        conn.setRequestProperty("User-Agent", "Paraguacraft-Mobile");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        if (conn.getResponseCode() != 200) return new JSONArray();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.io.InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        }
        JSONObject json = new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
        JSONArray capes = json.optJSONArray("capes");
        return capes != null ? capes : new JSONArray();
    }

    public static void equipPremiumCape(MinecraftAccount acc, String capeId) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                new java.net.URL("https://api.minecraftservices.com/minecraft/profile/capes/active").openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + acc.accessToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "Paraguacraft-Mobile");
        byte[] body = ("{\"capeId\":\"" + capeId + "\"}").getBytes(StandardCharsets.UTF_8);
        conn.getOutputStream().write(body);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Mojang no pudo activar la capa (" + code + ")");
        }
    }

    public static String instanceMcHint() {
        try {
            String pid = net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF
                    .getString(net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            LauncherProfiles.load();
            if (pid == null || LauncherProfiles.mainProfileJson == null) return "";
            MinecraftProfile p = LauncherProfiles.mainProfileJson.profiles.get(pid);
            if (p == null) return "";
            if (p.name != null && !p.name.isEmpty()) return p.name;
            return p.lastVersionId != null ? p.lastVersionId : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Cabeza 64×64: premium usa cache Mojang; local usa su PNG. Nunca la skin de otro nick. */
    public static Bitmap headBitmap(String username) {
        if (username == null || username.isEmpty()) return null;
        MinecraftAccount acc = MinecraftAccount.load(username);
        boolean local = acc == null || acc.isLocal();
        if (!local) {
            try {
                Bitmap cached = MinecraftAccount.getSkinFace(username);
                if (cached != null) return cached;
            } catch (Exception ignored) {}
            return null;
        }
        Bitmap fromUser = headFromSkinFile(userSkinFile(username));
        if (fromUser != null) return fromUser;
        File csl = new File(Tools.DIR_GAME_NEW, "CustomSkinLoader/LocalSkin/skins/" + username + ".png");
        return headFromSkinFile(csl);
    }

    public static Bitmap headOrSteve(String username) {
        Bitmap head = headBitmap(username);
        return head != null ? head : steveHead();
    }

    public static Bitmap headFromSkinFile(File skinFile) {
        Bitmap skin = decodePng(skinFile);
        if (skin == null) return null;
        try {
            return cropHead(skin);
        } catch (Exception e) {
            return null;
        }
    }

    public static void writeHeadCache(String username, Bitmap head) {
        if (username == null || username.isEmpty() || head == null) return;
        File dest = new File(Tools.DIR_CACHE, username + ".png");
        try {
            dest.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(dest)) {
                head.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        } catch (Exception ignored) {}
    }

    private static Bitmap steveHead() {
        Bitmap head = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        int hair = 0xFF2C221B;
        int skin = 0xFFC68642;
        int eye = 0xFF3B6EA5;
        int white = 0xFFFFFFFF;
        int mouth = 0xFFA05A32;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int c = skin;
                if (y < 2) c = hair;
                else if (y == 3 && (x == 2 || x == 5)) c = white;
                else if (y == 3 && (x == 3 || x == 4)) c = eye;
                else if (y == 5 && x >= 2 && x <= 5) c = mouth;
                head.setPixel(x, y, c);
            }
        }
        return Bitmap.createScaledBitmap(head, 64, 64, false);
    }

    public static Bitmap cropHead(Bitmap skin) {
        int w = skin.getWidth();
        int h = skin.getHeight();
        int unit = Math.max(1, w / 8);
        int x = unit;
        int y = unit;
        int size = unit;
        if (x + size > w || y + size > h) {
            size = Math.min(w, h);
            x = 0;
            y = 0;
        }
        Bitmap head = Bitmap.createBitmap(skin, x, y, size, size);
        return Bitmap.createScaledBitmap(head, 64, 64, false);
    }

    /** Frente visible de una capa 64×32 (UV 10×16 en 1,1). */
    public static Bitmap cropCapeFront(Bitmap cape) {
        if (cape == null) return null;
        int w = cape.getWidth();
        int h = cape.getHeight();
        int x = Math.max(1, w / 64);
        int y = Math.max(1, h / 32);
        int cw = Math.max(1, (w * 10) / 64);
        int ch = Math.max(1, (h * 16) / 32);
        if (x + cw > w) cw = Math.max(1, w - x);
        if (y + ch > h) ch = Math.max(1, h - y);
        Bitmap front = Bitmap.createBitmap(cape, x, y, cw, ch);
        return Bitmap.createScaledBitmap(front, 40, 64, false);
    }
}

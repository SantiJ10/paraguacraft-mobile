package net.paraguacraft.mobile;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class OptimizedManifest {
    public final JSONObject root;

    public static class Pack {
        public String id;
        public String mc;
        public String loader;
        public String loaderVersion;
        public int javaMajor;
        public JSONArray hosted;
        public JSONArray modrinth;
        public JSONArray curseforge;
        public JSONObject optifine;

        public String key() {
            return id != null && !id.isEmpty() ? id : mc;
        }

        public String displayName() {
            if ("1.20.1-forge".equals(key())) return "1.20.1 Forge";
            return mc;
        }
    }

    private OptimizedManifest(JSONObject root) {
        this.root = root;
    }

    public static OptimizedManifest load(Context ctx) throws Exception {
        try (InputStream in = ctx.getAssets().open("paraguacraft/optimized-manifest.v1.json")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new OptimizedManifest(new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8)));
        }
    }

    public List<Pack> packs() throws Exception {
        JSONArray arr = root.getJSONArray("packs");
        List<Pack> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Pack p = new Pack();
            p.mc = o.getString("mc");
            p.id = o.optString("id", p.mc);
            p.loader = o.getString("loader");
            p.loaderVersion = o.optString("loaderVersion", "");
            p.javaMajor = o.optInt("javaMajor", 17);
            p.hosted = o.optJSONArray("hosted");
            p.modrinth = o.optJSONArray("modrinth");
            p.curseforge = o.optJSONArray("curseforge");
            p.optifine = o.optJSONObject("optifine");
            out.add(p);
        }
        return out;
    }
}

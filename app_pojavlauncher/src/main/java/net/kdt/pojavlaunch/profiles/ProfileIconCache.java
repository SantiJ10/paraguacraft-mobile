package net.kdt.pojavlaunch.profiles;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.R;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ProfileIconCache {
    // Data header format: data:<mime>;<encoding>,<data>
    private static final String DATA_HEADER = "data:";
    private static final String FALLBACK_ICON_NAME = "default";
    /** Solo se ven un puñado de perfiles a la vez; sin tope el caché crecía
     *  con cada instancia y nunca soltaba los bitmaps. */
    private static final int MAX_CACHED_ICONS = 32;
    private static final LruCache<String, Drawable> sIconCache = new LruCache<>(MAX_CACHED_ICONS);
    // Acotado por naturaleza: un drawable por tipo de loader.
    private static final Map<String, Drawable> sStaticIconCache = new HashMap<>();

    /**
     * Fetch an icon from the cache, or load it if it's not cached.
     * @param resources the Resources object, used for creating drawables
     * @param key the profile key
     * @param icon the profile icon data (stored in the icon field of MinecraftProfile)
     * @return an icon drawable
     */
    public static @NonNull Drawable fetchIcon(Resources resources, @NonNull String key, @Nullable String icon) {
        Drawable cachedIcon = sIconCache.get(key);
        if(cachedIcon != null) return cachedIcon;
        if(icon != null && icon.startsWith(DATA_HEADER)) return fetchDataIcon(resources, key, icon);
        else return fetchStaticIcon(resources, key, icon);
    }

    /**
     * Drop an icon from the icon cache. When dropped, it's Drawable will be re-read from the
     * data string (or re-fetched from the static cache)
     * @param key the profile key
     */
    public static void dropIcon(@NonNull String key) {
        sIconCache.remove(key);
    }

    private static Drawable fetchDataIcon(Resources resources, String key, @NonNull String icon) {
        Drawable dataIcon = readDataIcon(resources, icon);
        if(dataIcon == null) dataIcon = fetchFallbackIcon(resources);
        sIconCache.put(key, dataIcon);
        return dataIcon;
    }

    private static Drawable fetchStaticIcon(Resources resources, String key, @Nullable String icon) {
        Drawable staticIcon = sStaticIconCache.get(icon);
        if(staticIcon == null) {
            if(icon != null) staticIcon = getStaticIcon(resources, icon);
            if(staticIcon == null) staticIcon = fetchFallbackIcon(resources);
            sStaticIconCache.put(icon, staticIcon);
        }
        sIconCache.put(key, staticIcon);
        return staticIcon;
    }

    private static @NonNull Drawable fetchFallbackIcon(Resources resources) {
        Drawable fallbackIcon = sStaticIconCache.get(FALLBACK_ICON_NAME);
        if(fallbackIcon == null) {
            fallbackIcon = Objects.requireNonNull(getStaticIcon(resources, FALLBACK_ICON_NAME));
            sStaticIconCache.put(FALLBACK_ICON_NAME, fallbackIcon);
        }
        return fallbackIcon;
    }

    private static Drawable getStaticIcon(Resources resources, @NonNull String icon) {
        int staticIconResource = getStaticIconResource(icon);
        if(staticIconResource == -1) return null;
        return ResourcesCompat.getDrawable(resources, staticIconResource, null);
    }

    private static int getStaticIconResource(String icon) {
        switch (icon) {
            case "default": return R.drawable.ic_paraguacraft;
            case "fabric": return R.drawable.ic_fabric;
            case "quilt": return R.drawable.ic_quilt;
            default: return -1;
        }
    }

    /** Los iconos se dibujan en filas de lista, asi que decodificar el PNG
     *  original entero podia reservar megabytes por perfil. */
    private static final int MAX_ICON_PX = 128;

    private static Drawable readDataIcon(Resources resources, String icon) {
        byte[] iconData = extractIconData(icon);
        if(iconData == null) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(iconData, 0, iconData.length, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSizeFor(Math.max(bounds.outWidth, bounds.outHeight));
        Bitmap iconBitmap = BitmapFactory.decodeByteArray(iconData, 0, iconData.length, options);
        if(iconBitmap == null) return null;
        return new BitmapDrawable(resources, iconBitmap);
    }

    private static int sampleSizeFor(int largestSide) {
        int sample = 1;
        while(largestSide / sample > MAX_ICON_PX) sample *= 2;
        return sample;
    }

    private static byte[] extractIconData(String inputString) {
        int firstSemicolon = inputString.indexOf(';');
        int commaAfterSemicolon = inputString.indexOf(',');
        if(firstSemicolon == -1 || commaAfterSemicolon == -1) return null;
        String dataEncoding = inputString.substring(firstSemicolon+1, commaAfterSemicolon);
        if(!dataEncoding.equals("base64")) return null;
        return Base64.decode(inputString.substring(commaAfterSemicolon+1), 0);
    }
}

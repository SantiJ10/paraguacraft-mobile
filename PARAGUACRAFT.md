# Paraguacraft Mobile

Fork de [Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android) (PojavLauncher) para jugar **Minecraft Java** en Android, con el mismo objetivo que el launcher de PC.

- Rama de trabajo: `paraguacraft`
- Remote `upstream`: Amethyst original (para mergear el motor)
- `applicationId`: `net.paraguacraft.mobile`
- Nombre visible: **Paraguacraft**
- Namespace JNI: **`net.kdt.pojavlaunch` (no cambiar)**
- Licencia: LGPL-3.0 (este árbol Amethyst) + créditos Amethyst / Pojav / Boardwalk
- Handoff extraído del launcher Tauri: `handoff-mobile/` (copia) y `Paraguacraft/handoff-mobile/`

## Build

NDK **27.3.13750724** (paquete r27d), JDK **17**, Android SDK Platform **37**.

`local.properties` (no se commitea) solo tiene `sdk.dir`. El NDK 27.3 va en `%LOCALAPPDATA%\Android\Sdk\ndk\27.3.13750724` (junction a `D:\Amin\instalador programas\android-ndk-r27d`). `androidnsbypass` también está pinneado a esa misma versión. No uses `ndk.dir`: AGP 9 lo rechaza si no coincide con el default 28.2.

Reserva: NDK **30.0.16248370** (r30) en `%LOCALAPPDATA%\Android\Sdk\ndk\30.0.16248370` (junction a `D:\Amin\instalador programas\android-ndk-r30`). No está pinneado; si AGP o un módulo nativo pide una versión más nueva, se puede cambiar `ndkVersion` a `30.0.16248370` en `app_pojavlauncher` y `androidnsbypass`.

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_NDK_HOME = "D:\Amin\instalador programas\android-ndk-r27d"
cd C:\Users\San\Documents\Amin\Proyectos\paraguacraft-mobile
.\gradlew.bat :app_pojavlauncher:assembleDebug
```

APK: `app_pojavlauncher/build/outputs/apk/debug/`

JRE: si falta, bajar el artefacto `jre8-pojav` del CI de Amethyst.

CurseForge: variable `CURSEFORGE_API_KEY` o `app_pojavlauncher/curseforge_key.txt` (no commitear).

## APK y auto-update

**No** commitear el APK (136+ MB). Igual que el `.exe` de PC:

1. `gradlew.bat :app_pojavlauncher:assembleDebug` (o Release cuando haya keystore).
2. Subir `app_pojavlauncher-debug.apk` a [GitHub Releases](https://github.com/SantiJ10/Paraguacraft/releases) con tag `mobile-0.1.1`.
3. Poner la URL directa del `.apk` en `latest-mobile.json` (este repo) para que Cloudflare Pages lo sirva en `https://paraguacraft.pages.dev/latest-mobile.json`.
4. El launcher móvil consulta ese JSON al abrir.

## v1

- Catálogo **Optimized only** (manifest `assets/paraguacraft/optimized-manifest.v1.json`)
- Cuentas Microsoft (WebView Amethyst) + local (UUID vanilla OfflinePlayer)
- Tienda Modrinth + CurseForge sobre la instancia activa
- Skins premium (MSA) + PNG local
- Táctil grande; HID mouse/teclado y mando nativos de Amethyst (controles → hold para remapper)
- Paraguabot pre-launch (sheet, no in-game)

No PvP, no servidores, no iOS (fase 2: Amethyst-iOS, sideload/JIT, no App Store).

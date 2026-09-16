# Plan v1 Android

## Repo motor

```
git clone --recursive https://github.com/AngelAuraMC/Amethyst-Android.git paraguacraft-mobile
cd paraguacraft-mobile
git remote add upstream https://github.com/AngelAuraMC/Amethyst-Android.git
git checkout -b paraguacraft
```

Hermana de este workspace: `../paraguacraft-mobile`.

Build: NDK 27.3, JDK 17, `gradlew.bat :app_pojavlauncher:assembleDebug`. Artefacto CI `jre8-pojav` si el JRE no viene en el clone.

**No** renombrar paquete JNI `net.kdt.pojavlaunch`. Solo `applicationId` / `applicationIdSuffix` → `net.paraguacraft.mobile`.

## Capa Paraguacraft (sobre Amethyst)

1. Catálogo Optimized only (manifest JSON del handoff). Primer launch: detectar RAM → lite/media/alta.
2. Cuentas: MSA device-code o WebView + offline UUID vanilla.
3. Tienda MR+CF sobre instancia activa.
4. Skins premium + PNG local.
5. Control: layout táctil grande default; HID mouse/teclado ya en Amethyst (exponer en Ajustes); mando remapper nativo + Controlify/Controllable opcional en Fabric/Forge modernos.
6. Paraguabot sheet pre-launch.
7. LICENSE LGPL-3.0 + créditos + “basado en Amethyst”.

## Fuera de alcance v1

LiteCraft/OptiCraft/Bedrock, JARs MC embebidos, clientes PvP, servidores/Playit/Geyser, iOS, Play Console, selector Vanilla/Forge/Fabric/Quilt.

## Criterio de listo

- APK debug instala.
- Login Microsoft y local.
- Un pack Optimized instala y arranca en 3–4 GB (o emulador).
- Tienda MR y CF instalan un mod compatible.
- Skin premium y local visibles in-game.
- Táctil jugable; HID/mando no rompen.
- Paraguabot repara o explica crash de prueba **sin** abrirse in-game.

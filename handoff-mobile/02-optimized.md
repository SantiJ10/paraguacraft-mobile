# Optimized — catálogo único (PC → móvil)

En PC el usuario **no elige** Fabric vs Forge. Ve un pack “Paraguacraft Optimized” `PACK_VERSION = 1.0.0`. El backend se resuelve así:

| Minecraft | Loader real | Cómo se elige |
|---|---|---|
| 1.8.9 | Forge `11.15.1.2318` + OptiFine mod `HD_U_M5` | `is_optifine_mc` |
| 1.12.2 | Forge `14.23.5.2860` + OptiFine `HD_U_G5` | `is_optifine_mc` |
| 1.18.2, 1.20.1, 1.21.11, 26.2 | Fabric (primera versión que devuelve `fabric::versions`) | `is_fabric_mc` |
| NeoForge 1.20.1 | **retirado** (`NEOFORGE_MCS = []`) | no ofrecer |

Java requerido (`required_for_mc`):

| MC | Major |
|---|---|
| ≤ 1.16.x (1.8.9, 1.12.2) | 8 |
| 1.18.2 | 17 |
| 1.20.1 | 17 (patch &lt; 5) |
| 1.21.11 | 21 |
| 26.2 | 25 |

En Amethyst: usar JRE Pojav `jre8-pojav` para 1.8.9/1.12.2; JRE 17/21/25 del motor para el resto. **No** embeber el JAR vanilla; Amethyst lo baja de Mojang.

Mods: pins en `optimized-manifest.v1.json`. SHA-1 **no** está en el repo; al instalar `GET https://api.modrinth.com/v2/version/{version_id}` y tomar `files[0].hashes.sha1` + `url`. CurseForge: `GET /v1/mods/{project}/files/{file}` con `x-api-key`.

OptiFine JAR: oficial optifine.net (UA de navegador) o fallback `https://bmclapi2.bangbang93.com/optifine/{mc}/{type}/{patch}`.

## Móvil: shaders OFF

PC instala Iris/OptiFine shaders **por gama**. En v1 móvil: **no instalar Iris activo ni shaderpacks**. Pack lite = Sodium/Lithium/culling; quitar `iris`, `oculus`, packs de `install_shaders_for_tier`.

## Options.txt Optimized (PC, copiar valores; no fullscreen)

Modern (Fabric):

| Clave | baja | media | alta |
|---|---|---|---|
| renderDistance | 5 | 8 | 10 |
| simulationDistance | 4 | 6 | 8 |
| particles | 2 | 1 | 1 |
| graphicsMode | 0 | 1 | 1 |
| ao | false | true | true |
| maxFps | 260 | 260 | 260 |
| enableVsync | false | false | false |
| entityShadows | false | false | false |
| entityDistanceScaling | 0.5 | 0.75 | 0.85 |
| renderClouds | false | fast | fast |
| mipmapLevels | 1 | 2 | 3 |

Legacy OptiFine: `fancyGraphics`, `ao` 0/1/2, `useVbo=true`, no `graphicsMode`.

Configs de mods (`apply_optimized_mod_configs`): lithium, moreculling, sodium-extra, entityculling, etc. En lite móvil aplicar el branch `"baja"`.

Marker PC: `.paraguacraft_optimized_tuned` versión `v9`.

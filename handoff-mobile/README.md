# Handoff Paraguacraft Mobile

Extracto **literal** del launcher Tauri de este repo (`launcher/`). No hay hashes de JARs Optimized en el código de PC: los pins son `version_id` Modrinth / `(project_id, file_id)` CurseForge; el SHA-1 se resuelve al instalar.

| Archivo | Contenido |
|---|---|
| `01-auth.md` | Microsoft (device-code + browser) + cuenta local/offline |
| `02-optimized.md` | Packs, loaders, Java, options, shaders (PC) vs móvil |
| `03-store.md` | Modrinth + CurseForge: URLs, headers, destinos |
| `04-skins.md` | Galería MineSkin, premium Mojang, PNG local, CSL |
| `05-performance.md` | Gamas PC y adaptación móvil (qué NO copiar) |
| `06-branding.md` | Paleta, tipografía, navegación |
| `07-paraguabot.md` | Groq/contexto PC y rediseño móvil |
| `08-source-index.md` | `path:línea` de todo lo citado |
| `09-ios-fase2.md` | Nota iOS (no implementar) |
| `10-mobile-v1-plan.md` | Criterio de listo y mapa Amethyst |
| `optimized-manifest.v1.json` | JSON canónico de packs Optimized |

**Reglas móviles (producto):** solo Android; solo catálogo Optimized; no PvP; no servidores/Playit/Geyser; no embeber JARs de Minecraft; JNI `net.kdt.pojavlaunch` intacto; `applicationId` `net.paraguacraft.mobile`.

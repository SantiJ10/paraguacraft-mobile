# Skins

## Premium (MSA)

- Perfil: `GET https://api.minecraftservices.com/minecraft/profile`
- Cambiar skin: `POST https://api.minecraftservices.com/minecraft/profile/skins` (multipart PNG + variant classic/slim) — `skins/mojang.rs`
- Capa: endpoints cape del mismo API.
- Pending: `pending_premium` hasta que Mojang confirme.

## No-premium (local)

- PNG global: `{minecraftDir}/paraguacraft_offline_skin.png`
- Cache cara: `{minecraftDir}/paraguacraft_offline_skin_face.png`
- **No** copiar a `steve.png`/`alex.png` del resource pack (rompe multiplayer).
- CustomSkinLoader: proyecto Modrinth `idMHQ4n2`. PC lo inyecta al launch offline (`skins/csl.rs`). Ely.by es el *skin server* de CSL, **no** el login. En móvil v1: PNG local inyectado vía CSL LocalSkin (`{USERNAME}.png`) o `skinCustom` de Amethyst. No agregar authlib-injector.

## Galería

- MineSkin list: `GET https://api.mineskin.org/get/list/{page}` página 1-index, `PAGE_SIZE = 15`
- Por UUID: `GET https://api.mineskin.org/get/uuid/{uuid}`
- Búsqueda jugador Mojang: perfil + textura.

UI: `SkinsView.vue` — tabs galería / import archivo / URL, variant classic vs slim, aplicar premium vs local.

## Launch

`commands/launch.rs` llama `skins::ensure_for_launch` antes de arrancar. Móvil: aplicar skin **antes** de `LaunchGameActivity`; no tocar el proceso in-game.

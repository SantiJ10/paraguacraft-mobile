# Índice de fuentes (path:línea)

Líneas del workspace Paraguacraft al extraer este handoff.

## Auth
- `launcher/src-tauri/src/core/accounts/microsoft.rs:19` CLIENT_ID
- `launcher/src-tauri/src/core/accounts/microsoft.rs:20` REDIRECT_URI
- `launcher/src-tauri/src/core/accounts/microsoft.rs:21` DEVICE_CLIENT_ID
- `launcher/src-tauri/src/core/accounts/microsoft.rs:22` SCOPE
- `launcher/src-tauri/src/core/accounts/microsoft.rs:23` DEVICE_VERIFY_URI
- `launcher/src-tauri/src/core/accounts/microsoft.rs:25-30` TOKEN/DEVICE/XBL/XSTS/MC/PROFILE URLs
- `launcher/src-tauri/src/core/accounts/microsoft.rs:62` `build_login_url`
- `launcher/src-tauri/src/core/accounts/microsoft.rs:182` `validate_profile`
- `launcher/src-tauri/src/core/accounts/microsoft.rs:206` `login_from_tokens`
- `launcher/src-tauri/src/core/accounts/offline.rs:8` `offline_uuid`
- `launcher/src-tauri/src/core/accounts/mod.rs:18` crafatar avatar
- `launcher/src-tauri/src/commands/accounts.rs:11-36` get/set/add_offline/rename
- `launcher/src/lib/msLogin.ts:2` MS_DEVICE_LINK + QR

## Optimized
- `launcher/src-tauri/src/core/loaders/optimized.rs:28` ID
- `launcher/src-tauri/src/core/loaders/optimized.rs:31-43` MC lists, Forge pins, MODRINTH, TUNED, PACK_VERSION
- `launcher/src-tauri/src/core/loaders/optimized.rs:59-90` PINS_1_21_11
- `launcher/src-tauri/src/core/loaders/optimized.rs:93-129` PINS_26_2
- `launcher/src-tauri/src/core/loaders/optimized.rs:132-166` PINS_1_20_1_FABRIC
- `launcher/src-tauri/src/core/loaders/optimized.rs:169-194` PINS_1_18_2
- `launcher/src-tauri/src/core/loaders/optimized.rs:197-224` PINS_1_20_1_NEOFORGE (retirado)
- `launcher/src-tauri/src/core/loaders/optimized.rs:247-277` MR/CF 1.12.2 y 1.8.9
- `launcher/src-tauri/src/core/loaders/optifine.rs:13-14` BMCL + optifine.net
- `launcher/src-tauri/src/core/loaders/optifine.rs:148-150` HD_U_G5 / HD_U_M5
- `launcher/src-tauri/src/core/loaders/optifine.rs:174-179` preferred_type_patch
- `launcher/src-tauri/src/core/java/mod.rs:13` `required_for_mc`
- `launcher/src-tauri/src/core/java/mojang.rs:41-43` jre-legacy / gamma / delta
- `launcher/src-tauri/src/core/performance.rs:340` `tier_options_optimized`
- `launcher/src-tauri/src/core/loaders/optimized.rs:711-747` shaders PC (no instalar en móvil)

## Tienda
- `launcher/src-tauri/src/core/store/modrinth.rs:20` API
- `launcher/src-tauri/src/core/store/modrinth.rs:104` search
- `launcher/src-tauri/src/core/store/curseforge.rs:20-42` API, GAME_ID, class_id, loader_type
- `launcher/src-tauri/src/core/store/curseforge.rs:282` search
- `launcher/src-tauri/src/core/store/mod.rs:100` content_subdir
- `launcher/src-tauri/src/core/store/mod.rs:112` store::search
- `launcher/src-tauri/src/core/store/deps.rs:16` MAX_DEPTH
- `launcher/src-tauri/src/core/store/deps.rs:113` resolve_required
- `launcher/src-tauri/src/commands/store.rs:16` store_search
- `launcher/src-tauri/src/config/keys.rs:16` CURSEFORGE_API_KEY

## Skins
- `launcher/src-tauri/src/core/skins/offline.rs:19-28` PNG paths
- `launcher/src-tauri/src/core/skins/csl.rs:14` CSL_PROJECT
- `launcher/src-tauri/src/core/skins/catalog.rs:9` PAGE_SIZE
- `launcher/src-tauri/src/core/skins/catalog.rs:99` mineskin list

## Perf / launch
- `launcher/src-tauri/src/core/hardware.rs:68-76` perfil
- `launcher/src-tauri/src/core/hardware.rs:393` recommend
- `launcher/src-tauri/src/core/launch/mod.rs:411` build_jvm_ram_gc

## Branding
- `launcher/src/styles/index.css:8-18` acento + surfaces
- `launcher/tailwind.config.js:8-14` pc.green / pc.ai
- `launcher/src/i18n/index.ts:7-26` copy es
- `launcher/src/App.vue:14` locale default es

## Paraguabot
- `launcher/src-tauri/src/core/ai.rs:205` SYSTEM_PROMPT
- `launcher/src-tauri/src/core/ai.rs:255` suggested_actions
- `launcher/src-tauri/src/core/ai.rs:333` analyze_prompt_async
- `launcher/src-tauri/src/config/keys.rs:312` Groq chat_url
- `launcher/src/components/layout/AiPanel.vue:20` runAction repair_instance

# Tienda — Modrinth + CurseForge (paridad PC)

Instalar **sobre la instancia Optimized activa**, filtrando por su MC + loader (`fabric` o `forge`). Tipos: `mod`, `resourcepack`, `shader`. Sin PvP, sin plugins, sin servidores.

## Destinos (instancia)

`content_subdir` (`store/mod.rs:100`):

| `project_type` | carpeta |
|---|---|
| mod (default) | `mods/` |
| resourcepack | `resourcepacks/` |
| shader | `shaderpacks/` |
| datapack | `datapacks/` (no v1 móvil) |

## Modrinth

- Base: `https://api.modrinth.com/v2`
- Search: `GET /search?query=&limit=&offset=&index=relevance&facets=`  
  Facets: `[["project_type:{type}"],["versions:{mc}"],["categories:{loader}"]]`  
  Loader solo si tipo es `mod|modpack|plugin`. Resource packs/shaders **sin** categoría de loader.
- Limit clamp 1–100. Default comando: offset 0, limit 40.
- Detalle: `GET /project/{id}`
- Versiones: `GET /project/{id}/version?game_versions=["mc"]&loaders=["loader"]`
- Archivo: `GET /version/{version_id}` → `files[].url` + `hashes.sha1`
- User-Agent: el de `net` del launcher (identificar Paraguacraft Mobile).

Firma PC:

```
modrinth::search(client, query, project_type, mc, loader, offset, limit)
modrinth::project_detail(client, project_id)
store::search(client, provider, cf_key, query, project_type, mc, loader, offset, limit)
```

Comando Tauri: `store_search` → `commands/store.rs:16`.

## CurseForge

- Base: `https://api.curseforge.com/v1`
- Game id: `432`
- Header: `x-api-key` + `Accept: application/json`
- **No commitear la key.** Env: `CURSEFORGE_API_KEY`. Fallback PC: `app_secrets.json` / Ajustes. En Android: `local.properties` o BuildConfig no versionado, o pedirla en Ajustes (igual que PC).
- Search: `GET /mods/search?gameId=432&searchFilter=&pageSize=&index=&sortField=2&sortOrder=desc`  
  + `classId` (mod=6, resourcepack=12, shader=6552)  
  + `gameVersion`  
  + `modLoaderType` (forge=1, fabric=4, quilt=5, neoforge=6)
- Limit clamp 1–50 (API CF).
- Si key vacía: error explícito, no buscar.
- HTTP 401/403: key inválida.
- `cf_distribution_blocked`: autor no permite third-party; mostrar URL CurseForge, no abortar toda la cola.

## Dependencias

`store/deps.rs` `resolve_required` — profundidad máx. 8. Solo REQUIRED. Remap Quilt FAPI↔Fabric API. UI: checkboxes de deps como `InstallStoreModal.vue`.

## UX PC a replicar

`StoreView.vue`: icono + nombre, pestañas tipo, provider MR/CF, paginación offset, filtros MC+loader de la instancia. Modal de instalación con deps.

Móvil: filtrar **siempre** con MC+loader del pack Optimized activo (no vanilla libre).

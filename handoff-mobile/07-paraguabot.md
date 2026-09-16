# Paraguabot — rediseño móvil (no clonar el chat de escritorio)

## Qué hace en PC

- UI: `AiPanel.vue` chat largo + acciones clicables.
- API: Groq `https://api.groq.com/openai/v1/chat/completions` (`GROQ_API_KEY`). Fallback OpenAI. Sin key: heurísticas locales (`analyze_prompt`).
- Modelos Groq (orden): modelo config, `openai/gpt-oss-120b`, `openai/gpt-oss-20b`, `llama-3.3-70b-versatile`, `llama-3.1-8b-instant`.
- max_tokens 1024, temperature 0.35.
- Contexto: instancia activa (nombre, MC, loader), favoritos, **servidores/Playit** (omitir en móvil), último prompt.
- Acciones deterministas: `repair_instance`, `sync_pvp_config` (**no** en móvil), `open_server_console` (**no** en móvil).
- Crash: `diagnostics.rs` + `PostCrashBanner.vue` + `latest.log`.

## Móvil v1

- **Solo pre-launch.** Si Minecraft está en foreground, el sheet no existe (0% CPU in-game).
- UI: bottom sheet + **chips**, no hilo de chat.
- Acciones:
  1. Reparar instancia (`repair_instance` equivalente Amethyst: re-verify assets, re-download missing mods del manifest).
  2. Reinstalar Optimized (wipe mods/configs del pack, reinstalar pins).
  3. Cambiar gama (lite/media/alta → Xmx + options + renderer).
  4. Explicar último crash: leer `{instance}/logs/latest.log` (cola), heurística local + Groq opcional.
- Contexto inyectado: pack activo, RAM dispositivo, gama, último error. **Sin** PvP ni servidores.
- Key: misma `GROQ_API_KEY` por env/ajustes; si falta, solo heurística (como PC).

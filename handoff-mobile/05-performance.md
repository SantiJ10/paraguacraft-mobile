# Rendimiento — PC vs móvil

## Gama PC (no copiar umbrales a un celular)

`hardware.rs` perfil:

- `baja` si RAM ≤ 8 GB **o** cores ≤ 4, o (RAM ≤ 12 y cores ≤ 6)
- `alta` si RAM ≥ 24 **o** (RAM ≥ 16 y cores ≥ 6)
- else `media`

`recommend(ram_gb)` Xmx: ≤8 GB → 45% tope 4096; ≤16 → 6144; ≤32 → 12288; else 16384. Redondeo 512, mínimo 2048. GC: ZGC si RAM ≥ 16, si no G1.

## JVM PC Optimized (launch/mod.rs)

Java 8: G1, MaxGCPauseMillis=30, G1NewSizePercent=20, DisableExplicitGC.  
Java 15+: **ZGC** / ZGenerational en 1.21.11 Java 21. AlwaysPreTouch. Shenandoah opcional.

## Qué NO copiar a móvil

| PC | Móvil |
|---|---|
| ZGC / ZGenerational | **No.** Usar G1 o SerialGC del JRE Pojav |
| AlwaysPreTouch | **No** (RAM + arranque) |
| Game Mode Windows | N/A |
| Iris on + shaderpacks por gama | **Shaders OFF**, no instalar Iris por defecto |
| Complementary Unbound / Photon / Hysteria | **No** |
| Xmx = 45% de 8–16 GB desktop | Dejar **1.0–1.5 GB** al sistema Android |
| maxFps 260 | 60 o vsync según SoC; no 260 en gama baja |

## Gama móvil (primera ejecución)

Detectar `ActivityManager.MemoryInfo.totalMem`.

| RAM dispositivo | Gama | Xmx orientativo | Renderer | Pack |
|---|---|---|---|---|
| ≤ 3.5 GB | lite | 1024–1280 MB | GL4ES | lite (Sodium/Lithium/culling) |
| 3.5–6 GB | media | 1536–2048 MB | GL4ES; Zink solo si SoC OK | media sin Iris |
| ≥ 6.5 GB | alta | 2048–3072 MB, nunca más de (total − 1536) | Vulkan/Zink opcional | alta sin Iris default |

`Xms` ≈ 50–75% de Xmx, no igualar Xmx en 2–3 GB.

options.txt: usar tabla `tier_options_optimized` de PC para baja/media/alta (`02-optimized.md`). Lite = branch `"baja"`.

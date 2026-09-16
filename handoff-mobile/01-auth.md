# Auth — igual que PC

No hay authlib-injector en el login. Ely.by aparece solo como **loader de skins** (CustomSkinLoader) al lanzar offline, no como autenticación.

## Microsoft (premium)

Client IDs **públicos** (Azure apps del launcher, no secretos):

| Uso | Constante | Valor |
|---|---|---|
| Auth-code / navegador | `CLIENT_ID` | `72fb7c48-c2f5-4d13-b0e7-9835b3b906c0` |
| Device-code / QR | `DEVICE_CLIENT_ID` | `d772766b-19b4-4f69-b353-989f890c5d3b` |

Redirect: `https://login.live.com/oauth20_desktop.srf`  
Scope: `XboxLive.signin offline_access`  
Verify URI (QR): `https://www.microsoft.com/link`  
QR imagen: `https://api.qrserver.com/v1/create-qr-code/?size={n}x{n}&data=` + encodeURIComponent(link)

Endpoints:

1. Device code: `POST https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode` form `client_id` + `scope`
2. Token: `POST https://login.microsoftonline.com/consumers/oauth2/v2.0/token`  
   - device: `grant_type=urn:ietf:params:oauth:grant-type:device_code`  
   - refresh: `grant_type=refresh_token` + `refresh_token` + el **mismo** `client_id` que se usó al loguear (`ms_client_id`)
   - auth-code: `grant_type=authorization_code` + `CLIENT_ID`
3. Xbox Live: `POST https://user.auth.xboxlive.com/user/authenticate` JSON RPS `d={ms_access}`
4. XSTS: `POST https://xsts.auth.xboxlive.com/xsts/authorize` RelyingParty `rp://api.minecraftservices.com/`
5. Minecraft: `POST https://api.minecraftservices.com/authentication/login_with_xbox` `{ identityToken: "XBL3.0 x={uhs};{xsts}" }`
6. Perfil: `GET https://api.minecraftservices.com/minecraft/profile` Bearer `mc_access_token`  
   200 → name+uuid; 401/403 → refresh; 429/5xx → transitorio

Refresh **on-demand** (no hilo timer). El poll del device-code lo dispara la UI.

Login URL browser:  
`https://login.live.com/oauth20_authorize.srf?client_id={CLIENT_ID}&response_type=code&redirect_uri={REDIRECT}&scope={SCOPE}&prompt=select_account`

Móvil: WebView o Custom Tab con el mismo `CLIENT_ID` + redirect desktop, **o** device-code + QR (mejor en tablet). Guardar `ms_refresh_token` + `ms_client_id`.

Avatar launcher: `https://crafatar.com/avatars/{uuid_sin_guiones}?overlay`

## Offline / no-premium

- Nick validado ≥ 3 caracteres.
- UUID v3: MD5(`OfflinePlayer:{username}`), version nibble `0x30`, variant `0x80` (idéntico a vanilla offline).
- Id interno: `offline-{uuid}`.
- `kind`: `"offline"`, `premium`: false.
- Launch: `auth` legacy / offline; **no** token Mojang.
- Skin: PNG global + LocalSkin / CSL (ver `04-skins.md`). No inyectar steve/alex del brand pack.

Comandos PC: `add_offline_account`, `rename_offline_account`, `get_accounts`, `set_active_account`.

## Qué no agregar en móvil

- authlib-injector / Ely.by como **login** (no está en el flujo de cuentas).
- Cuentas “gratis públicas”. Solo Microsoft + nick local, como el launcher.

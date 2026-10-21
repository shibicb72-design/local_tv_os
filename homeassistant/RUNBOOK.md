# Runbook: voice control for the projector

Operator runbook, 14 steps. Placeholders in angle brackets are replaced by the operator. Real values never go into
the repo. Everything is done by the operator **except step 9, the only SHIBI STEP**.
Any line that touches the projector (steps 5, 11, 13) needs Shibi's OK first.

Run the commands from the `homeassistant` folder of the repo on the Alienware.

## Steps

1. **Docker Desktop (WSL2)** runs and starts with Windows. The Alienware's sleep is off (a Windows power setting on
   the Alienware, not a projector setting).
2. **`scripts\setup.ps1`**: checks Docker, creates `secrets/`, copies the adb key, asks for the Tailscale auth key
   and time zone and writes `.env`, creates `config\secrets.yaml` from the example.
3. **Tailscale** (the operator's own free account): in the tailnet policy enable HTTPS and allow Funnel for
   `tag:ha`; create a tagged, single-use auth key and put it in `.env` (`setup.ps1` asks for it).
4. **`docker compose up -d`**. Open `http://127.0.0.1:8123` and create the Home Assistant **owner** account (the
   operator, strong password). Then create the user `google-link`: non-admin, "can only log in from the local
   network" **off**, a 32-character random password kept in the operator's password manager (never in the repo).
5. **Android Debug Bridge integration** (projector): host `10.0.0.242`, port 5555, default key path, entity named
   `media_player.projector_adb`. Open the integration's System options and **turn polling off**. Test in Developer
   tools: `androidtv.adb_command` with command `echo ok` (read-only; the projector is unchanged). *Needs OK.*
6. **`scripts\check-exposure.ps1 -PublicHost <host>.<tailnet>.ts.net`** must pass before Google is configured.
7. **Google side** (see below), with the **operator's** Google account: project `transcendence-ha`, cloud-to-cloud
   integration, HomeGraph API, service account key saved as `config/SERVICE_ACCOUNT.json`, `google_project_id` in
   `config/secrets.yaml`; then `docker compose restart homeassistant`.
8. **Make the test integration visible to Shibi's account**: add Shibi's Google account to the project in Cloud IAM
   with the Viewer role (Google's documented way to let other accounts test a smart-home project). If the
   integration does not show up for Shibi, fallback: in step 9 Shibi signs in once to the console on the Alienware
   and the operator creates the project under Shibi's account in the same sitting.
9. **SHIBI STEP**, boxed below.
10. **Name clash**: if the projector already exists in Google Home as a Cast device named "TV", the names clash.
    Shibi decides which one keeps "TV".
11. **Voice tests** (each *needs OK*, they change the screen or volume): "switch TV to Home / YouTube / Prime Video
    / Files / Player / HDMI 1", "set TV volume to 20 / 35 percent" (afterwards check
    `cmd media_session volume --stream 3 --get`, read-only), "turn off TV", "turn on TV". Log each in `adb_log.txt`
    and `logs/V6.txt`.
12. **Reboot the Alienware once**; check Home Assistant, Caddy and Tailscale come back and step 11 still works.
13. **Re-authorisation drill** (described, not executed): `scripts\adb-reauth.ps1` shows the steps and runs
    `adb connect`; someone accepts the prompt on the projector with the remote and ticks "Always allow"; then
    `docker compose restart homeassistant`.
14. **Rollback**: `docker compose down`; unlink the service in the Google Home app (Shibi, optional); delete the
    console project.

## SHIBI STEP (step 9)

```
+==============================================================================================+
|  SHIBI STEP  (the only one)                                                                  |
|                                                                                              |
|  On Shibi's phone, in the Google Home app (signed in to Shibi's Google account):             |
|                                                                                              |
|    1. Tap  +  ->  Set up device  ->  Works with Google                                       |
|    2. Search  "[test] transcendence-ha"                                                      |
|    3. The Home Assistant sign-in page opens.                                                 |
|       User:  google-link        Password: from the operator                                  |
|    4. Tap  ALLOW / LINK                                                                      |
|    5. Choose the home "Transcendence Living" and the room.                                   |
|                                                                                              |
|  Done: a device "TV" appears.                                                                |
+==============================================================================================+
```

The operator has done everything else before this point (steps 1-8) and does steps 10-14 afterwards.

## Google side (step 7)

`<host>` is the Funnel hostname of the Tailscale node (`<host>.<tailnet>.ts.net`), `<project_id>` the Google project id
(also stored as `google_project_id` in `config/secrets.yaml`).

Follow Home Assistant's current "Google Assistant - Manual setup" page for the exact console labels; Google renames them.
The values to type in the Google Home Developer Console (cloud-to-cloud integration, account linking):

| Field | Value |
|---|---|
| Fulfillment URL | `https://<host>/api/google_assistant` |
| Client ID | `https://oauth-redirect.googleusercontent.com/r/<project_id>` |
| Client secret | any non-empty string (Home Assistant does not check it) |
| Authorization URL | `https://<host>/auth/authorize` |
| Token URL | `https://<host>/auth/token` |
| Scopes | `email`, `name` |

Also:
1. Enable the **HomeGraph API** in the same Google Cloud project.
2. Create a service account key (JSON) and save it as `config/SERVICE_ACCOUNT.json` (gitignored).
3. Put the project id in `config/secrets.yaml` as `google_project_id`, then restart Home Assistant:
   `docker compose restart homeassistant`.
4. `scripts\check-exposure.ps1` must have passed (step 6) before linking.

## Android Debug Bridge integration (step 5)

Host `10.0.0.242`, port 5555, default key path, entity `media_player.projector_adb` (`config/packages/tv.yaml` sends
its commands there). In the integration's System options **turn polling off**: no `scan_interval`, no 10-second
`dumpsys` on the projector. The adb key is the Platform-Tools key the projector already trusts, mounted read-only, so
no new "Allow USB debugging?" prompt appears.

Never expose `media_player.projector_adb` to Google. Only `media_player.tv` is exposed (`expose_by_default: false`).
Link with the dedicated non-admin Home Assistant user `google-link`, never with the owner account.

## Security rules in short

- Only Caddy's allowlisted paths reach Home Assistant from outside; `/api/` and the websocket answer 404.
- adb (5555) is never published or proxied; Home Assistant reaches the projector over the LAN only.
- Secrets (`.env`, `secrets/`, `config/secrets.yaml`, `config/SERVICE_ACCOUNT.json`) are gitignored.
- The Tailscale auth key is single-use and tagged `tag:ha`.

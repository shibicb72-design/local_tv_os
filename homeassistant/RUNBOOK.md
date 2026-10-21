# Runbook: voice control for the projector

Operator runbook. The full 14 steps are completed in step V4; this file holds the "Google side" section from V2.
Placeholders in angle brackets are replaced by the operator. Real values never go into the repo.

## Google side

Done with the **operator's** Google account (step 7 of the outline in `docs/plans/next-features.md` 6.6).
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
4. `scripts\check-exposure.ps1` must pass before linking (added in V4).

## Android Debug Bridge integration (projector)

Add the integration with host `10.0.0.242`, port 5555, default key path, and name the entity
`media_player.projector_adb` (`config/packages/tv.yaml` sends its commands there). Then open the integration's
System options and **turn polling off**: no `scan_interval`, no 10-second `dumpsys` on the projector.
Never expose `media_player.projector_adb` to Google. The full steps come in V4.

Only `media_player.tv` is exposed to Google (`expose_by_default: false`). Link with the dedicated non-admin
Home Assistant user `google-link`, never with the owner account.

"""Checks the Home Assistant voice-control stack in homeassistant/ without Docker.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
from pathlib import Path
import re
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]
HA = ROOT / "homeassistant"

# docs/plans/next-features.md 6.1: the only paths that may reach Home Assistant from outside.
ALLOWED_PATHS = [
    "/api/google_assistant",
    "/auth/authorize",
    "/auth/token",
    "/auth/login_flow*",
    "/auth/providers",
    "/frontend_latest/*",
    "/frontend_es5/*",
    "/static/*",
    "/manifest.json",
]


def load_compose():
    return yaml.safe_load((HA / "compose.yaml").read_text(encoding="utf-8"))


class ComposeTest(unittest.TestCase):
    def setUp(self):
        self.compose = load_compose()
        self.services = self.compose["services"]

    def test_services(self):
        self.assertEqual({"homeassistant", "caddy", "tailscale"}, set(self.services))

    def test_only_loopback_8123_is_published(self):
        published = []
        for name, svc in self.services.items():
            for port in svc.get("ports", []):
                published.append((name, str(port)))
        self.assertEqual([("homeassistant", "127.0.0.1:8123:8123")], published)

    def test_adb_port_is_never_mentioned_as_a_port(self):
        for name, svc in self.services.items():
            for key in ("ports", "expose"):
                for port in svc.get(key, []):
                    self.assertNotIn("5555", str(port), name)

    def test_images_are_pinned(self):
        for name, svc in self.services.items():
            image = svc["image"]
            self.assertIn(":", image, name)
            self.assertFalse(image.endswith(":latest"), name)
            self.assertFalse(image.endswith(":stable"), name)

    def test_tailscale_is_userspace(self):
        ts = self.services["tailscale"]
        self.assertEqual("true", str(ts["environment"]["TS_USERSPACE"]).lower())
        self.assertNotIn("devices", ts)
        self.assertNotIn("cap_add", ts)
        self.assertNotIn("privileged", ts)
        self.assertNotIn("network_mode", ts)
        self.assertNotIn("/dev/net/tun", json.dumps(ts))
        self.assertNotIn("NET_ADMIN", json.dumps(ts))
        self.assertIn("TS_SERVE_CONFIG", ts["environment"])
        self.assertIn("TS_STATE_DIR", ts["environment"])

    def test_adb_key_is_mounted_read_only(self):
        volumes = self.services["homeassistant"]["volumes"]
        for key in ("androidtv_adbkey", "androidtv_adbkey.pub"):
            mounts = [v for v in volumes if v.split(":")[1].endswith(key)]
            self.assertEqual(1, len(mounts), key)
            self.assertTrue(mounts[0].endswith(":ro"), key)

    def test_restart_policy(self):
        for name, svc in self.services.items():
            self.assertEqual("unless-stopped", svc["restart"], name)

    def test_subnet(self):
        subnet = self.compose["networks"]["ha_net"]["ipam"]["config"][0]["subnet"]
        self.assertEqual("172.30.0.0/24", subnet)


class CaddyTest(unittest.TestCase):
    def setUp(self):
        self.text = (HA / "caddy" / "Caddyfile").read_text(encoding="utf-8")

    def test_allowlist_matches_plan_exactly(self):
        matchers = re.findall(r"^\s*@\w+\s+path\s+(.+)$", self.text, re.MULTILINE)
        self.assertEqual(1, len(matchers))
        self.assertEqual(ALLOWED_PATHS, matchers[0].split())

    def test_only_allowed_matcher_is_proxied_and_rest_is_404(self):
        self.assertEqual(1, self.text.count("reverse_proxy"))
        self.assertRegex(self.text, r"handle @allowed \{\s*reverse_proxy homeassistant:8123\s*\}")
        # the last handle block is the catch-all and answers 404
        self.assertRegex(self.text.rstrip(), r"handle \{\s*respond \"[^\"]*\" 404\s*\}\s*\}$")

    def test_admin_api_is_off(self):
        self.assertRegex(self.text, r"admin off")


class ServeConfigTest(unittest.TestCase):
    def setUp(self):
        self.serve = json.loads((HA / "tailscale" / "serve.json").read_text(encoding="utf-8"))

    def test_funnel_only_443(self):
        funnel = self.serve["AllowFunnel"]
        self.assertTrue(funnel)
        for host_port, enabled in funnel.items():
            self.assertTrue(host_port.endswith(":443"), host_port)
            self.assertIs(True, enabled)

    def test_proxies_only_to_caddy(self):
        proxies = []
        for web in self.serve["Web"].values():
            for handler in web["Handlers"].values():
                proxies.append(handler["Proxy"])
        self.assertEqual(["http://caddy:8080"], proxies)

    def test_no_raw_tcp_forwarding(self):
        for port, cfg in self.serve["TCP"].items():
            self.assertEqual("443", port)
            self.assertNotIn("TCPForward", cfg)


class RepoHygieneTest(unittest.TestCase):
    def test_gitignore_covers_secrets(self):
        lines = (HA / ".gitignore").read_text(encoding="utf-8").split()
        for path in (
            ".env",
            "secrets/",
            "config/secrets.yaml",
            "config/SERVICE_ACCOUNT.json",
            "config/.storage/",
            "*.db*",
            "config/.HA_VERSION",
        ):
            self.assertIn(path, lines)

    def test_env_example_has_placeholders_only(self):
        values = {}
        for line in (HA / ".env.example").read_text(encoding="utf-8").splitlines():
            if line and not line.startswith("#"):
                key, _, value = line.partition("=")
                values[key] = value
        self.assertEqual({"TS_AUTHKEY", "TS_HOSTNAME", "TZ"}, set(values))
        self.assertTrue(values["TS_AUTHKEY"].startswith("<"))
        self.assertTrue(values["TZ"].startswith("<"))

    def test_no_secrets_in_tracked_files(self):
        patterns = [
            re.compile(r"tskey-[A-Za-z0-9-]{10,}"),
            re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+"),
            re.compile(r"\.ts\.net\b"),
        ]
        allowed_suffixes = {".yaml", ".yml", ".json", ".example", ".md", ".ps1", ".py", ""}
        for path in HA.rglob("*"):
            if not path.is_file() or path.name == ".gitignore":
                continue
            if "secrets" in path.relative_to(HA).parts[:1]:
                continue
            if path.suffix not in allowed_suffixes:
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            for pattern in patterns:
                for match in pattern.finditer(text):
                    # "<host>.<tailnet>.ts.net" style placeholders in docs are fine
                    if "<" in text[max(0, match.start() - 20):match.end() + 1]:
                        continue
                    self.fail(f"{path.relative_to(ROOT)}: {pattern.pattern} matches {match.group(0)!r}")


if __name__ == "__main__":
    unittest.main()

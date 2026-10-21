# com.xgimi.atmosphere ("XGIMI Wall"): what it does and what it costs
Measured: <date, time span>, build RT.3.24.329, Home = com.gathin.tvhome, state during sampling: <idle on Home>.
Raw files: C:\Users\shibi\cc_xgimi\inspect\<run>\atmosphere\ (not in the repo).

## 1. Identity
| Item | Value |
|---|---|
| Version name / code | <value> |
| UID | <value> |
| System app? | <yes/no> |
| Privileged? | <yes/no> |
| Enabled? | <yes/no> |
| APK size | <bytes> |
| First install | <date> |
| Last update | <date> |
| Signature | <platform / XGIMI / other> |

## 2. Components (from the manifest)
| Kind | Name | Exported | Intent filters / triggers | Permission |
|---|---|---|---|---|
| <activity / service / receiver / provider> | <name> | <yes/no> | <filters> | <permission or none> |

Dream service: <yes/no>.

## 3. Permissions requested / granted
| Permission | Granted | Why it matters |
|---|---|---|
| <permission> | <yes/no> | <reason> |

## 4. What starts it
| Trigger | Evidence (file:line) | Seen in this run? |
|---|---|---|
| <boot receiver / screensaver (dream) / launcher entry / alarm / job / sticky service / another XGIMI app binding it> | <file:line> | <yes/no> |

## 5. Assets
| Folder | Files | Total size | Content (scenes, video, audio, shaders) |
|---|---|---|---|
| <folder> | <n> | <bytes> | <content> |

## 6. Cost over 60 minutes at idle
| Sample | Running? | PID | RSS (MB) | PSS total (MB) | Java heap (MB) | Native (MB) | Graphics (MB) | top %CPU |
|---|---|---|---|---|---|---|---|---|
| <hh:mm:ss> | <yes/no> | <pid> | <value> | <value> | <value> | <value> | <value> | <value> |
| Mean / max | | | <value> | <value> | <value> | <value> | <value> | <value> |

CPU time over the window: (utime+stime delta) / CLK_TCK = <s> → <%> of one core. Since boot (batterystats): <ms>.
Network over the window: rx <B>, tx <B>. Wakeups: alarms <n>/h, jobs <n>/h.

## 7. Active cost (optional, 5 min while XGIMI Wall is shown)
| PSS | %CPU | janky frames / total (gfxinfo) |
|---|---|---|
| <value> | <value> | <value> |

## 8. Verdict
Heavy: <yes/no>, against the thresholds in docs/plans/next-features.md 7.4 (list each threshold and the measured value).
Recommendation: <keep as is | keep hidden from the grid | disable in debloat batch 4 (Shibi decides) | replace with RomanDream>.
The package stays installed either way.

# Work instructions — Engineering Lead / local_tv_os

## Standing rule (caller, Oct 25 2026)

When the caller (Shibi) is talking through ideas, **do NOT start acting in the middle of them**.

1. Collect all the ideas.
2. Take them into account.
3. Summarize and contextualize them into a grander picture first.
4. **Only act** when the caller explicitly says go (examples of go: "okay, where are we at?" then "I want to create a prompt from this", then go — or any clear "go / do it / ship it / commit it").

Default: summarize and collect before acting. Act only on an explicit go.

This applies to Engineering Lead and anyone working this repo under that lead.

## Project context

- Building a **new local OS experience** for a **Vizio TV** (replacing the prior XGIMI Horizon S Max target).
- Base: this repo — a fork of CleanYourTV (`local_tv_os`).
- Goals: Apple TV–style theme and layout (home screen, poster rows, floating selection glow); keep core TV functions (settings, apps); strip adware, bloatware, and tracking.
- Do not invent timelines in plans or prompts unless the caller asks.

## Hard platform note (do not ignore)

The current target set is **VIZIO VQM85C-1010** running **VIZIO OS / SmartCast**, not Android TV. ADB + leanback launcher patterns from the XGIMI work do not apply as-is. Any dig must verify what is actually possible on this panel (SmartCast API, cast, HDMI/external Android box, etc.) before assuming package-disable or APK Home replacement.

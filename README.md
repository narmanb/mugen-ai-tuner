# MUGEN AI Tuner

Android app for analyzing, explaining, and safely tuning MUGEN/IKEMEN character AI difficulty with backups, behavior controls, and offline code analysis.

## Goals

- Analyze MUGEN/IKEMEN character AI without an LLM or internet connection.
- Detect direct `AILevel` use, AI flag variables, and common legacy AI activation patterns.
- Explain detected AI behavior in plain language with confidence ratings.
- Provide safe difficulty presets and custom behavior controls only when the analyzer is confident about an edit.
- Preview changes before writing them.
- Create versioned backups of every modified text file and support restore/undo.
- Keep the analysis engine platform-independent so a desktop frontend can be added later.

## Project structure

- `app-android/` — native Android UI and Storage Access Framework integration.
- `core/` — platform-independent Kotlin parser, analyzer, difficulty model, and edit planning.
- `.github/workflows/` — automated tests and Android debug APK builds.

## Safety principle

MUGEN does not reserve `var(59)` (or any other variable) for AI. The analyzer traces how variables are assigned and used instead of assuming variable numbers have fixed meanings. Uncertain code is reported but is not modified automatically.

## Status

Early development. The first milestone is a reliable offline analyzer and safe edit-planning engine before automatic modification is enabled broadly.

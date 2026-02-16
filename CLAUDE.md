# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Solar angle calculation library for computing optimal solar panel angles based on date, time, and geographic position. Supports fixed installations, single-axis trackers, and dual-axis trackers. Intended language is Clojure.

## Project Status

**Implemented:**
- `com.kardashevtypev.solar.angles` — Core solar position and panel angle calculations (src/com/kardashevtypev/solar/angles.clj)
- `com.kardashevtypev.solar.angles.spec` — Clojure specs for validation (src/com/kardashevtypev/solar/angles/spec.clj)
- `com.kardashevtypev.solar.lookup-table` — Precomputed lookup tables for single-axis and dual-axis trackers (src/com/kardashevtypev/solar/lookup_table.clj)
- `com.kardashevtypev.solar.lookup-table.spec` — Specs for lookup table config and structures (src/com/kardashevtypev/solar/lookup_table/spec.clj)
- Comprehensive test suites (test/com/kardashevtypev/solar/angles_test.clj, test/com/kardashevtypev/solar/lookup_table_test.clj)

**Design documents** in `dev/archnotes/`:
- `solar-panel-angle-calculations.md` — Core solar position formulas (declination, hour angle, zenith, azimuth, equation of time) and panel angle calculations for all tracker types. Contains the reference Clojure implementation.
- `solar-angle-lookup-tables.md` — Precomputed lookup table design for embedded/real-time use. Covers table structures, compact encodings (int16, delta, polynomial), interpolation, and storage budgets.

## Architecture (from design docs)

**Calculation pipeline:** day-of-year → intermediate angle B → equation of time → local solar time → hour angle + declination → zenith/azimuth → panel angles

**Key namespaces:**
- `com.kardashevtypev.solar.angles` — Core solar position and panel angle functions. Entry point is `solar-position` which returns a map with `:zenith`, `:altitude`, `:azimuth`, `:hour-angle`, `:declination`, etc.
- `com.kardashevtypev.solar.lookup-table` — Precomputed angle tables indexed by `[day-of-year][interval]`. Depends on `com.kardashevtypev.solar.angles`.

**Conventions:**
- Angles in degrees (radians only internally for trig)
- Latitude positive = North, longitude negative = West
- Azimuth: 0° = North, 90° = East, 180° = South, 270° = West
- Hour angle: negative = morning, positive = afternoon, 0° = solar noon
- Default reference location: Springfield, IL (39.8°N, 89.6°W, std meridian -90°)

## Git Commit Handling

**GPG Signing:** Before creating commits, check if GPG signing is required:

```bash
git config --get commit.gpgsign
```

- If the result is `true`, **DO NOT** create commits automatically (GPG signing requires interactive passphrase entry)
- Instead:
  1. Stage the relevant files with `git add`
  2. Print a commit message for the user to use manually
  3. Inform the user they need to commit manually due to GPG signing: `git commit -m "message"`

- If the result is empty or `false`, proceed with normal commit workflow

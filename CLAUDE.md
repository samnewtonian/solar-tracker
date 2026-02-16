# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Solar angle calculation library for computing optimal solar panel angles based on date, time, and geographic position. Supports fixed installations, single-axis trackers, and dual-axis trackers. Implemented in both Clojure and Python with numerically identical results.

## Project Structure

```
clojure/                          # Clojure implementation
  deps.edn                        # Project dependencies
  src/com/kardashevtypev/solar/
    angles.clj                    # Core solar position & panel angle calculations
    angles/spec.clj               # Clojure specs for validation
    lookup_table.clj              # Precomputed lookup tables
    lookup_table/spec.clj         # Specs for lookup table config & structures
  test/com/kardashevtypev/solar/
    angles_test.clj               # Angles test suite (42 tests / 11k+ assertions)
    lookup_table_test.clj         # Lookup table test suite

python/                           # Python implementation (package: solar_tracker)
  pyproject.toml                  # Hatchling build, requires Python >=3.11
  solar_tracker/
    __init__.py                   # Re-exports full public API
    _types.py                     # Frozen dataclasses & Season StrEnum
    angles.py                     # Core solar position & panel angle calculations
    lookup_table.py               # Precomputed lookup tables
  tests/
    conftest.py                   # Shared test config
    test_angles.py                # Angles test suite (148 parametrized cases)
    test_lookup_table.py          # Lookup table test suite

dev/archnotes/                    # Design documents
doc/                              # Implementation notes
```

## Implementation Notes

### Clojure (`clojure/`)

- Package: `com.kardashevtypev.solar.{angles,lookup-table}`
- Keyword maps for return types (`:zenith`, `:altitude`, `:azimuth`, etc.)
- Clojure specs for input/output validation
- Run tests: `cd clojure && clj -X:test`

### Python (`python/`)

- Package: `solar_tracker`
- Frozen dataclasses for return types (`SolarPosition`, `DualAxisAngles`, etc.)
- `Season` is a `StrEnum` (`"summer"`, `"winter"`, `"spring"`, `"fall"`)
- Type hints on all public functions; no runtime validation module
- No external dependencies (stdlib only); `pytest` is a dev dependency
- Run tests: `cd python && python -m pytest`

## Architecture

**Calculation pipeline:** day-of-year → intermediate angle B → equation of time → local solar time → hour angle + declination → zenith/azimuth → panel angles

**Module structure (both implementations):**
- `angles` — Core solar position and panel angle functions. Entry point is `solar_position` / `solar-position` which returns all computed angles.
- `lookup_table` / `lookup-table` — Precomputed angle tables indexed by `[day-of-year][interval]`. Depends on `angles`.

**Conventions (shared across both implementations):**
- Angles in degrees (radians only internally for trig)
- Latitude positive = North, longitude negative = West
- Azimuth: 0° = North, 90° = East, 180° = South, 270° = West
- Hour angle: negative = morning, positive = afternoon, 0° = solar noon
- Default reference location: Springfield, IL (39.8°N, 89.6°W, std meridian -90°)

## Design Documents

In `dev/archnotes/`:
- `solar-panel-angle-calculations.md` — Core solar position formulas (declination, hour angle, zenith, azimuth, equation of time) and panel angle calculations for all tracker types.
- `solar-angle-lookup-tables.md` — Precomputed lookup table design for embedded/real-time use. Covers table structures, compact encodings (int16, delta, polynomial), interpolation, and storage budgets.

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

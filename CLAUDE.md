# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Solar angle calculation library for computing optimal solar panel angles based on date, time, and geographic position. Supports fixed installations, single-axis trackers, and dual-axis trackers. Implemented in Clojure (reference), Python, and Rust.

## Project Structure

```
clojure/                          # Clojure implementation
  deps.edn                        # Project dependencies
  examples/
    calculation.clj               # Standalone example
  src/com/kardashevtypev/solar/
    angles.clj                    # Core solar position & panel angle calculations
    angles/spec.clj               # Clojure specs for validation
    lookup_table.clj              # Precomputed lookup tables
    lookup_table/spec.clj         # Specs for lookup table config & structures
  test/com/kardashevtypev/solar/
    angles_test.clj               # Angles test suite (42 tests / 10k+ assertions)
    lookup_table_test.clj         # Lookup table test suite
    test_util.clj                 # Shared test helpers (approx=)

python/                           # Python implementation (package: solar_tracker)
  pyproject.toml                  # Hatchling build, requires Python >=3.11
  examples/
    calculation.py                # Standalone example
  solar_tracker/
    __init__.py                   # Re-exports full public API
    _types.py                     # Frozen dataclasses & Season StrEnum
    angles.py                     # Core solar position & panel angle calculations
    lookup_table.py               # Precomputed lookup tables
  tests/
    conftest.py                   # Shared test config
    test_angles.py                # Angles test suite (148 parametrized cases)
    test_lookup_table.py          # Lookup table test suite

rust/                             # Rust implementation (crate: solar_tracker)
  Cargo.toml                      # Depends on chrono
  examples/
    calculation.rs                # Standalone example using chrono-tz
  src/
    lib.rs                        # Crate root, mod declarations + pub use re-exports
    types.rs                      # Structs, enums, Default impl
    angles.rs                     # Core solar position & panel angle calculations
    lookup_table.rs               # Precomputed lookup tables
  tests/
    test_angles.rs                # Angles integration tests (49 tests)
    test_lookup_table.rs          # Lookup table integration tests (33 tests)

dev/archnotes/                    # Design documents
doc/                              # Documentation
  api-reference.md                # Cross-language public API reference
  internal-helpers.md             # Per-language internal helper catalog
```

## Implementation Notes

### Clojure (`clojure/`) — reference implementation

- Package: `com.kardashevtypev.solar.{angles,lookup-table}`
- **`solar-position` takes `[latitude longitude ZonedDateTime]`** — accepts any timezone-aware datetime, converts to UTC internally via `.withZoneSameInstant`
- No `std-meridian` parameter or `local-solar-time` function; UTC-based calculation uses `utc-lst-correction` (longitude + EoT) computed once per day
- Public helpers: `leap-year?`, `days-in-months`, `utc-lst-correction`, `solar-angles-at`
- Keyword maps for return types (`:zenith`, `:altitude`, `:azimuth`, etc.)
- Clojure specs for input/output validation
- Run tests: `cd clojure && clj -X:test`

### Python (`python/`)

- Package: `solar_tracker`
- **`solar_position` takes `(latitude, longitude, datetime)`** — accepts any timezone-aware `datetime`, converts to UTC internally via `.astimezone(timezone.utc)`. Raises `ValueError` for naive datetimes.
- No `std_meridian` parameter or `local_solar_time` function; UTC-based calculation uses `utc_lst_correction` (longitude + EoT) computed once per day
- Public helpers: `leap_year`, `days_in_months`, `utc_lst_correction`, `solar_angles_at`
- Frozen dataclasses for return types (`SolarPosition`, `DualAxisAngles`, etc.)
- `Season` is a `StrEnum` (`"summer"`, `"winter"`, `"spring"`, `"fall"`)
- Type hints on all public functions; no runtime validation module
- No external dependencies (stdlib only); `pytest` is a dev dependency
- Run tests: `cd python && python -m pytest`

### Rust (`rust/`)

- Crate: `solar_tracker`
- **`solar_position` takes `(latitude, longitude, &DateTime<Tz>)`** — accepts any `chrono::DateTime<Tz>`, converts to UTC internally via `.with_timezone(&Utc)`
- No `std_meridian` parameter or `local_solar_time` function; UTC-based calculation uses `utc_lst_correction` (longitude + EoT) computed once per day
- Public helpers: `leap_year`, `days_in_months`, `utc_lst_correction`, `solar_angles_at`
- Structs with derives for return types (`SolarPosition`, `DualAxisAngles`, etc.)
- `Season` is an enum with variants `Summer`, `Winter`, `Spring`, `Fall`
- Generic `LookupTable<E>` and `DayData<E>` with type aliases `SingleAxisTable` / `DualAxisTable`
- Depends on `chrono` (with `clock` feature)
- Run tests: `cd rust && cargo test`
- Lint: `cd rust && cargo clippy -- -D warnings`

## Architecture

**Calculation pipeline (all implementations):** timezone-aware datetime → UTC conversion → day-of-year → equation of time + declination → `utc-lst-correction` (constant per day) → local solar time → hour angle → zenith/azimuth → panel angles

**Module structure (all implementations):**
- `angles` — Core solar position and panel angle functions. Entry point is `solar_position` / `solar-position` which returns all computed angles.
- `lookup_table` / `lookup-table` — Precomputed angle tables indexed by `[day-of-year][UTC-minutes]`. Depends on `angles`. Separate generation for single-axis and dual-axis. Precomputes sin/cos of latitude (once) and declination (per day) for table generation.

**Conventions (shared across all implementations):**
- Angles in degrees (radians only internally for trig)
- Latitude positive = North, longitude negative = West
- Azimuth: 0° = North, 90° = East, 180° = South, 270° = West
- Hour angle: negative = morning, positive = afternoon, 0° = solar noon
- Default reference location: Springfield, IL (39.8°N, 89.6°W)

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

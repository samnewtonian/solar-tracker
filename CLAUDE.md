# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Solar angle calculation library for computing optimal solar panel angles based on date, time, and geographic position. Supports fixed installations, single-axis trackers, and dual-axis trackers. Intended language is Clojure.

## Project Status

Early stage — no source code yet. Design documents live in `dev/archnotes/`:

- `solar-panel-angle-calculations.md` — Core solar position formulas (declination, hour angle, zenith, azimuth, equation of time) and panel angle calculations for all tracker types. Contains the reference Clojure implementation with two namespaces: `solar.angles` (core calculations) and usage examples.
- `solar-angle-lookup-tables.md` — Precomputed lookup table design for embedded/real-time use. Covers table structures, compact encodings (int16, delta, polynomial), interpolation, and storage budgets. Contains reference `solar.lookup-table` namespace depending on `solar.angles`.

## Architecture (from design docs)

**Calculation pipeline:** day-of-year → intermediate angle B → equation of time → local solar time → hour angle + declination → zenith/azimuth → panel angles

**Key namespaces (planned):**
- `solar.angles` — Core solar position and panel angle functions. Entry point is `solar-position` which returns a map with `:zenith`, `:altitude`, `:azimuth`, `:hour-angle`, `:declination`, etc.
- `solar.lookup-table` — Precomputed angle tables indexed by `[day-of-year][interval]`. Depends on `solar.angles`.

**Conventions:**
- Angles in degrees (radians only internally for trig)
- Latitude positive = North, longitude negative = West
- Azimuth: 0° = North, 90° = East, 180° = South, 270° = West
- Hour angle: negative = morning, positive = afternoon, 0° = solar noon
- Default reference location: Springfield, IL (39.8°N, 89.6°W, std meridian -90°)

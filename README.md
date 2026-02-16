# solar-tracker

Solar angle calculation library for computing optimal solar panel angles based on date, time, and geographic position. Supports fixed installations, single-axis trackers, and dual-axis trackers.

Implemented in Clojure and Python with numerically identical results. Uses standard solar position algorithms (NOAA / Duffie & Beckman).

## Features

- **Solar position** — zenith, altitude, azimuth, declination, hour angle, equation of time
- **Dual-axis tracking** — tilt and panel azimuth to point directly at the sun
- **Single-axis tracking** — rotation angle for north-south horizontal trackers
- **Fixed installations** — optimal annual tilt and seasonal adjustments
- **Lookup tables** — precomputed daylight-only tables with interpolated lookups

## Requirements

**Clojure:** Clojure 1.12+, Java 11+

**Python:** Python 3.11+ (no external dependencies; pytest for dev)

## Usage

### Clojure

Add as a git dependency in your `deps.edn` (use `:deps/root` since the Clojure source lives under `clojure/`):

```clojure
{:deps
 {io.github.samnewtonian/solar-tracker
  {:git/sha "..."
   ;; Optional: :git/tag "v0.1.0"
   :deps/root "clojure"}}}
```

Then in your code:

```clojure
(require '[com.kardashevtypev.solar.angles :as sa])

;; Calculate solar position for Springfield, IL on March 21 at noon
;; (39.8°N, 89.6°W, Central Time std meridian -90°)
(sa/solar-position 39.8 -89.6 2026 3 21 12 0 -90.0)
;; => {:day-of-year 80
;;     :declination -0.40
;;     :equation-of-time -7.86
;;     :local-solar-time 11.84
;;     :hour-angle -2.36
;;     :zenith 40.26
;;     :altitude 49.74
;;     :azimuth 176.34}

;; Dual-axis tracker angles
(sa/dual-axis-angles (sa/solar-position 39.8 -89.6 2026 6 21 14 30 -90.0))
;; => {:tilt <zenith>, :panel-azimuth <facing-sun>}

;; Single-axis tracker rotation
(let [pos (sa/solar-position 39.8 -89.6 2026 3 21 15 0 -90.0)]
  (sa/single-axis-tilt pos 39.8))

;; Optimal fixed tilt for a latitude
(sa/optimal-fixed-tilt 39.8)   ;; => 33.3°

;; Seasonal tilt adjustments
(sa/seasonal-tilt-adjustment 40.0 :summer)  ;; => 25.0°
(sa/seasonal-tilt-adjustment 40.0 :winter)  ;; => 55.0°
```

### Python

```python
from solar_tracker import solar_position, dual_axis_angles, single_axis_tilt
from solar_tracker import optimal_fixed_tilt, seasonal_tilt_adjustment, Season

# Calculate solar position for Springfield, IL on March 21 at noon
pos = solar_position(39.8, -89.6, 2026, 3, 21, 12, 0, -90.0)
# SolarPosition(day_of_year=80, declination=-0.40, equation_of_time=-7.86,
#   local_solar_time=11.84, hour_angle=-2.36, zenith=40.26,
#   altitude=49.74, azimuth=176.34)

# Dual-axis tracker angles
da = dual_axis_angles(solar_position(39.8, -89.6, 2026, 6, 21, 14, 30, -90.0))
# DualAxisAngles(tilt=..., panel_azimuth=...)

# Single-axis tracker rotation
pos = solar_position(39.8, -89.6, 2026, 3, 21, 15, 0, -90.0)
single_axis_tilt(pos, 39.8)

# Optimal fixed tilt for a latitude
optimal_fixed_tilt(39.8)   # => 33.3°

# Seasonal tilt adjustments
seasonal_tilt_adjustment(40.0, Season.SUMMER)  # => 25.0°
seasonal_tilt_adjustment(40.0, Season.WINTER)  # => 55.0°
```

## API Reference

Both implementations expose the same functions with identical signatures (modulo language naming conventions). The tables below use the Python names; Clojure equivalents use kebab-case (e.g. `solar_position` → `solar-position`).

### Core Solar Position (`angles`)

| Function | Description |
|----------|-------------|
| `solar_position(lat, lon, year, month, day, hour, minute, std_meridian)` | Full solar position; returns all computed angles |
| `day_of_year(year, month, day)` | Calendar date to day number (1-366) |
| `solar_declination(n)` | Declination angle for day of year |
| `equation_of_time(n)` | Equation of time correction in minutes |
| `local_solar_time(local_time, std_meridian, longitude, n)` | Clock time to true solar time |
| `hour_angle(local_solar_time)` | Hour angle from local solar time |
| `solar_zenith_angle(lat, decl, hour_angle)` | Zenith angle |
| `solar_altitude(zenith)` | Altitude (complement of zenith) |
| `solar_azimuth(lat, decl, hour_angle)` | Azimuth (0°=N, 90°=E, 180°=S) |

### Panel Angles (`angles`)

| Function | Description |
|----------|-------------|
| `dual_axis_angles(solar_pos)` | Tilt and panel azimuth for dual-axis tracker |
| `single_axis_tilt(solar_pos, latitude)` | Rotation angle for single-axis N-S tracker |
| `optimal_fixed_tilt(latitude)` | Optimal annual fixed tilt (empirical formula) |
| `seasonal_tilt_adjustment(latitude, season)` | Seasonal tilt; season is `summer`, `winter`, `spring`, or `fall` |

### Lookup Tables (`lookup_table`)

| Function | Description |
|----------|-------------|
| `generate_single_axis_table(config)` | Generate precomputed single-axis table for 365 days |
| `generate_dual_axis_table(config)` | Generate precomputed dual-axis table for 365 days |
| `lookup_single_axis(table, day_of_year, minutes)` | Interpolated single-axis lookup by day and time |
| `lookup_dual_axis(table, day_of_year, minutes)` | Interpolated dual-axis lookup by day and time |
| `estimate_sunrise_sunset(latitude, day_of_year)` | Sunrise/sunset estimate in minutes from midnight |
| `interpolate_angle(a1, a2, fraction)` | Angle interpolation with 360° wraparound handling |
| `table_to_compact(table)` | Strip metadata; return nested lists of angle values |
| `doy_to_month_day(year, doy)` | Day-of-year to (month, day) |
| `minutes_to_time(minutes)` | Minutes since midnight to (hour, minute) |
| `time_to_minutes(hour, minute)` | (hour, minute) to minutes since midnight |
| `intervals_per_day(interval_minutes)` | Number of intervals in a day |

### Conventions

- All angles in degrees (radians used internally only)
- Latitude: positive = North, negative = South
- Longitude: negative = West, positive = East
- Azimuth: 0° = North, 90° = East, 180° = South, 270° = West
- Hour angle: negative = morning, positive = afternoon, 0° = solar noon

## Running Tests

```bash
# Clojure
cd clojure && clj -X:test

# Python
cd python && python -m pytest
```

## References

- NOAA Solar Calculator methodology
- Duffie & Beckman, *Solar Engineering of Thermal Processes*
- PVEducation.org solar position algorithms

## License

Copyright 2026

Licensed under the Apache License, Version 2.0

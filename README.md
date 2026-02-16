# solar-tracker

Solar angle calculation library for computing optimal solar panel angles based on date, time, and geographic position. Supports fixed installations, single-axis trackers, and dual-axis trackers.

Written in Clojure. Uses standard solar position algorithms (NOAA / Duffie & Beckman).

## Features

- **Solar position** — zenith, altitude, azimuth, declination, hour angle, equation of time
- **Dual-axis tracking** — tilt and panel azimuth to point directly at the sun
- **Single-axis tracking** — rotation angle for north-south horizontal trackers
- **Fixed installations** — optimal annual tilt and seasonal adjustments

## Requirements

- Clojure 1.12+
- Java 11+

## Usage

Add the dependency to your `deps.edn` (or clone the repo directly):

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

## API

### Core Solar Position

| Function | Description |
|----------|-------------|
| `(solar-position lat lon year month day hour minute std-meridian)` | Full solar position calculation; returns a map with all angles |
| `(day-of-year year month day)` | Calendar date to day number (1-366) |
| `(solar-declination n)` | Declination angle for day of year |
| `(equation-of-time n)` | Equation of time correction in minutes |
| `(local-solar-time local-time std-meridian longitude n)` | Clock time to true solar time |
| `(hour-angle local-solar-time)` | Hour angle from local solar time |
| `(solar-zenith-angle lat decl hour-angle)` | Zenith angle |
| `(solar-altitude zenith)` | Altitude (complement of zenith) |
| `(solar-azimuth lat decl hour-angle zenith)` | Azimuth (0°=N, 90°=E, 180°=S) |

### Panel Angles

| Function | Description |
|----------|-------------|
| `(dual-axis-angles solar-pos)` | Tilt and panel azimuth for dual-axis tracker |
| `(single-axis-tilt solar-pos latitude)` | Rotation angle for single-axis N-S tracker |
| `(optimal-fixed-tilt latitude)` | Optimal annual fixed tilt (empirical formula) |
| `(seasonal-tilt-adjustment latitude season)` | Seasonal tilt; season is `:summer`, `:winter`, `:spring`, or `:fall` |

### Conventions

- All angles in degrees (radians used internally only)
- Latitude: positive = North, negative = South
- Longitude: negative = West, positive = East
- Azimuth: 0° = North, 90° = East, 180° = South, 270° = West
- Hour angle: negative = morning, positive = afternoon, 0° = solar noon

## Running Tests

```bash
clj -X:test
```

## Project Structure

```
src/
  com/kardashevtypev/solar/
    angles.clj              # Core calculations
    angles/
      spec.clj              # Clojure specs for angles
    lookup_table.clj        # Precomputed lookup tables
    lookup_table/
      spec.clj              # Clojure specs for lookup tables
test/
  com/kardashevtypev/solar/
    angles_test.clj         # Angles test suite
    lookup_table_test.clj   # Lookup table test suite
dev/
  archnotes/                # Design documents
doc/                        # Implementation notes
```

## References

- NOAA Solar Calculator methodology
- Duffie & Beckman, *Solar Engineering of Thermal Processes*
- PVEducation.org solar position algorithms

## License

Copyright 2026

Licensed under the Apache License, Version 2.0

# API Reference

Public API for the `solar_tracker` library. Covers all three implementations: Rust, Python, and Clojure.

## Overview

Solar angle calculation library for computing optimal solar panel angles based on date, time, and geographic position. Supports fixed installations, single-axis trackers, and dual-axis trackers.

### Conventions

- All angles are in **degrees** (radians used only internally for trigonometry).
- **Latitude**: positive = North, negative = South.
- **Longitude**: positive = East, negative = West.
- **Azimuth**: 0° = North, 90° = East, 180° = South, 270° = West.
- **Hour angle**: negative = morning (sun east), positive = afternoon (sun west), 0° = solar noon.
- **Default reference location**: Springfield, IL (39.8°N, 89.6°W).

### Naming Conventions

| Convention | Rust | Python | Clojure |
|---|---|---|---|
| Function names | `snake_case` | `snake_case` | `kebab-case` |
| Type names | `PascalCase` structs | `PascalCase` dataclasses | keyword maps |
| Constants | `SCREAMING_SNAKE` | `SCREAMING_SNAKE` | `kebab-case` |

---

## Types

### `SolarPosition`

Complete solar position result returned by `solar_position`.

| Field | Type | Unit | Description |
|---|---|---|---|
| `day_of_year` | int | — | Ordinal day of year in UTC (1–366) |
| `declination` | float | degrees | Solar declination angle |
| `equation_of_time` | float | minutes | Equation of time correction |
| `local_solar_time` | float | hours | True local solar time (decimal hours) |
| `hour_angle` | float | degrees | Solar hour angle |
| `zenith` | float | degrees | Solar zenith angle |
| `altitude` | float | degrees | Solar altitude/elevation angle |
| `azimuth` | float | degrees | Solar azimuth angle |

- **Rust**: `SolarPosition` struct with `pub` fields.
- **Python**: frozen `@dataclass`.
- **Clojure**: keyword map with keys `:day-of-year`, `:declination`, `:equation-of-time`, `:local-solar-time`, `:hour-angle`, `:zenith`, `:altitude`, `:azimuth`.

### `DualAxisAngles`

Panel orientation angles for a dual-axis tracker.

| Field | Type | Unit | Description |
|---|---|---|---|
| `tilt` | float | degrees | Panel tilt angle (equals solar zenith) |
| `panel_azimuth` | float | degrees | Panel azimuth (sun azimuth + 180°, normalized) |

- **Clojure**: keyword map with `:tilt`, `:panel-azimuth`.

### `SunriseSunset`

Estimated sunrise and sunset times.

| Field | Type | Unit | Description |
|---|---|---|---|
| `sunrise` | int | minutes | Minutes from midnight (local solar time) |
| `sunset` | int | minutes | Minutes from midnight (local solar time) |

- **Clojure**: keyword map with `:sunrise`, `:sunset`.

### `Season`

Season enumeration for seasonal tilt adjustments.

| Variant | Rust | Python | Clojure |
|---|---|---|---|
| Summer | `Season::Summer` | `Season.SUMMER` / `"summer"` | `:summer` |
| Winter | `Season::Winter` | `Season.WINTER` / `"winter"` | `:winter` |
| Spring | `Season::Spring` | `Season.SPRING` / `"spring"` | `:spring` |
| Fall | `Season::Fall` | `Season.FALL` / `"fall"` | `:fall` |

- **Python**: `StrEnum` — each variant's value is its lowercase string.

### `LookupTableConfig`

Configuration for lookup table generation.

| Field | Type | Default | Description |
|---|---|---|---|
| `interval_minutes` | int | 5 | Minutes between table entries |
| `latitude` | float | 39.8 | Observer latitude (degrees) |
| `longitude` | float | -89.6 | Observer longitude (degrees) |
| `year` | int | 2026 | Calendar year |
| `sunrise_buffer_minutes` | int | 30 | Extra minutes before sunrise |
| `sunset_buffer_minutes` | int | 30 | Extra minutes after sunset |

- **Rust**: struct with `Default` impl.
- **Python**: frozen `@dataclass` with default values.
- **Clojure**: plain map. Default provided as `default-config`.

### `LookupTable`

A precomputed angle lookup table.

| Field | Type | Description |
|---|---|---|
| `config` | `LookupTableConfig` | The configuration used to generate the table |
| `days` | list of `DayData` | One entry per day of year |
| `metadata` | `TableMetadata` | Generation metadata |

- **Rust**: generic `LookupTable<E>` with type aliases `SingleAxisTable` and `DualAxisTable`.
- **Python / Clojure**: single type; entry type varies by generator.

### `DayData`

Per-day data within a lookup table.

| Field | Type | Description |
|---|---|---|
| `day_of_year` | int | Ordinal day (1–366) |
| `sunrise_minutes` | int | Estimated sunrise (local solar time, minutes) |
| `sunset_minutes` | int | Estimated sunset (local solar time, minutes) |
| `entries` | list | Angle entries for this day |

- **Rust**: generic `DayData<E>`.
- **Clojure**: keyword map with `:day-of-year`, `:sunrise-minutes`, `:sunset-minutes`, `:entries`.

### `TableMetadata`

Metadata about a generated table.

| Field | Type | Description |
|---|---|---|
| `generated_at` | string | ISO 8601 timestamp of generation (UTC) |
| `total_entries` | int | Total number of entries across all days |
| `storage_estimate_kb` | float | Estimated storage size in kilobytes |

- **Clojure**: keyword map with `:generated-at`, `:total-entries`, `:storage-estimate-kb`.

### `SingleAxisEntry`

One entry in a single-axis lookup table.

| Field | Type | Description |
|---|---|---|
| `minutes` | int | UTC minutes since midnight |
| `rotation` | float or nil | Rotation angle (degrees), nil if nighttime |

- **Rust**: `rotation: Option<f64>`.
- **Clojure**: keyword map with `:minutes`, `:rotation` (nil if nighttime).

### `DualAxisEntry`

One entry in a dual-axis lookup table.

| Field | Type | Description |
|---|---|---|
| `minutes` | int | UTC minutes since midnight |
| `tilt` | float or nil | Tilt angle (degrees), nil if nighttime |
| `panel_azimuth` | float or nil | Panel azimuth (degrees), nil if nighttime |

- **Rust**: `tilt: Option<f64>`, `panel_azimuth: Option<f64>`.
- **Clojure**: keyword map with `:minutes`, `:tilt`, `:panel-azimuth`.

---

## Constants

### `EARTH_AXIAL_TILT`

Earth's axial tilt in degrees: **23.45**.

| Rust | Python | Clojure |
|---|---|---|
| `EARTH_AXIAL_TILT: f64` | `EARTH_AXIAL_TILT` | `earth-axial-tilt` |

### `DEGREES_PER_HOUR`

Degrees of Earth rotation per hour: **15.0**.

| Rust | Python | Clojure |
|---|---|---|
| `DEGREES_PER_HOUR: f64` | `DEGREES_PER_HOUR` | `degrees-per-hour` |

### `DEFAULT_CONFIG`

Default `LookupTableConfig` with Springfield, IL coordinates and 5-minute intervals.

| Rust | Python | Clojure |
|---|---|---|
| `LookupTableConfig::default()` | `DEFAULT_CONFIG` | `default-config` |

Note: Rust uses the `Default` trait rather than a named constant.

---

## Core Angle Functions (`angles` module)

### `deg_to_rad`

Convert degrees to radians.

| | Signature |
|---|---|
| **Rust** | `deg_to_rad(deg: f64) -> f64` |
| **Python** | `deg_to_rad(deg: float) -> float` |
| **Clojure** | `(deg->rad deg)` |

### `rad_to_deg`

Convert radians to degrees.

| | Signature |
|---|---|
| **Rust** | `rad_to_deg(rad: f64) -> f64` |
| **Python** | `rad_to_deg(rad: float) -> float` |
| **Clojure** | `(rad->deg rad)` |

### `normalize_angle`

Normalize an angle to the range [0, 360).

| | Signature |
|---|---|
| **Rust** | `normalize_angle(angle: f64) -> f64` |
| **Python** | `normalize_angle(angle: float) -> float` |
| **Clojure** | `(normalize-angle angle)` |

### `leap_year`

Returns true if the given year is a leap year.

| | Signature |
|---|---|
| **Rust** | `leap_year(year: i32) -> bool` |
| **Python** | `leap_year(year: int) -> bool` |
| **Clojure** | `(leap-year? year)` |

### `days_in_months`

Returns a sequence of 12 integers — the number of days in each month for the given year.

| | Signature |
|---|---|
| **Rust** | `days_in_months(year: i32) -> [u32; 12]` |
| **Python** | `days_in_months(year: int) -> list[int]` |
| **Clojure** | `(days-in-months year)` → vector of 12 ints |

### `day_of_year`

Calculate the ordinal day of year (1–366) from year, month, and day.

| | Signature |
|---|---|
| **Rust** | `day_of_year(year: i32, month: u32, day: u32) -> i32` |
| **Python** | `day_of_year(year: int, month: int, day: int) -> int` |
| **Clojure** | `(day-of-year year month day)` |

### `intermediate_angle_b`

Calculate intermediate angle B used in the equation of time. Takes the day of year (1–365), returns B in radians.

Formula: `B = (n - 1) × 360° / 365`, converted to radians.

| | Signature |
|---|---|
| **Rust** | `intermediate_angle_b(n: i32) -> f64` |
| **Python** | `intermediate_angle_b(n: int) -> float` |
| **Clojure** | `(intermediate-angle-b n)` |

### `equation_of_time`

Calculate the Equation of Time correction. Accounts for Earth's elliptical orbit and axial tilt, causing solar noon to drift from clock noon throughout the year.

**Parameters**: `n` — day of year (1–365).
**Returns**: correction in minutes.

| | Signature |
|---|---|
| **Rust** | `equation_of_time(n: i32) -> f64` |
| **Python** | `equation_of_time(n: int) -> float` |
| **Clojure** | `(equation-of-time n)` |

### `utc_lst_correction`

Compute the UTC-to-local-solar-time correction in hours for a given longitude and equation of time.

Formula: `correction = (4 × longitude + EoT) / 60`

To get local solar time: `LST = (UTC_hours + correction) mod 24`.

**Parameters**:
- `longitude` — observer's longitude in degrees (negative for West).
- `eot` — equation of time in minutes.

**Returns**: correction in hours.

| | Signature |
|---|---|
| **Rust** | `utc_lst_correction(longitude: f64, eot: f64) -> f64` |
| **Python** | `utc_lst_correction(longitude: float, eot: float) -> float` |
| **Clojure** | `(utc-lst-correction longitude eot)` |

### `hour_angle`

Calculate the solar hour angle from local solar time.

Formula: `h = 15° × (LST - 12)`.

**Parameters**: `local_solar_time` — in decimal hours.
**Returns**: hour angle in degrees.

| | Signature |
|---|---|
| **Rust** | `hour_angle(local_solar_time: f64) -> f64` |
| **Python** | `hour_angle(local_solar_time: float) -> float` |
| **Clojure** | `(hour-angle local-solar-time)` |

### `solar_declination`

Calculate the solar declination angle (angle between the sun and Earth's equatorial plane).

Ranges from -23.45° (winter solstice) to +23.45° (summer solstice).

**Parameters**: `n` — day of year (1–365).
**Returns**: declination in degrees.

| | Signature |
|---|---|
| **Rust** | `solar_declination(n: i32) -> f64` |
| **Python** | `solar_declination(n: int) -> float` |
| **Clojure** | `(solar-declination n)` |

### `solar_zenith_angle`

Calculate the solar zenith angle (angle between the sun and vertical).

**Parameters**:
- `latitude` — observer's latitude in degrees.
- `declination` — solar declination in degrees.
- `hour_angle` — hour angle in degrees.

**Returns**: zenith angle in degrees.

| | Signature |
|---|---|
| **Rust** | `solar_zenith_angle(latitude: f64, declination: f64, hour_angle: f64) -> f64` |
| **Python** | `solar_zenith_angle(latitude: float, declination: float, hour_angle: float) -> float` |
| **Clojure** | `(solar-zenith-angle latitude declination hour-angle)` |

### `solar_altitude`

Calculate solar altitude (elevation) angle — the complement of the zenith angle.

Formula: `altitude = 90° - zenith`.

| | Signature |
|---|---|
| **Rust** | `solar_altitude(zenith_angle: f64) -> f64` |
| **Python** | `solar_altitude(zenith_angle: float) -> float` |
| **Clojure** | `(solar-altitude zenith-angle)` |

### `solar_azimuth`

Calculate the solar azimuth angle using atan2 for proper quadrant handling.

**Parameters**:
- `latitude` — observer's latitude in degrees.
- `declination` — solar declination in degrees.
- `hour_angle` — hour angle in degrees.

**Returns**: azimuth in degrees (0° = North, 90° = East, 180° = South, 270° = West).

| | Signature |
|---|---|
| **Rust** | `solar_azimuth(latitude: f64, declination: f64, hour_angle: f64) -> f64` |
| **Python** | `solar_azimuth(latitude: float, declination: float, hour_angle: float) -> float` |
| **Clojure** | `(solar-azimuth latitude declination hour-angle)` |

### `solar_angles_at`

Compute solar angles from precomputed day-constants and UTC time. A lower-level entry point used when the declination and UTC-LST correction are already known (e.g., table generation loops).

**Parameters**:
- `latitude` — observer's latitude in degrees.
- `decl` — solar declination in degrees.
- `correction` — UTC-to-LST correction in hours (from `utc_lst_correction`).
- `utc_hours` — UTC time in decimal hours.

**Returns**: `(local_solar_time, hour_angle, zenith, altitude, azimuth)`.

| | Signature |
|---|---|
| **Rust** | `solar_angles_at(latitude: f64, decl: f64, correction: f64, utc_hours: f64) -> (f64, f64, f64, f64, f64)` |
| **Python** | `solar_angles_at(latitude, decl, correction, utc_hours) -> tuple[float, float, float, float, float]` |
| **Clojure** | `(solar-angles-at latitude decl correction utc-hours)` → map with `:local-solar-time`, `:hour-angle`, `:zenith`, `:altitude`, `:azimuth` |

Note: Rust and Python return a 5-tuple; Clojure returns a keyword map.

### `solar_position`

Calculate complete solar position for a given location and timezone-aware datetime. This is the primary entry point for computing solar angles.

The datetime is converted to UTC internally. Day-of-year, equation of time, declination, and all derived angles are computed from the UTC representation.

**Parameters**:
- `latitude` — observer's latitude in degrees (positive = North).
- `longitude` — observer's longitude in degrees (negative = West).
- `dt` / `datetime` — a timezone-aware datetime.

**Returns**: `SolarPosition` (see [Types](#solarposition)).

| | Signature |
|---|---|
| **Rust** | `solar_position<Tz: TimeZone>(latitude: f64, longitude: f64, dt: &DateTime<Tz>) -> SolarPosition` |
| **Python** | `solar_position(latitude: float, longitude: float, dt: datetime) -> SolarPosition` |
| **Clojure** | `(solar-position latitude longitude datetime)` — `datetime` is a `java.time.ZonedDateTime` |

- **Python**: raises `ValueError` if `dt` is naive (no timezone).
- **Rust**: uses `chrono::DateTime<Tz>` — generic over any `chrono::TimeZone`.

### `single_axis_tilt`

Calculate optimal rotation angle for a single-axis (north-south oriented) horizontal tracker.

**Parameters**:
- `pos` — a `SolarPosition` result.
- `latitude` — observer's latitude in degrees.

**Returns**: rotation angle in degrees (positive = tilted toward west).

| | Signature |
|---|---|
| **Rust** | `single_axis_tilt(pos: &SolarPosition, latitude: f64) -> f64` |
| **Python** | `single_axis_tilt(pos: SolarPosition, latitude: float) -> float` |
| **Clojure** | `(single-axis-tilt pos latitude)` — `pos` is a keyword map (or anything with `:hour-angle`) |

### `dual_axis_angles`

Calculate optimal angles for a dual-axis tracker. Points the panel directly at the sun.

**Parameters**: `pos` — a `SolarPosition` result.
**Returns**: `DualAxisAngles` with `tilt` = zenith and `panel_azimuth` = azimuth + 180° (normalized).

| | Signature |
|---|---|
| **Rust** | `dual_axis_angles(pos: &SolarPosition) -> DualAxisAngles` |
| **Python** | `dual_axis_angles(pos: SolarPosition) -> DualAxisAngles` |
| **Clojure** | `(dual-axis-angles pos)` → map with `:tilt`, `:panel-azimuth` |

### `optimal_fixed_tilt`

Calculate the optimal annual fixed tilt angle for a given latitude using the empirical formula:

`tilt = 0.76 × |latitude| + 3.1°`

| | Signature |
|---|---|
| **Rust** | `optimal_fixed_tilt(latitude: f64) -> f64` |
| **Python** | `optimal_fixed_tilt(latitude: float) -> float` |
| **Clojure** | `(optimal-fixed-tilt latitude)` |

### `seasonal_tilt_adjustment`

Calculate recommended tilt angle for a fixed installation based on season.

| Season | Formula |
|---|---|
| Summer | `|latitude| - 15°` |
| Winter | `|latitude| + 15°` |
| Spring / Fall | `|latitude|` |

| | Signature |
|---|---|
| **Rust** | `seasonal_tilt_adjustment(latitude: f64, season: Season) -> f64` |
| **Python** | `seasonal_tilt_adjustment(latitude: float, season: Season) -> float` |
| **Clojure** | `(seasonal-tilt-adjustment latitude season)` — season is a keyword |

---

## Lookup Table Functions (`lookup_table` module)

### `minutes_to_time`

Convert minutes since midnight to an (hour, minute) pair.

| | Signature |
|---|---|
| **Rust** | `minutes_to_time(total_minutes: i32) -> (i32, i32)` |
| **Python** | `minutes_to_time(total_minutes: int) -> tuple[int, int]` |
| **Clojure** | `(minutes->time total-minutes)` → `[hour minute]` |

### `time_to_minutes`

Convert an (hour, minute) pair to minutes since midnight.

| | Signature |
|---|---|
| **Rust** | `time_to_minutes(time: (i32, i32)) -> i32` |
| **Python** | `time_to_minutes(t: tuple[int, int]) -> int` |
| **Clojure** | `(time->minutes [hour minute])` |

### `intervals_per_day`

Calculate the number of intervals in a day (1440 / interval_minutes).

| | Signature |
|---|---|
| **Rust** | `intervals_per_day(interval_minutes: i32) -> i32` |
| **Python** | `intervals_per_day(interval_minutes: int) -> int` |
| **Clojure** | `(intervals-per-day interval-minutes)` |

### `doy_to_month_day`

Convert a day-of-year ordinal to (month, day) for a given year.

| | Signature |
|---|---|
| **Rust** | `doy_to_month_day(year: i32, doy: i32) -> (u32, u32)` |
| **Python** | `doy_to_month_day(year: int, doy: int) -> tuple[int, int]` |
| **Clojure** | `(doy->month-day year doy)` → `[month day]` |

Note: Rust uses `chrono::NaiveDate::from_yo_opt` internally; Python and Clojure walk the `days_in_months` vector.

### `estimate_sunrise_sunset`

Estimate sunrise and sunset times for a given latitude and day of year using the hour-angle formula: `cos(h) = -tan(lat) × tan(decl)`.

**Parameters**:
- `latitude` — observer's latitude in degrees.
- `day_of_year` — ordinal day (1–366).

**Returns**: `SunriseSunset` with sunrise/sunset as minutes from midnight in local solar time.

Special cases:
- **Polar night** (cos_h >= 1): returns sunrise = sunset = 720 (noon).
- **Polar day** (cos_h <= -1): returns sunrise = 0, sunset = 1440.

| | Signature |
|---|---|
| **Rust** | `estimate_sunrise_sunset(latitude: f64, day_of_year: i32) -> SunriseSunset` |
| **Python** | `estimate_sunrise_sunset(latitude: float, day_of_year: int) -> SunriseSunset` |
| **Clojure** | `(estimate-sunrise-sunset latitude day-of-year)` → map with `:sunrise`, `:sunset` |

### `interpolate_angle`

Interpolate between two angles, handling 360° wraparound correctly. Returns nil/None if either input is nil/None.

**Parameters**:
- `a1`, `a2` — angles in degrees (or nil/None).
- `fraction` — interpolation fraction (0.0 to 1.0).

**Returns**: interpolated angle in degrees (normalized to [0, 360)), or nil/None.

| | Signature |
|---|---|
| **Rust** | `interpolate_angle(a1: Option<f64>, a2: Option<f64>, fraction: f64) -> Option<f64>` |
| **Python** | `interpolate_angle(a1: float \| None, a2: float \| None, fraction: float) -> float \| None` |
| **Clojure** | `(interpolate-angle a1 a2 fraction)` |

### `generate_single_axis_table`

Generate a precomputed single-axis tracker lookup table for an entire year.

Entries contain UTC minutes and rotation angle (nil/None during nighttime). Tables are indexed by UTC minutes with configurable interval spacing.

**Parameters**: `config` — a `LookupTableConfig`.
**Returns**: `LookupTable` (Rust: `SingleAxisTable`) containing `SingleAxisEntry` entries.

| | Signature |
|---|---|
| **Rust** | `generate_single_axis_table(config: &LookupTableConfig) -> SingleAxisTable` |
| **Python** | `generate_single_axis_table(config: LookupTableConfig) -> LookupTable` |
| **Clojure** | `(generate-single-axis-table config)` |

### `generate_dual_axis_table`

Generate a precomputed dual-axis tracker lookup table for an entire year.

Entries contain UTC minutes, tilt, and panel azimuth (nil/None during nighttime).

**Parameters**: `config` — a `LookupTableConfig`.
**Returns**: `LookupTable` (Rust: `DualAxisTable`) containing `DualAxisEntry` entries.

| | Signature |
|---|---|
| **Rust** | `generate_dual_axis_table(config: &LookupTableConfig) -> DualAxisTable` |
| **Python** | `generate_dual_axis_table(config: LookupTableConfig) -> LookupTable` |
| **Clojure** | `(generate-dual-axis-table config)` |

### `lookup_single_axis`

Look up a single-axis rotation angle from a precomputed table with linear interpolation between entries.

**Parameters**:
- `table` — a single-axis `LookupTable`.
- `day_of_year` — ordinal day (1–366).
- `minutes` — UTC minutes since midnight.

**Returns**: a `SingleAxisEntry` with the interpolated rotation, or nil/None if the time is outside the table's range for that day.

| | Signature |
|---|---|
| **Rust** | `lookup_single_axis(table: &SingleAxisTable, day_of_year: i32, minutes: i32) -> Option<SingleAxisEntry>` |
| **Python** | `lookup_single_axis(table: LookupTable, day_of_year: int, minutes: int) -> SingleAxisEntry \| None` |
| **Clojure** | `(lookup-single-axis table day-of-year minutes)` |

### `lookup_dual_axis`

Look up dual-axis angles from a precomputed table with interpolation. Uses linear interpolation for tilt and circular interpolation (via `interpolate_angle`) for panel azimuth to handle 360° wraparound.

**Parameters**:
- `table` — a dual-axis `LookupTable`.
- `day_of_year` — ordinal day (1–366).
- `minutes` — UTC minutes since midnight.

**Returns**: a `DualAxisEntry` with interpolated tilt and panel azimuth, or nil/None if outside range.

| | Signature |
|---|---|
| **Rust** | `lookup_dual_axis(table: &DualAxisTable, day_of_year: i32, minutes: i32) -> Option<DualAxisEntry>` |
| **Python** | `lookup_dual_axis(table: LookupTable, day_of_year: int, minutes: int) -> DualAxisEntry \| None` |
| **Clojure** | `(lookup-dual-axis table day-of-year minutes)` |

### `table_to_compact` / `single_axis_table_to_compact` / `dual_axis_table_to_compact`

Strip metadata and return nested lists of raw angle values for compact storage or export.

**Single-axis output**: `[[rotation, ...], ...]` — one sub-list per day, one value per entry.

**Dual-axis output**: `[[(tilt, panel_azimuth), ...], ...]` — one sub-list per day, one pair per entry.

| | Signature |
|---|---|
| **Rust** | `single_axis_table_to_compact(table: &SingleAxisTable) -> Vec<Vec<Option<f64>>>` |
| **Rust** | `dual_axis_table_to_compact(table: &DualAxisTable) -> Vec<Vec<(Option<f64>, Option<f64>)>>` |
| **Python** | `table_to_compact(table: LookupTable) -> list` |
| **Clojure** | `(table->compact table)` |

Note: Rust has separate functions for each table type; Python and Clojure use a single polymorphic function that inspects the entry type at runtime.

---

## Cross-Implementation Differences

| Aspect | Rust | Python | Clojure |
|---|---|---|---|
| Datetime input | `chrono::DateTime<Tz>` (generic) | `datetime.datetime` (tz-aware) | `java.time.ZonedDateTime` |
| Return types | Structs | Frozen dataclasses | Keyword maps |
| `solar_angles_at` return | 5-tuple `(f64, f64, f64, f64, f64)` | 5-tuple | keyword map |
| Compact export | Two functions (`single_axis_table_to_compact`, `dual_axis_table_to_compact`) | One function (`table_to_compact`) | One function (`table->compact`) |
| `Season` type | Enum with `PascalCase` variants | `StrEnum` with lowercase string values | Keywords (`:summer`, etc.) |
| `DEFAULT_CONFIG` | `LookupTableConfig::default()` (trait) | `DEFAULT_CONFIG` (module-level constant) | `default-config` (var) |
| Nullable angles | `Option<f64>` | `float \| None` | `nil` |
| External dependencies | `chrono` | None (stdlib only) | None (uses `java.time`) |

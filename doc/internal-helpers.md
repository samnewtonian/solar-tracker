# Internal Helpers

Per-language catalog of private/internal functions and types that are **not** part of the public API. These exist for performance optimization, code deduplication, or implementation convenience within the lookup table generation and lookup paths.

These are implementation details and may change without notice between versions.

---

## Rust

All internal items are in `rust/src/lookup_table.rs`.

### `FastAngles` (struct)

```rust
struct FastAngles {
    hour_angle: f64,
    zenith: f64,
    azimuth: f64,
}
```

Lightweight struct holding only the solar angles needed during table generation. Avoids allocating a full `SolarPosition` (which also carries `day_of_year`, `declination`, `equation_of_time`, `local_solar_time`, and `altitude`).

### `compute_angles_fast`

```rust
fn compute_angles_fast(
    sin_lat: f64, cos_lat: f64,
    sin_dec: f64, cos_dec: f64,
    correction: f64, utc_hours: f64,
) -> FastAngles
```

Compute solar angles using precomputed sin/cos values for latitude and declination. Mirrors the formulas in `solar_zenith_angle` and `solar_azimuth` but avoids redundant `deg_to_rad` and trig calls on values that are constant across an entire day (latitude trig) or across all intervals within a day (declination trig). Returns a `FastAngles` instead of a full `SolarPosition`.

**Why it exists**: Hot path optimization. Called once per interval per day during table generation (~100k+ times for a year at 5-minute intervals).

### `generate_table`

```rust
fn generate_table<E, F>(
    config: &LookupTableConfig,
    entry_fn: F,
    bytes_per_entry: usize,
) -> LookupTable<E>
where
    F: Fn(i32, &FastAngles, bool) -> E,
```

Shared table generation loop parameterized by an entry constructor function. Iterates days 1–365/366 and UTC-minute intervals within the daylight window (plus buffers). For each interval, calls `compute_angles_fast` and passes the result to `entry_fn` along with the UTC minutes and a daylight flag.

Precomputes sin/cos of latitude once and sin/cos of declination once per day to minimize trig overhead.

**Why it exists**: Code deduplication — `generate_single_axis_table` and `generate_dual_axis_table` differ only in their entry constructor.

### `interpolate_linear`

```rust
fn interpolate_linear(v1: Option<f64>, v2: Option<f64>, fraction: f64) -> Option<f64>
```

Simple linear interpolation between two optional values. Returns `None` if either input is `None`.

**Why it exists**: Used by `lookup_single_axis` (for rotation) and `lookup_dual_axis` (for tilt). Kept separate from the public `interpolate_angle` which handles circular wraparound.

### `HasMinutes` (trait)

```rust
trait HasMinutes {
    fn minutes(&self) -> i32;
}
```

Implemented for both `SingleAxisEntry` and `DualAxisEntry`. Provides a uniform interface for `find_bracketing_entries` to access the `minutes` field without knowing the concrete entry type.

**Why it exists**: Enables `find_bracketing_entries` to be generic over entry types, avoiding code duplication between single-axis and dual-axis lookup paths.

### `find_bracketing_entries`

```rust
fn find_bracketing_entries<E: HasMinutes>(
    entries: &[E],
    interval_minutes: i32,
    minutes: i32,
) -> Option<(&E, Option<&E>, f64)>
```

Find the two entries bracketing a given UTC minutes value for interpolation. Uses O(1) index computation from the regular interval spacing rather than binary search. Returns `(entry_before, entry_after, fraction)` or `None` if the time is outside the entry range.

**Why it exists**: Shared lookup logic between `lookup_single_axis` and `lookup_dual_axis`.

### `format_utc_now`

```rust
fn format_utc_now() -> String
```

Format the current UTC time as an ISO 8601 string for `TableMetadata.generated_at`. Isolated as a helper for testability.

---

## Python

All internal items are in `python/solar_tracker/lookup_table.py`, prefixed with `_` per Python convention.

### `_compute_angles_fast`

```python
def _compute_angles_fast(sin_lat, cos_lat, sin_dec, cos_dec, correction, utc_hours)
```

Compute solar angles using precomputed sin/cos values. Returns a `SimpleNamespace` with attributes `local_solar_time`, `hour_angle`, `zenith`, `altitude`, `azimuth`.

Same optimization rationale as the Rust `compute_angles_fast`: avoids redundant trig on constant-per-day values in the table generation inner loop.

### `_generate_table`

```python
def _generate_table(
    config: LookupTableConfig,
    entry_fn: Callable,
    bytes_per_entry: int,
) -> LookupTable
```

Shared table generation loop. Same structure as the Rust version: iterates days and intervals, calls `_compute_angles_fast`, delegates entry construction to `entry_fn`.

### `_interpolate_linear`

```python
def _interpolate_linear(v1: float | None, v2: float | None, fraction: float) -> float | None
```

Simple linear interpolation. Returns `None` if either input is `None`.

### `_find_bracketing_entries`

```python
def _find_bracketing_entries(
    entries: list, interval_minutes: int, minutes: int
) -> tuple | None
```

Find bracketing entries for interpolation using O(1) index computation. Returns `(entry_before, entry_after, fraction)` or `None`.

---

## Clojure

Internal items are in `clojure/src/com/kardashevtypev/solar/lookup_table.clj` (marked `defn-`, i.e., namespace-private) and `angles.clj`.

### `compute-angles-fast` (private)

```clojure
(defn- compute-angles-fast [sin-lat cos-lat sin-dec cos-dec correction utc-hours])
```

Compute solar angles with precomputed trig values. Returns a keyword map `{:local-solar-time :hour-angle :zenith :altitude :azimuth}`.

### `generate-table` (private)

```clojure
(defn- generate-table [config entry-fn bytes-per-entry])
```

Shared table generation. `entry-fn` is `(fn [minutes angles is-daylight?] ...)` where `angles` is the map from `compute-angles-fast`.

### `interpolate-linear` (private)

```clojure
(defn- interpolate-linear [v1 v2 fraction])
```

Simple linear interpolation. Returns `nil` if either input is `nil`.

### `find-bracketing-entries` (private)

```clojure
(defn- find-bracketing-entries [entries interval-minutes minutes])
```

Find bracketing entries using O(1) index lookup. Returns `[entry-before entry-after fraction]` or `nil`.

### `deg->rad-factor` and `rad->deg-factor` (angles.clj)

```clojure
(def ^:const deg->rad-factor (/ math/PI 180.0))
(def ^:const rad->deg-factor (/ 180.0 math/PI))
```

Precomputed conversion factors in `angles.clj`. These are technically public (Clojure `def` is public by default) but are implementation details — callers should use `deg->rad` and `rad->deg` instead. They exist to avoid recomputing the division on every conversion call.

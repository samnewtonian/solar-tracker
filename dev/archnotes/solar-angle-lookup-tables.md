# Precomputed Solar Angle Lookup Tables

Strategies for generating and optimizing lookup tables for solar panel tracking systems.

---

## Year-over-Year Variation

**Short answer:** For practical tracking purposes, solar angles are effectively identical year to year. A single lookup table indexed by day-of-year and time-of-day works indefinitely.

### Why Angles Are Stable

The solar position formulas depend on:

1. **Earth's axial tilt (obliquity):** Currently ~23.44°, decreasing by ~0.47" per year. This amounts to ~0.013° per century—imperceptible for tracking.

2. **Earth's orbital eccentricity:** Changes over ~100,000 year cycles. Negligible on human timescales.

3. **Precession of equinoxes:** ~26,000 year cycle. Shifts which stars appear where, but doesn't affect sun-earth geometry for tracking purposes.

4. **Calendar alignment:** The only practical consideration. The 365.25-day tropical year means dates shift by ~6 hours annually, corrected by leap years.

### Leap Year Handling

The day-of-year calculation shifts by one day after February 29 in leap years. The implementation derives leap year status from the configured year and generates 365 or 366 days accordingly — no separate config parameter needed.

For embedded systems with tight memory, a fixed 365-day table with ±1 day error (~0.4° declination near equinoxes) is acceptable. The Clojure/Python/Rust implementations generate the correct number of days automatically.

---

## Interval Sizing Considerations

### Physical Constraints

Motor and actuator systems have inherent limitations:

| Factor | Typical Value | Implication |
|--------|---------------|-------------|
| Motor step resolution | 0.1° - 1.0° | Finer table resolution is wasted |
| Actuator response time | 1-10 seconds | Sub-second intervals unnecessary |
| Mechanical backlash | 0.5° - 2.0° | Absorbs small calculated changes |
| Maximum slew rate | 1° - 5° per second | Limits catch-up speed |

### Angular Rate of Change

The sun moves at different apparent speeds throughout the day:

**Hour angle:** Fixed at 15°/hour (360° / 24 hours)

**Zenith angle rate:** Varies by time of day and season
- **Near sunrise/sunset:** Changes rapidly (up to ~1°/minute near horizon)
- **Near solar noon:** Changes slowly (~0.1°/minute)
- **Seasonal effect:** Faster changes near solstices at high latitudes

**Azimuth rate:** Highly variable
- **Near solar noon:** Can exceed 1°/minute (sun crosses meridian)
- **Morning/evening:** Slower, more gradual change

### Recommended Intervals

| Application | Interval | Points/Day | Rationale |
|-------------|----------|------------|-----------|
| Residential single-axis | 15 minutes | 96 | Matches typical inverter logging |
| Commercial single-axis | 5 minutes | 288 | Good balance of precision and size |
| Dual-axis (standard) | 5 minutes | 288 | Adequate for most motors |
| Dual-axis (precision) | 1 minute | 1440 | Research/CPV applications |
| Agricultural/greenhouse | 30 minutes | 48 | Lower precision acceptable |

---

## Table Structure Design

### Time Representation

**All times in the lookup table use UTC.** This avoids DST complications entirely—the conversion from local clock time to UTC happens once at the system boundary (typically when reading the real-time clock), and all internal lookups use consistent UTC indices.

For a tracker in Central Illinois:
- Local noon ≈ 18:00 UTC (1080 minutes)
- Local 6 AM ≈ 12:00 UTC (720 minutes)
- Local 6 PM ≈ 00:00 UTC next day (1440 minutes)

### Single-Axis Tracker

Stores one value per interval: tracker rotation angle.

```
Structure: rotation_angle[day][interval]
Days: 365 (or 366)
Intervals: n per day
Total entries: 365 × n
Bytes (float32): 365 × n × 4
```

**Example sizes (single-axis):**
| Interval | Points/Day | Total Entries | Size (float32) |
|----------|------------|---------------|----------------|
| 15 min | 96 | 35,040 | 137 KB |
| 5 min | 288 | 105,120 | 411 KB |
| 1 min | 1440 | 525,600 | 2.0 MB |

### Dual-Axis Tracker

Stores two values per interval: tilt angle and azimuth angle.

```
Structure: angles[day][interval] = {tilt, azimuth}
Total entries: 365 × n × 2
Bytes (float32): 365 × n × 8
```

**Example sizes (dual-axis):**
| Interval | Points/Day | Total Entries | Size (float32) |
|----------|------------|---------------|----------------|
| 15 min | 96 | 70,080 | 274 KB |
| 5 min | 288 | 210,240 | 822 KB |
| 1 min | 1440 | 1,051,200 | 4.0 MB |

### Compact Representations

**Fixed-point encoding:** Angles range 0-360° or -90° to +90°. Use scaled integers:

```
int16 encoding: angle × 100 → 0.01° resolution, 2 bytes
int8 encoding:  angle × 0.5 → 0.5° resolution, 1 byte (if acceptable)
```

**Size reduction with int16:**
| Config | Interval | float32 | int16 | Savings |
|--------|----------|---------|-------|---------|
| Single-axis | 5 min | 411 KB | 205 KB | 50% |
| Dual-axis | 5 min | 822 KB | 411 KB | 50% |

---

## Optimizations

### 1. Daytime-Only Storage

Solar tracking is only meaningful during daylight. Storing only sunrise-to-sunset intervals saves significant space.

**Approach:**
- Store sunrise/sunset times (or interval indices) per day
- Only populate entries within that window
- Use sparse or variable-length day records

**Savings:** 40-60% depending on latitude and season

**Trade-off:** More complex indexing logic

### 2. Symmetry Exploitation

Solar geometry has symmetries that can reduce storage:

**Daily symmetry:** Morning and afternoon angles are symmetric around solar noon.
- Store only morning values (sunrise to noon)
- Mirror for afternoon: `afternoon_angle = -morning_angle` (for hour-angle-based quantities)
- **Savings:** ~50% of daytime entries

**Annual symmetry:** Days equidistant from solstices have similar declinations.
- Day 172 (summer solstice) mirrors day 355 (days from winter solstice)
- Store 183 days, mirror for the other half
- **Caveat:** Equation of Time breaks perfect symmetry; small errors introduced
- **Savings:** ~50% of days

**Combined symmetry:** Store ~25% of full table with some accuracy loss.

### 3. Delta Encoding

Store differences between consecutive values instead of absolute angles:

```
entry[0] = absolute_angle
entry[n] = entry[n-1] + delta[n]
```

**Benefits:**
- Deltas are small (typically < 2° per 5-minute interval)
- Can use smaller integer types (int8 for deltas)
- Better compression ratio

**Drawbacks:**
- Requires sequential access or periodic keyframes
- Accumulates floating-point error (use periodic resets)

### 4. Polynomial Approximation

Instead of storing every interval, store polynomial coefficients per day:

```
angle(t) = a₀ + a₁t + a₂t² + a₃t³
```

Where `t` is time within the day (0 to 24 hours).

**Storage:** 4 coefficients × 365 days × 4 bytes = ~5.7 KB (single-axis)

**Accuracy:** 3rd-order polynomial typically achieves < 0.5° error for zenith angle.

**Trade-off:** Runtime polynomial evaluation vs. direct lookup.

### 5. Spherical Harmonic Compression

For maximum compression, represent the entire annual cycle as spherical harmonics or Fourier series. Reconstruction requires more computation but storage drops to a few hundred bytes.

---

## Interpolation Strategies

Since solar position changes smoothly and continuously, linear interpolation between table entries works well.

### Linear Interpolation

```
Given: table entries at times t₀ and t₁
Query time: t where t₀ ≤ t < t₁
Fraction: f = (t - t₀) / (t₁ - t₀)
Result: angle = table[t₀] × (1 - f) + table[t₁] × f
```

**Error analysis:** For 5-minute intervals, linear interpolation error is typically < 0.05°.

### Azimuth Wraparound

Azimuth angles wrap at 360°. Handle interpolation carefully:

```clojure
(defn interpolate-angle
  "Interpolate between two angles, handling 360° wraparound."
  [a1 a2 fraction]
  (let [diff (- a2 a1)
        ;; Choose shorter arc
        adjusted-diff (cond
                        (> diff 180)  (- diff 360)
                        (< diff -180) (+ diff 360)
                        :else diff)]
    (mod (+ a1 (* adjusted-diff fraction)) 360.0)))
```

### Cubic Interpolation

For smoother motor control, cubic (Hermite) interpolation reduces velocity discontinuities:

```
angle(f) = (2f³ - 3f² + 1)p₀ + (f³ - 2f² + f)m₀ + (-2f³ + 3f²)p₁ + (f³ - f²)m₁
```

Where `p₀`, `p₁` are endpoint values and `m₀`, `m₁` are tangent slopes (computed from adjacent points).

---

## Nighttime Behavior

### Stow Position

Most trackers return to a "stow" position overnight:

- **Single-axis:** Horizontal (0° rotation) or slight east-facing tilt
- **Dual-axis:** Flat or manufacturer-specified position
- **Wind stow:** Some systems have separate high-wind positions

### Wake Strategy

Options for morning startup:

1. **Pre-position:** Move to expected sunrise angle before dawn
2. **Reactive:** Wait for sufficient light, then acquire sun position
3. **Hybrid:** Move to approximate position, fine-tune with sensor feedback

### Table Encoding

For nighttime intervals, options include:

- **Sentinel value:** Use NaN or -999 to indicate "stow"
- **Omit entirely:** Only store daytime intervals (requires sunrise/sunset index)
- **Stow angle:** Store actual stow position for consistency

---

## Clojure Implementation

```clojure
(ns com.kardashevtypev.solar.lookup-table
  "Precomputed solar angle lookup table generation and access.
   Generates daylight-only tables for single-axis and dual-axis trackers
   with configurable interval and sunrise/sunset buffers."
  (:require [com.kardashevtypev.solar.angles :as angles]
            [clojure.math :as math]))

;;; ============================================================
;;; Configuration
;;; ============================================================

(def default-config
  {:interval-minutes 5
   :latitude 39.8
   :longitude -89.6
   :year 2026
   :sunrise-buffer-minutes 30
   :sunset-buffer-minutes 30})

;;; ============================================================
;;; Time and Date Utilities
;;; ============================================================

(defn minutes->time [total-minutes]
  [(quot total-minutes 60) (mod total-minutes 60)])

(defn time->minutes [[hour minute]]
  (+ (* hour 60) minute))

(defn intervals-per-day [interval-minutes]
  (quot 1440 interval-minutes))

(defn doy->month-day
  "Convert day-of-year to [month day] for a given year."
  [year doy]
  (let [dim (angles/days-in-months year)]
    (loop [month 1, remaining doy]
      (let [d (nth dim (dec month))]
        (if (<= remaining d)
          [month remaining]
          (recur (inc month) (- remaining d)))))))

;;; ============================================================
;;; Sunrise/Sunset Estimation
;;; ============================================================

(defn estimate-sunrise-sunset
  "Estimate sunrise and sunset in local solar time minutes.
   Returns {:sunrise minutes, :sunset minutes}.
   Uses: cos(h) = -tan(φ) × tan(δ)"
  [latitude day-of-year]
  (let [lat-rad (angles/deg->rad latitude)
        decl (angles/solar-declination day-of-year)
        decl-rad (angles/deg->rad decl)
        cos-h (* -1.0 (math/tan lat-rad) (math/tan decl-rad))]
    (cond
      (>= cos-h 1.0)  {:sunrise 720 :sunset 720}    ; Polar night
      (<= cos-h -1.0) {:sunrise 0 :sunset 1440}      ; Polar day
      :else
      (let [h-deg (angles/rad->deg (math/acos cos-h))
            half-day-minutes (* (/ h-deg 15.0) 60.0)]
        {:sunrise (long (- 720 half-day-minutes))
         :sunset  (long (+ 720 half-day-minutes))}))))

;;; ============================================================
;;; Interpolation
;;; ============================================================

(defn interpolate-angle
  "Interpolate between two angles, handling 360° wraparound."
  [a1 a2 fraction]
  (when (and a1 a2)
    (let [diff (- a2 a1)
          adjusted-diff (cond
                          (> diff 180)  (- diff 360)
                          (< diff -180) (+ diff 360)
                          :else diff)]
      (mod (+ a1 (* adjusted-diff fraction)) 360.0))))

(defn- interpolate-linear [v1 v2 fraction]
  (when (and v1 v2)
    (+ v1 (* fraction (- v2 v1)))))

;;; ============================================================
;;; Table Generation
;;; ============================================================

(defn- compute-angles-fast
  "Compute solar angles with precomputed trig values for latitude and declination.
   Avoids redundant deg->rad and sin/cos calls on constant values.
   Returns {:local-solar-time :hour-angle :zenith :altitude :azimuth}."
  [sin-lat cos-lat sin-dec cos-dec correction utc-hours]
  (let [lst       (mod (+ utc-hours correction) 24.0)
        ha        (* angles/degrees-per-hour (- lst 12.0))
        ha-rad    (angles/deg->rad ha)
        cos-z     (+ (* sin-lat sin-dec)
                     (* cos-lat cos-dec (math/cos ha-rad)))
        zenith    (angles/rad->deg (math/acos (max -1.0 (min 1.0 cos-z))))
        sin-az    (* -1.0 cos-dec (math/sin ha-rad))
        cos-az    (- (* sin-dec cos-lat)
                     (* cos-dec sin-lat (math/cos ha-rad)))]
    {:local-solar-time lst
     :hour-angle ha
     :zenith zenith
     :altitude (- 90.0 zenith)
     :azimuth (angles/normalize-angle (angles/rad->deg (math/atan2 sin-az cos-az)))}))

(defn- generate-table
  "Shared table generation. Iterates days 1-365/366, computes solar angles
   at each UTC-minute interval within the daylight window (plus buffers).
   Precomputes sin/cos of latitude once and declination per day.

   entry-fn: (fn [minutes angles is-daylight?]) -> entry map
   bytes-per-entry: used for storage estimate"
  [config entry-fn bytes-per-entry]
  (let [{:keys [interval-minutes latitude longitude year
                sunrise-buffer-minutes sunset-buffer-minutes]} config
        n-intervals (intervals-per-day interval-minutes)
        lat-rad (angles/deg->rad latitude)
        sin-lat (math/sin lat-rad)
        cos-lat (math/cos lat-rad)
        n-days (if (angles/leap-year? year) 366 365)
        days (vec
              (for [doy (range 1 (inc n-days))]
                (let [decl       (angles/solar-declination doy)
                      eot        (angles/equation-of-time doy)
                      correction (angles/utc-lst-correction longitude eot)
                      correction-minutes (* correction 60.0)
                      dec-rad (angles/deg->rad decl)
                      sin-dec (math/sin dec-rad)
                      cos-dec (math/cos dec-rad)
                      {:keys [sunrise sunset]} (estimate-sunrise-sunset latitude doy)
                      ;; Shift local solar sunrise/sunset to UTC minutes
                      sunrise-utc (long (- sunrise correction-minutes))
                      sunset-utc  (long (- sunset correction-minutes))
                      start-minute (max 0 (- sunrise-utc sunrise-buffer-minutes))
                      end-minute   (min 1439 (+ sunset-utc sunset-buffer-minutes))
                      entries (vec
                               (for [interval (range n-intervals)
                                     :let [minutes (* interval interval-minutes)]
                                     :when (and (>= minutes start-minute)
                                                (<= minutes end-minute))]
                                 (let [utc-hours     (/ minutes 60.0)
                                       ang           (compute-angles-fast sin-lat cos-lat
                                                                         sin-dec cos-dec
                                                                         correction utc-hours)
                                       local-minutes (long (+ minutes correction-minutes))
                                       is-daylight?  (and (>= local-minutes sunrise)
                                                          (<= local-minutes sunset))]
                                   (entry-fn minutes ang is-daylight?))))]
                  {:day-of-year doy
                   :sunrise-minutes sunrise
                   :sunset-minutes sunset
                   :entries entries})))
        total-entries (reduce + (map (comp count :entries) days))
        storage-kb (/ (* total-entries bytes-per-entry) 1024.0)]
    {:config config
     :days days
     :metadata {:generated-at (str (java.time.Instant/now))
                :total-entries total-entries
                :storage-estimate-kb storage-kb}}))

(defn generate-single-axis-table [config]
  (let [latitude (:latitude config)]
    (generate-table config
                    (fn [minutes pos is-daylight?]
                      {:minutes minutes
                       :rotation (when is-daylight?
                                   (angles/single-axis-tilt pos latitude))})
                    4)))

(defn generate-dual-axis-table [config]
  (generate-table config
                  (fn [minutes pos is-daylight?]
                    (if is-daylight?
                      (let [da (angles/dual-axis-angles pos)]
                        {:minutes minutes
                         :tilt (:tilt da)
                         :panel-azimuth (:panel-azimuth da)})
                      {:minutes minutes :tilt nil :panel-azimuth nil}))
                  8))

;;; ============================================================
;;; Lookup Functions
;;; ============================================================

(defn- find-bracketing-entries
  "Find the two entries bracketing the given minutes value.
   Uses O(1) index computation from regular interval spacing.
   Returns [entry-before entry-after fraction] or nil if outside range."
  [entries interval-minutes minutes]
  (when (seq entries)
    (let [first-minutes (:minutes (first entries))
          last-minutes  (:minutes (peek entries))]
      (when (and (>= minutes first-minutes) (<= minutes last-minutes))
        (let [idx-before   (min (quot (- minutes first-minutes) interval-minutes)
                                (dec (count entries)))
              entry-before (get entries idx-before)
              entry-after  (get entries (inc idx-before))
              t0           (:minutes entry-before)]
          (if (or (nil? entry-after) (= minutes t0))
            [entry-before nil 0.0]
            (let [t1       (:minutes entry-after)
                  fraction (/ (double (- minutes t0)) (- t1 t0))]
              [entry-before entry-after fraction])))))))

(defn lookup-single-axis
  "Look up single-axis rotation with linear interpolation.
   Returns {:minutes m :rotation angle} or nil if outside range."
  [table day-of-year minutes]
  (let [entries (:entries (get (:days table) (dec day-of-year)))
        interval-minutes (get-in table [:config :interval-minutes])]
    (when-let [[before after fraction] (find-bracketing-entries entries interval-minutes minutes)]
      (if (nil? after)
        {:minutes minutes :rotation (:rotation before)}
        {:minutes minutes
         :rotation (interpolate-linear (:rotation before) (:rotation after) fraction)}))))

(defn lookup-dual-axis
  "Look up dual-axis angles with linear interpolation.
   Uses interpolate-angle for panel-azimuth to handle 360° wraparound."
  [table day-of-year minutes]
  (let [entries (:entries (get (:days table) (dec day-of-year)))
        interval-minutes (get-in table [:config :interval-minutes])]
    (when-let [[before after fraction] (find-bracketing-entries entries interval-minutes minutes)]
      (if (nil? after)
        {:minutes minutes :tilt (:tilt before) :panel-azimuth (:panel-azimuth before)}
        {:minutes minutes
         :tilt (interpolate-linear (:tilt before) (:tilt after) fraction)
         :panel-azimuth (interpolate-angle (:panel-azimuth before)
                                           (:panel-azimuth after)
                                           fraction)}))))

;;; ============================================================
;;; Compact Export
;;; ============================================================

(defn table->compact
  "Strip metadata and return nested vectors of angle values.
   Single-axis: [[rotation ...] ...]
   Dual-axis:   [[[tilt panel-azimuth] ...] ...]"
  [table]
  (let [sample-entry (first (:entries (first (:days table))))]
    (if (contains? sample-entry :rotation)
      (mapv (fn [day] (mapv :rotation (:entries day))) (:days table))
      (mapv (fn [day]
              (mapv (fn [e] [(:tilt e) (:panel-azimuth e)]) (:entries day)))
            (:days table)))))

(comment
  ;; Generate single-axis table
  (def sa-table (generate-single-axis-table default-config))
  (:metadata sa-table)

  ;; Look up spring equinox noon (UTC 1080 min ≈ local noon for Springfield)
  (lookup-single-axis sa-table 80 1080)

  ;; Generate dual-axis table at 15-min intervals
  (def da-table (generate-dual-axis-table (assoc default-config :interval-minutes 15)))
  (lookup-dual-axis da-table 172 1080)

  ;; Compact export
  (def compact (table->compact sa-table))
  (count compact)         ; 365 days
  (count (first compact)) ; entries per day
  )
```

---

## Storage Format Recommendations

### Embedded Systems (< 64 KB RAM)

Use polynomial approximation:
- Store 4 coefficients per day × 365 days
- Reconstruct angles at runtime
- Total: ~6 KB for single-axis

### Microcontrollers (64 KB - 1 MB)

Use int16 delta encoding with 15-minute intervals:
- Single-axis: ~35 KB
- Dual-axis: ~70 KB
- Include daytime hours only

### General Purpose (> 1 MB)

Use full float32 tables with 5-minute intervals:
- Single-axis: ~400 KB
- Dual-axis: ~800 KB
- Include sunrise/sunset buffers

### Cloud/Server

Store full 1-minute resolution for maximum precision:
- Single-axis: ~2 MB
- Dual-axis: ~4 MB
- Enable on-demand generation for any location

---

## Summary: Year-over-Year Reuse

| Factor | Annual Drift | 10-Year Drift | Impact |
|--------|--------------|---------------|--------|
| Axial tilt | ~0.0001° | ~0.001° | None |
| Orbital shape | Negligible | Negligible | None |
| Leap year | ±1 day shift | Corrected | Minor (< 0.5°) |
| Calendar drift | N/A | N/A | None with leap years |

**Conclusion:** A single precomputed table, indexed by day-of-year and time-of-day, is valid indefinitely. Leap year handling is the only practical consideration, and even ignoring it produces errors well within mechanical tolerance of tracking systems.

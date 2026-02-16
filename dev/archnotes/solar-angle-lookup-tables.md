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

The day-of-year calculation shifts by one day after February 29 in leap years. Two approaches:

**Option A: Ignore it**
- Maximum error: angles calculated for "wrong" day by ±1
- Declination error: ~0.4° maximum (near equinoxes when declination changes fastest)
- Practical impact: Negligible for most tracking systems

**Option B: Separate leap year table**
- Store 366-day table, use appropriate slice
- Or compute day-of-year correctly and interpolate

**Recommendation:** For embedded systems with tight memory, ignore leap year differences. For systems with storage headroom, maintain the 366-day table and skip day 60 (Feb 29) in non-leap years.

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
(ns solar.lookup-table
  "Precomputed solar angle lookup table generation and access."
  (:require [solar.angles :as angles]
            [clojure.math :as math]))

;;; ============================================================
;;; Configuration
;;; ============================================================

(def default-config
  {:interval-minutes 5
   :latitude 39.8
   :longitude -89.6
   :std-meridian -90.0
   :year 2026
   :include-night? false
   :sunrise-buffer-minutes 30   ; Start tracking before sunrise
   :sunset-buffer-minutes 30})  ; Continue tracking after sunset

;;; ============================================================
;;; Time Utilities
;;; ============================================================

(defn minutes->time
  "Convert minutes since midnight to [hour minute]."
  [total-minutes]
  [(quot total-minutes 60) (mod total-minutes 60)])

(defn time->minutes
  "Convert [hour minute] to minutes since midnight."
  [[hour minute]]
  (+ (* hour 60) minute))

(defn intervals-per-day
  "Calculate number of intervals in a day."
  [interval-minutes]
  (quot 1440 interval-minutes))

;;; ============================================================
;;; Sunrise/Sunset Estimation
;;; ============================================================

(defn estimate-sunrise-sunset
  "Estimate sunrise and sunset times for a given day.
   Returns {:sunrise minutes, :sunset minutes} from midnight.
   
   Uses the hour angle at sunrise/sunset formula:
   cos(h) = -tan(φ) × tan(δ)"
  [latitude day-of-year]
  (let [lat-rad (angles/deg->rad latitude)
        decl (angles/solar-declination day-of-year)
        decl-rad (angles/deg->rad decl)
        
        ;; Hour angle at sunrise/sunset
        cos-h (* -1.0 (math/tan lat-rad) (math/tan decl-rad))
        
        ;; Clamp for polar day/night conditions
        cos-h-clamped (max -1.0 (min 1.0 cos-h))]
    
    (if (or (>= cos-h-clamped 1.0)   ; Polar night
            (<= cos-h-clamped -1.0))  ; Polar day
      ;; Handle polar conditions
      (if (pos? decl)
        {:sunrise 0 :sunset 1440}     ; 24-hour daylight
        {:sunrise 720 :sunset 720})   ; No daylight
      
      ;; Normal sunrise/sunset
      (let [h-deg (angles/rad->deg (math/acos cos-h-clamped))
            half-day-minutes (* (/ h-deg 15.0) 60)
            solar-noon-minutes 720]  ; Approximate
        {:sunrise (- solar-noon-minutes half-day-minutes)
         :sunset (+ solar-noon-minutes half-day-minutes)}))))

;;; ============================================================
;;; Single Day Generation
;;; ============================================================

(defn generate-day-angles
  "Generate angle entries for a single day.
   
   Returns vector of maps, one per interval:
   {:minutes <minutes-since-midnight>
    :zenith <zenith-angle>
    :azimuth <azimuth-angle>
    :altitude <altitude-angle>
    :single-axis-rotation <rotation-for-single-axis>
    :dual-axis {:tilt <tilt> :panel-azimuth <panel-azimuth>}
    :is-daylight? <boolean>}"
  [{:keys [interval-minutes latitude longitude std-meridian year
           include-night? sunrise-buffer-minutes sunset-buffer-minutes]
    :as config}
   day-of-year]
  (let [n-intervals (intervals-per-day interval-minutes)
        {:keys [sunrise sunset]} (estimate-sunrise-sunset latitude day-of-year)
        
        ;; Determine which month/day this is (approximate, for API)
        ;; This is a simplification; production code would use proper date math
        month (inc (quot day-of-year 30))
        day-of-month (inc (mod day-of-year 30))
        
        start-minute (if include-night?
                       0
                       (max 0 (- sunrise sunrise-buffer-minutes)))
        end-minute (if include-night?
                     1440
                     (min 1440 (+ sunset sunset-buffer-minutes)))]
    
    (vec
     (for [interval (range n-intervals)
           :let [minutes (* interval interval-minutes)
                 [hour minute] (minutes->time minutes)]
           :when (or include-night?
                     (and (>= minutes start-minute)
                          (<= minutes end-minute)))]
       (let [pos (angles/solar-position latitude longitude year
                                        month day-of-month
                                        hour minute std-meridian)
             is-daylight? (and (>= minutes sunrise) (<= minutes sunset))]
         {:minutes minutes
          :zenith (:zenith pos)
          :azimuth (:azimuth pos)
          :altitude (:altitude pos)
          :single-axis-rotation (when is-daylight?
                                  (angles/single-axis-tilt pos latitude))
          :dual-axis (when is-daylight?
                       (angles/dual-axis-angles pos))
          :is-daylight? is-daylight?})))))

;;; ============================================================
;;; Full Year Generation
;;; ============================================================

(defn generate-year-table
  "Generate complete lookup table for entire year.
   
   Returns map:
   {:config <config-used>
    :days [<day-1-angles> <day-2-angles> ... <day-365-angles>]
    :metadata {:generated-at <timestamp>
               :total-entries <count>
               :storage-estimate-kb <size>}}"
  [config]
  (let [days (vec (for [doy (range 1 366)]
                    (generate-day-angles config doy)))
        total-entries (reduce + (map count days))
        ;; Estimate: 6 floats per entry × 4 bytes
        storage-kb (/ (* total-entries 6 4) 1024.0)]
    {:config config
     :days days
     :metadata {:generated-at (java.time.Instant/now)
                :total-entries total-entries
                :storage-estimate-kb storage-kb}}))

;;; ============================================================
;;; Table Lookup with Interpolation
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

(defn lookup-angles
  "Look up angles from table with linear interpolation.
   
   Inputs:
     table       - Result from generate-year-table
     day-of-year - Day (1-365)
     minutes     - Minutes since midnight
   
   Returns interpolated angle data or nil if outside table range."
  [{:keys [config days]} day-of-year minutes]
  (let [{:keys [interval-minutes]} config
        day-entries (get days (dec day-of-year))
        
        ;; Find bracketing entries
        entry-minutes (map :minutes day-entries)
        idx-before (last (keep-indexed
                          (fn [i m] (when (<= m minutes) i))
                          entry-minutes))]
    
    (when idx-before
      (let [entry-before (get day-entries idx-before)
            entry-after (get day-entries (inc idx-before))
            
            ;; Calculate interpolation fraction
            t0 (:minutes entry-before)
            t1 (if entry-after
                 (:minutes entry-after)
                 (+ t0 interval-minutes))
            fraction (/ (- minutes t0) (- t1 t0))]
        
        (if (or (nil? entry-after) (< fraction 0.01))
          ;; Use entry-before directly
          entry-before
          
          ;; Interpolate
          {:minutes minutes
           :zenith (+ (:zenith entry-before)
                      (* fraction (- (:zenith entry-after)
                                     (:zenith entry-before))))
           :azimuth (interpolate-angle (:azimuth entry-before)
                                       (:azimuth entry-after)
                                       fraction)
           :altitude (+ (:altitude entry-before)
                        (* fraction (- (:altitude entry-after)
                                       (:altitude entry-before))))
           :is-daylight? (:is-daylight? entry-before)
           :interpolated? true})))))

;;; ============================================================
;;; Compact Export Formats
;;; ============================================================

(defn table->compact-single-axis
  "Export table as compact single-axis format.
   Returns vector of vectors: [[day1-rotations] [day2-rotations] ...]
   Uses nil for non-daylight intervals."
  [{:keys [days]}]
  (mapv (fn [day-entries]
          (mapv :single-axis-rotation day-entries))
        days))

(defn table->compact-dual-axis
  "Export table as compact dual-axis format.
   Returns vector of vectors of [tilt azimuth] pairs."
  [{:keys [days]}]
  (mapv (fn [day-entries]
          (mapv (fn [{:keys [dual-axis]}]
                  (when dual-axis
                    [(:tilt dual-axis) (:panel-azimuth dual-axis)]))
                day-entries))
        days))

(defn table->binary
  "Serialize table to binary format for embedded systems.
   Returns byte array with int16 encoded angles (× 100 for 0.01° resolution)."
  [{:keys [days]} axis-type]
  (let [encode-angle (fn [a] (if a
                               (short (math/round (* a 100)))
                               Short/MIN_VALUE))
        entries (case axis-type
                  :single-axis (for [day days, entry day]
                                 [(encode-angle (:single-axis-rotation entry))])
                  :dual-axis (for [day days, entry day]
                               [(encode-angle (get-in entry [:dual-axis :tilt]))
                                (encode-angle (get-in entry [:dual-axis :panel-azimuth]))]))]
    ;; In production, would write to ByteBuffer
    (flatten entries)))

;;; ============================================================
;;; Example Usage
;;; ============================================================

(defn example-table-generation
  "Demonstrate table generation and lookup."
  []
  (println "Generating solar angle lookup table...")
  (let [config (assoc default-config :include-night? false)
        table (generate-year-table config)
        {:keys [total-entries storage-estimate-kb]} (:metadata table)]
    
    (println (format "Generated table with %,d entries" total-entries))
    (println (format "Estimated storage: %.1f KB" storage-estimate-kb))
    (println)
    
    ;; Example lookups
    (println "Example lookups for Springfield, IL:")
    (println)
    
    (doseq [[label doy minutes] [["Spring equinox noon" 80 720]
                                  ["Summer solstice 2pm" 172 840]
                                  ["Winter solstice 10am" 355 600]]]
      (let [result (lookup-angles table doy minutes)]
        (println (format "%s (day %d, %02d:%02d):"
                         label doy (quot minutes 60) (mod minutes 60)))
        (println (format "  Zenith: %.1f°  Azimuth: %.1f°  Altitude: %.1f°"
                         (:zenith result) (:azimuth result) (:altitude result)))
        (when-let [sa (:single-axis-rotation result)]
          (println (format "  Single-axis rotation: %.1f°" sa)))
        (when-let [da (:dual-axis result)]
          (println (format "  Dual-axis: tilt=%.1f° panel-azimuth=%.1f°"
                           (:tilt da) (:panel-azimuth da))))
        (println)))
    
    ;; Return table for further use
    table))

(comment
  ;; Generate full year table
  (def my-table (example-table-generation))
  
  ;; Look up specific time
  (lookup-angles my-table 172 720)  ; Summer solstice noon
  
  ;; Export compact format
  (def compact (table->compact-dual-axis my-table))
  (count compact)  ; 365 days
  (count (first compact))  ; entries per day
  
  ;; Check table size
  (:metadata my-table)
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

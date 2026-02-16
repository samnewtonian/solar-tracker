# Solar Panel Angle Calculations

Reference guide for computing optimal solar panel angles based on date, time, and geographic position. Includes formulas for both single-axis and dual-axis tracking systems.

---

## Overview

The optimal angle for a solar panel is one that makes the panel surface perpendicular (normal) to incoming sunlight. This requires calculating the sun's position in the sky, expressed as:

- **Zenith Angle (θz)**: Angle between the sun and vertical (directly overhead)
- **Azimuth Angle (A)**: Compass direction of the sun, measured clockwise from true north

For **single-axis tracking**, only the tilt (elevation) changes throughout the day.  
For **dual-axis tracking**, both tilt and azimuth orientation change to follow the sun precisely.

---

## Variables and Definitions

| Symbol | Name | Unit | Description |
|--------|------|------|-------------|
| φ | Latitude | degrees | Geographic latitude (positive = North) |
| λ | Longitude | degrees | Geographic longitude (positive = East) |
| n | Day of Year | integer | Ordinal day (Jan 1 = 1, Dec 31 = 365/366) |
| δ | Solar Declination | degrees | Angle between sun's rays and Earth's equatorial plane |
| h | Hour Angle | degrees | Angular displacement of sun from solar noon |
| θz | Zenith Angle | degrees | Angle from vertical to sun position |
| A | Azimuth Angle | degrees | Compass bearing of sun (0° = North, 90° = East) |
| α | Solar Altitude/Elevation | degrees | Angle from horizon to sun (α = 90° - θz) |
| LST | Local Solar Time | hours | True solar time at observer's location |
| LT | Local Time | hours | Clock time (24-hour format, decimal) |
| E | Equation of Time | minutes | Correction for Earth's orbital eccentricity |

---

## Step-by-Step Calculations

### Step 1: Calculate Day of Year (n)

Convert calendar date to ordinal day of year (1-365 or 1-366 for leap years).

```
n = day_of_year(date)
```

**Examples:**
- January 1 → n = 1
- March 21 → n = 80 (non-leap year)
- June 21 → n = 172
- December 31 → n = 365

---

### Step 2: Calculate Intermediate Angle B

Used in the Equation of Time calculation:

```
B = (n - 1) × (360° / 365)
```

Convert to radians for trigonometric functions:

```
B_rad = B × (π / 180)
```

---

### Step 3: Calculate Equation of Time (E)

The Equation of Time corrects for Earth's elliptical orbit and axial tilt. Result is in **minutes**:

```
E = 229.18 × (0.000075 
            + 0.001868 × cos(B) 
            - 0.032077 × sin(B) 
            - 0.014615 × cos(2B) 
            - 0.040849 × sin(2B))
```

This correction ranges from approximately -14 to +16 minutes throughout the year.

---

### Step 4: Calculate Local Solar Time (LST)

Convert clock time to true solar time. **UTC input is required** — this avoids DST complications entirely. The calling code accepts a timezone-aware datetime (`ZonedDateTime`) and converts to UTC internally, so DST handling stays at the system boundary.

The UTC→LST correction is constant for a given day and longitude:

```
correction = (4 × L_loc) / 60 + E / 60    (hours)
LST = mod(UTC_hours + correction, 24)
```

Where:
- `UTC_hours` = Time in UTC as decimal hours (e.g., 7:30 PM UTC = 19.5)
- `L_loc` = Local longitude in degrees (negative for West)
- `E` = Equation of Time (minutes)

The `mod 24` handles day boundary wraparound.

**Why UTC, not local time:** An earlier version of this library used a `local-solar-time` formula with an explicit `std-meridian` parameter: `LST = LT + (4 × (L_loc - L_st)) / 60 + E / 60`. This was fragile — callers had to manually subtract 1 hour during DST, and the `std-meridian` was a political/administrative concept disconnected from the physical calculation. The UTC approach eliminates both problems: the physical correction depends only on longitude (a constant), and timezone-aware datetime objects handle DST transitions automatically.

---

### Step 5: Calculate Hour Angle (h)

The hour angle represents the sun's angular position relative to solar noon:

```
h = 15° × (LST - 12)
```

Properties:
- At solar noon: h = 0°
- Morning (before noon): h < 0° (negative)
- Afternoon (after noon): h > 0° (positive)
- Each hour = 15° of rotation

---

### Step 6: Calculate Solar Declination (δ)

The declination is the angle between the sun and the Earth's equatorial plane:

```
δ = 23.45° × sin(360° × (284 + n) / 365)
```

Properties:
- Summer solstice (~June 21): δ ≈ +23.45°
- Winter solstice (~Dec 21): δ ≈ -23.45°
- Equinoxes (~Mar 21, Sep 21): δ ≈ 0°

---

### Step 7: Calculate Solar Zenith Angle (θz)

The zenith angle is the angle between the sun and the vertical (straight up):

```
cos(θz) = sin(φ) × sin(δ) + cos(φ) × cos(δ) × cos(h)
```

Solve for θz:

```
θz = arccos(sin(φ) × sin(δ) + cos(φ) × cos(δ) × cos(h))
```

**The optimal panel tilt angle equals the zenith angle** for maximum direct irradiance.

---

### Step 8: Calculate Solar Altitude/Elevation (α)

The altitude is the complement of the zenith angle:

```
α = 90° - θz
```

This is the angle of the sun above the horizon.

---

### Step 9: Calculate Solar Azimuth Angle (A)

The azimuth is the compass direction of the sun. This calculation requires careful handling of quadrants:

```
cos(A) = (sin(δ) × cos(φ) - cos(δ) × sin(φ) × cos(h)) / cos(α)
```

Or equivalently using the sine form for quadrant determination:

```
sin(A) = -cos(δ) × sin(h) / cos(α)
```

**Quadrant Resolution:**
- If sin(h) < 0 (morning): A is in eastern half (0° to 180° measured from North through East)
- If sin(h) > 0 (afternoon): A is in western half (180° to 360° measured from North through West)

A more robust formula using atan2:

```
A = atan2(-sin(h), tan(δ) × cos(φ) - sin(φ) × cos(h))
```

Then normalize to 0-360° range.

---

## Single-Axis Tracking

Single-axis trackers rotate on one axis, typically oriented north-south, adjusting the tilt throughout the day to follow the sun's elevation.

### Configuration

- **Axis orientation**: Typically aligned north-south
- **Motion**: Tilts east-to-west following the sun
- **Angle tracked**: Solar elevation/altitude

### Optimal Tilt Angle

For a horizontal single-axis tracker (north-south axis):

```
Panel Tilt = arctan(tan(θz) × |cos(A - axis_azimuth)|)
```

For simplified north-south axis where tracker follows the sun's daily arc:

```
Tracker Rotation Angle = arctan(tan(h) / cos(φ))
```

### Energy Gain

- **15-25% more energy** than fixed installations
- **20-25% gain** at mid-latitudes like Central Illinois (40°N)

---

## Dual-Axis Tracking

Dual-axis trackers adjust both tilt and azimuth to keep panels perpendicular to sunlight at all times.

### Configuration

- **Primary axis**: Adjusts azimuth (compass direction)
- **Secondary axis**: Adjusts tilt (elevation angle)
- **Motion**: Full sun tracking in two dimensions

### Optimal Angles

**Tilt Angle (from horizontal):**
```
Panel Tilt = θz (Solar Zenith Angle)
```

**Azimuth Angle (compass direction panel faces):**
```
Panel Azimuth = A (Solar Azimuth) + 180°
```

(Panel faces toward the sun, so add 180° to the sun's position)

### Energy Gain

- **25-35% more energy** than fixed installations
- **30-35% gain** at mid-latitudes like Central Illinois (40°N)

---

## Simplified Fixed Installation Formulas

For installations without tracking, use these simplified optimizations:

### Annual Optimal Tilt

```
Optimal Tilt = 0.76 × |latitude| + 3.1°
```

Example for Central Illinois (40°N):
```
Optimal Tilt = 0.76 × 40 + 3.1 = 33.5°
```

### Seasonal Adjustments

| Season | Tilt Adjustment |
|--------|-----------------|
| Summer | Latitude - 15° |
| Winter | Latitude + 15° |
| Spring/Fall | Latitude ± 0° |

---

## Clojure Reference Implementation

```clojure
(ns com.kardashevtypev.solar.angles
  "Solar panel angle calculations for single and dual-axis tracking systems.
   All angles in degrees unless otherwise noted."
  (:require [clojure.math :as math])
  (:import [java.time ZonedDateTime ZoneOffset]))

;;; ============================================================
;;; Constants and Conversion Utilities
;;; ============================================================

(def ^:const earth-axial-tilt 23.45)
(def ^:const degrees-per-hour 15.0)
(def ^:const deg->rad-factor (/ math/PI 180.0))
(def ^:const rad->deg-factor (/ 180.0 math/PI))

(defn deg->rad [deg] (* deg deg->rad-factor))
(defn rad->deg [rad] (* rad rad->deg-factor))

(defn normalize-angle
  "Normalize angle to 0-360 degree range."
  [angle]
  (mod angle 360.0))

;;; ============================================================
;;; Date Utilities
;;; ============================================================

(defn leap-year?
  "Returns true if year is a leap year."
  [year]
  (or (zero? (mod year 400))
      (and (zero? (mod year 4))
           (not (zero? (mod year 100))))))

(defn days-in-months
  "Returns a vector of days per month for the given year."
  [year]
  [31 (if (leap-year? year) 29 28) 31 30 31 30 31 31 30 31 30 31])

(defn day-of-year
  "Calculate ordinal day of year (1-366) from year, month, day."
  [year month day]
  (let [days-before-month (reduce + (take (dec month) (days-in-months year)))]
    (+ days-before-month day)))

;;; ============================================================
;;; Core Solar Position Calculations
;;; ============================================================

(defn intermediate-angle-b
  "Calculate intermediate angle B used in equation of time.
   Input: n = day of year (1-365)
   Output: B in radians"
  [n]
  (deg->rad (* (dec n) (/ 360.0 365.0))))

(defn equation-of-time
  "Calculate the Equation of Time correction.
   Input: n = day of year (1-365)
   Output: correction in minutes"
  [n]
  (let [b (intermediate-angle-b n)]
    (* 229.18
       (+ 0.000075
          (* 0.001868 (math/cos b))
          (* -0.032077 (math/sin b))
          (* -0.014615 (math/cos (* 2 b)))
          (* -0.040849 (math/sin (* 2 b)))))))

(defn hour-angle
  "Calculate the hour angle from local solar time.
   Input: local-solar-time in decimal hours
   Output: hour angle in degrees"
  [local-solar-time]
  (* degrees-per-hour (- local-solar-time 12.0)))

(defn solar-declination
  "Calculate solar declination angle.
   Input: n = day of year (1-365)
   Output: declination in degrees"
  [n]
  (* earth-axial-tilt
     (math/sin (deg->rad (* 360.0 (/ (+ 284 n) 365.0))))))

(defn solar-zenith-angle
  "Calculate the solar zenith angle."
  [latitude declination hour-angle]
  (let [lat-rad (deg->rad latitude)
        dec-rad (deg->rad declination)
        ha-rad (deg->rad hour-angle)
        cos-zenith (+ (* (math/sin lat-rad) (math/sin dec-rad))
                      (* (math/cos lat-rad)
                         (math/cos dec-rad)
                         (math/cos ha-rad)))]
    (rad->deg (math/acos (max -1.0 (min 1.0 cos-zenith))))))

(defn solar-altitude [zenith-angle] (- 90.0 zenith-angle))

(defn solar-azimuth
  "Calculate solar azimuth angle using atan2 for proper quadrant handling."
  [latitude declination hour-angle]
  (let [lat-rad (deg->rad latitude)
        dec-rad (deg->rad declination)
        ha-rad (deg->rad hour-angle)
        sin-az (* -1.0 (math/cos dec-rad) (math/sin ha-rad))
        cos-az (- (* (math/sin dec-rad) (math/cos lat-rad))
                  (* (math/cos dec-rad) (math/sin lat-rad) (math/cos ha-rad)))]
    (normalize-angle (rad->deg (math/atan2 sin-az cos-az)))))

(defn utc-lst-correction
  "Compute the UTC→LST correction in hours for a given longitude and equation of time.
   LST = mod(utc-hours + correction, 24).
   Constant for a given day and longitude — compute once per day."
  [longitude eot]
  (+ (/ (* 4.0 longitude) 60.0) (/ eot 60.0)))

(defn solar-angles-at
  "Compute solar angles from precomputed day-constants and UTC time.
   Returns {:local-solar-time :hour-angle :zenith :altitude :azimuth}.
   Used by both solar-position and lookup-table generation."
  [latitude decl correction utc-hours]
  (let [lst    (mod (+ utc-hours correction) 24.0)
        ha     (hour-angle lst)
        zenith (solar-zenith-angle latitude decl ha)
        alt    (solar-altitude zenith)
        azim   (solar-azimuth latitude decl ha)]
    {:local-solar-time lst
     :hour-angle ha
     :zenith zenith
     :altitude alt
     :azimuth azim}))

;;; ============================================================
;;; High-Level Solar Position Function
;;; ============================================================

(defn solar-position
  "Calculate complete solar position for given location and timezone-aware datetime.

   Inputs:
     latitude  - Observer's latitude (degrees, positive = North)
     longitude - Observer's longitude (degrees, negative = West)
     datetime  - A java.time.ZonedDateTime with timezone info.
                 Internally converted to UTC via .withZoneSameInstant.

   Returns a map containing:
     :day-of-year, :declination, :equation-of-time, :local-solar-time,
     :hour-angle, :zenith, :altitude, :azimuth"
  [latitude longitude datetime]
  (let [utc        (.withZoneSameInstant datetime ZoneOffset/UTC)
        utc-hours  (+ (.getHour utc) (/ (.getMinute utc) 60.0) (/ (.getSecond utc) 3600.0))
        n          (day-of-year (.getYear utc) (.getMonthValue utc) (.getDayOfMonth utc))
        eot        (equation-of-time n)
        decl       (solar-declination n)
        correction (utc-lst-correction longitude eot)
        angles     (solar-angles-at latitude decl correction utc-hours)]
    (merge {:day-of-year n :declination decl :equation-of-time eot} angles)))

;;; ============================================================
;;; Panel Angle Calculations
;;; ============================================================

(defn single-axis-tilt
  "Calculate optimal tilt angle for single-axis (north-south) tracker.
   Input: solar-position map, latitude
   Output: rotation angle in degrees (positive = tilted toward west)"
  [{:keys [hour-angle]} latitude]
  (let [ha-rad (deg->rad hour-angle)
        lat-rad (deg->rad latitude)]
    (rad->deg (math/atan (/ (math/tan ha-rad) (math/cos lat-rad))))))

(defn dual-axis-angles
  "Calculate optimal angles for dual-axis tracker.
   Input: solar-position map
   Output: map with :tilt and :panel-azimuth in degrees"
  [{:keys [zenith azimuth]}]
  {:tilt zenith
   :panel-azimuth (normalize-angle (+ azimuth 180.0))})

;;; ============================================================
;;; Fixed Installation Helpers
;;; ============================================================

(defn optimal-fixed-tilt [latitude]
  (+ (* 0.76 (abs latitude)) 3.1))

(defn seasonal-tilt-adjustment [latitude season]
  (case season
    :summer (- (abs latitude) 15.0)
    :winter (+ (abs latitude) 15.0)
    (:spring :fall) (abs latitude)))

;;; ============================================================
;;; Example Usage
;;; ============================================================

(defn example-calculation []
  (let [latitude 39.8
        longitude -89.6
        datetime (ZonedDateTime/of 2026 3 21 12 0 0 0 (java.time.ZoneId/of "America/Chicago"))
        pos (solar-position latitude longitude datetime)
        single-axis (single-axis-tilt pos latitude)
        dual-axis (dual-axis-angles pos)]
    (println (format "Location: Springfield, IL (%.1f°N, %.1f°W)" latitude (- longitude)))
    (println (format "Datetime: %s" datetime))
    (println (format "Zenith: %.2f°  Altitude: %.2f°  Azimuth: %.2f°"
                     (:zenith pos) (:altitude pos) (:azimuth pos)))
    (println (format "Single-axis rotation: %.2f°" single-axis))
    (println (format "Dual-axis tilt: %.2f°  azimuth: %.2f°"
                     (:tilt dual-axis) (:panel-azimuth dual-axis)))))

(comment
  (example-calculation)

  ;; Calculate position for a specific time
  (solar-position 39.8 -89.6
                  (ZonedDateTime/of 2026 6 21 14 30 0 0
                                    (java.time.ZoneId/of "America/Chicago")))

  ;; Get dual-axis angles
  (-> (solar-position 39.8 -89.6
                      (ZonedDateTime/of 2026 6 21 14 30 0 0
                                        (java.time.ZoneId/of "America/Chicago")))
      (dual-axis-angles))
  )
```

---

## Worked Example

**Location:** Springfield, IL (39.8°N, 89.6°W)  
**Date:** March 21, 2026 (Spring Equinox)  
**Time:** Solar Noon (~18:00 UTC)

### Step-by-Step Calculation

1. **Day of Year:** n = 80 (March 21)

2. **Intermediate Angle B:**
   ```
   B = (80 - 1) × (360° / 365) = 77.9°
   ```

3. **Equation of Time:**
   ```
   E ≈ -7.5 minutes (mid-March)
   ```

4. **UTC→LST Correction and Local Solar Time:**
   ```
   correction = (4 × (-89.6)) / 60 + (-7.5) / 60 = -5.97 - 0.125 = -6.1 hours
   LST = mod(18.0 + (-6.1), 24) ≈ 11.9 hours
   ```

5. **Hour Angle:**
   ```
   h = 15° × (11.9 - 12) = -1.5°
   ```

6. **Solar Declination:**
   ```
   δ = 23.45° × sin(360° × (284 + 80) / 365)
   δ ≈ 0° (equinox)
   ```

7. **Solar Zenith Angle:**
   ```
   cos(θz) = sin(39.8°) × sin(0°) + cos(39.8°) × cos(0°) × cos(-1.5°)
   cos(θz) = 0 + 0.766 × 1 × 0.9997 = 0.766
   θz = arccos(0.766) ≈ 40.0°
   ```

8. **Solar Altitude:**
   ```
   α = 90° - 40° = 50°
   ```

9. **Solar Azimuth:**
   ```
   A ≈ 179° (nearly due south, slightly east since before solar noon)
   ```

### Resulting Panel Angles

| Tracking Type | Tilt | Azimuth |
|---------------|------|---------|
| Fixed (annual optimal) | 33.5° | 180° (due south) |
| Single-axis | ~0° rotation at solar noon | N/A |
| Dual-axis | 40.0° | 359° (facing sun) |

---

## References

- NOAA Solar Calculator methodology
- Duffie & Beckman, "Solar Engineering of Thermal Processes"
- PVEducation.org solar position algorithms

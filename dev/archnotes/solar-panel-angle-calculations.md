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
| n | Day of Year | integer | Julian day (Jan 1 = 1, Dec 31 = 365/366) |
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

Convert calendar date to Julian day number (1-365 or 1-366 for leap years).

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

Convert clock time to true solar time:

```
LST = LT + (4 × (L_st - L_loc)) / 60 + E / 60
```

Where:
- `LT` = Local clock time in decimal hours (e.g., 2:30 PM = 14.5)
- `L_st` = Standard meridian for local time zone (e.g., -90° for US Central)
- `L_loc` = Local longitude (negative for West)
- `E` = Equation of Time (minutes)

**Time Zone Standard Meridians:**
| Time Zone | Standard Meridian |
|-----------|-------------------|
| US Eastern (EST/EDT) | -75° |
| US Central (CST/CDT) | -90° |
| US Mountain (MST/MDT) | -105° |
| US Pacific (PST/PDT) | -120° |
| UTC | 0° |

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
(ns solar.angles
  "Solar panel angle calculations for single and dual-axis tracking systems.
   All angles in degrees unless otherwise noted."
  (:require [clojure.math :as math]))

;;; ============================================================
;;; Constants and Conversion Utilities
;;; ============================================================

(def ^:const earth-axial-tilt 23.45)
(def ^:const degrees-per-hour 15.0)

(defn deg->rad
  "Convert degrees to radians."
  [deg]
  (* deg (/ math/PI 180.0)))

(defn rad->deg
  "Convert radians to degrees."
  [rad]
  (* rad (/ 180.0 math/PI)))

(defn normalize-angle
  "Normalize angle to 0-360 degree range."
  [angle]
  (mod (+ (mod angle 360.0) 360.0) 360.0))

;;; ============================================================
;;; Date Utilities
;;; ============================================================

(defn day-of-year
  "Calculate day of year (1-366) from year, month, day.
   Uses the standard algorithm accounting for leap years."
  [year month day]
  (let [leap? (or (zero? (mod year 400))
                  (and (zero? (mod year 4))
                       (not (zero? (mod year 100)))))
        days-in-months [31 (if leap? 29 28) 31 30 31 30 31 31 30 31 30 31]
        days-before-month (reduce + (take (dec month) days-in-months))]
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
   Output: correction in minutes
   
   This accounts for Earth's elliptical orbit and axial tilt,
   causing solar noon to drift from clock noon throughout the year."
  [n]
  (let [b (intermediate-angle-b n)]
    (* 229.18
       (+ 0.000075
          (* 0.001868 (math/cos b))
          (* -0.032077 (math/sin b))
          (* -0.014615 (math/cos (* 2 b)))
          (* -0.040849 (math/sin (* 2 b)))))))

(defn local-solar-time
  "Calculate Local Solar Time from clock time.
   Inputs:
     local-time     - Clock time in decimal hours (e.g., 14.5 for 2:30 PM)
     std-meridian   - Standard meridian for time zone (degrees, negative for West)
     local-longitude - Observer's longitude (degrees, negative for West)
     day-of-year    - Day number (1-365)
   Output: Local solar time in decimal hours"
  [local-time std-meridian local-longitude day-of-year]
  (let [eot (equation-of-time day-of-year)
        ;; Longitude correction: 4 minutes per degree difference
        long-correction (/ (* 4.0 (- std-meridian local-longitude)) 60.0)
        ;; Equation of time correction (convert from minutes to hours)
        eot-correction (/ eot 60.0)]
    (+ local-time long-correction eot-correction)))

(defn hour-angle
  "Calculate the hour angle from local solar time.
   Input: local-solar-time in decimal hours
   Output: hour angle in degrees
   
   Properties:
     - At solar noon: h = 0°
     - Morning: h < 0° (sun is east)
     - Afternoon: h > 0° (sun is west)
     - Each hour = 15° of Earth rotation"
  [local-solar-time]
  (* degrees-per-hour (- local-solar-time 12.0)))

(defn solar-declination
  "Calculate solar declination angle.
   Input: n = day of year (1-365)
   Output: declination in degrees
   
   The declination is the angle between the sun and Earth's equatorial plane.
   Ranges from -23.45° (winter solstice) to +23.45° (summer solstice)."
  [n]
  (* earth-axial-tilt
     (math/sin (deg->rad (* 360.0 (/ (+ 284 n) 365.0))))))

(defn solar-zenith-angle
  "Calculate the solar zenith angle.
   Inputs:
     latitude    - Observer's latitude in degrees (positive = North)
     declination - Solar declination in degrees
     hour-angle  - Hour angle in degrees
   Output: zenith angle in degrees
   
   The zenith angle is the angle between the sun and vertical (straight up).
   At solar noon on the equinox at the equator, zenith = 0°."
  [latitude declination hour-angle]
  (let [lat-rad (deg->rad latitude)
        dec-rad (deg->rad declination)
        ha-rad (deg->rad hour-angle)
        cos-zenith (+ (* (math/sin lat-rad) (math/sin dec-rad))
                      (* (math/cos lat-rad)
                         (math/cos dec-rad)
                         (math/cos ha-rad)))]
    ;; Clamp to [-1, 1] to handle floating point errors
    (rad->deg (math/acos (max -1.0 (min 1.0 cos-zenith))))))

(defn solar-altitude
  "Calculate solar altitude (elevation) angle.
   Input: zenith-angle in degrees
   Output: altitude in degrees above horizon
   
   The altitude is simply the complement of the zenith angle."
  [zenith-angle]
  (- 90.0 zenith-angle))

(defn solar-azimuth
  "Calculate solar azimuth angle using atan2 for proper quadrant handling.
   Inputs:
     latitude    - Observer's latitude in degrees
     declination - Solar declination in degrees
     hour-angle  - Hour angle in degrees
     zenith-angle - Solar zenith angle in degrees
   Output: azimuth in degrees (0° = North, 90° = East, 180° = South, 270° = West)
   
   Uses the robust atan2-based formula to avoid quadrant ambiguity."
  [latitude declination hour-angle zenith-angle]
  (let [lat-rad (deg->rad latitude)
        dec-rad (deg->rad declination)
        ha-rad (deg->rad hour-angle)
        alt-rad (deg->rad (solar-altitude zenith-angle))
        
        ;; Calculate azimuth using atan2 for proper quadrant handling
        sin-az (* -1.0 (math/cos dec-rad) (math/sin ha-rad))
        cos-az (- (* (math/sin dec-rad) (math/cos lat-rad))
                  (* (math/cos dec-rad) (math/sin lat-rad) (math/cos ha-rad)))
        
        ;; atan2 gives result in radians, in range [-π, π]
        az-rad (math/atan2 sin-az cos-az)]
    
    ;; Convert to degrees and normalize to [0, 360)
    (normalize-angle (rad->deg az-rad))))

;;; ============================================================
;;; High-Level Solar Position Function
;;; ============================================================

(defn solar-position
  "Calculate complete solar position for given location, date, and time.
   
   Inputs:
     latitude        - Observer's latitude (degrees, positive = North)
     longitude       - Observer's longitude (degrees, negative = West)
     year            - Calendar year
     month           - Month (1-12)
     day             - Day of month (1-31)
     hour            - Hour in 24-hour format (0-23)
     minute          - Minute (0-59)
     std-meridian    - Standard meridian for time zone (degrees)
   
   Returns a map containing:
     :day-of-year    - Julian day number
     :declination    - Solar declination (degrees)
     :equation-of-time - Equation of time correction (minutes)
     :local-solar-time - True solar time (decimal hours)
     :hour-angle     - Hour angle (degrees)
     :zenith         - Solar zenith angle (degrees)
     :altitude       - Solar altitude/elevation (degrees)
     :azimuth        - Solar azimuth (degrees, 0° = North)"
  [latitude longitude year month day hour minute std-meridian]
  (let [n (day-of-year year month day)
        local-time (+ hour (/ minute 60.0))
        eot (equation-of-time n)
        lst (local-solar-time local-time std-meridian longitude n)
        ha (hour-angle lst)
        decl (solar-declination n)
        zenith (solar-zenith-angle latitude decl ha)
        alt (solar-altitude zenith)
        azim (solar-azimuth latitude decl ha zenith)]
    {:day-of-year n
     :declination decl
     :equation-of-time eot
     :local-solar-time lst
     :hour-angle ha
     :zenith zenith
     :altitude alt
     :azimuth azim}))

;;; ============================================================
;;; Panel Angle Calculations
;;; ============================================================

(defn single-axis-tilt
  "Calculate optimal tilt angle for single-axis (north-south) tracker.
   
   For a horizontal single-axis tracker with north-south orientation,
   the tracker rotates to follow the sun's daily east-west arc.
   
   Input: solar-position map from (solar-position ...)
   Output: rotation angle in degrees (positive = tilted toward west)"
  [{:keys [hour-angle] :as solar-pos} latitude]
  (let [ha-rad (deg->rad hour-angle)
        lat-rad (deg->rad latitude)]
    (rad->deg (math/atan (/ (math/tan ha-rad)
                            (math/cos lat-rad))))))

(defn dual-axis-angles
  "Calculate optimal angles for dual-axis tracker.
   
   Returns both the panel tilt and azimuth needed to point
   directly at the sun.
   
   Input: solar-position map from (solar-position ...)
   Output: map with :tilt and :azimuth in degrees"
  [{:keys [zenith azimuth]}]
  {:tilt zenith
   :panel-azimuth (normalize-angle (+ azimuth 180.0))})

;;; ============================================================
;;; Fixed Installation Helpers
;;; ============================================================

(defn optimal-fixed-tilt
  "Calculate optimal annual fixed tilt angle for a given latitude.
   Uses the empirical formula: tilt = 0.76 × |latitude| + 3.1°
   
   Input: latitude in degrees
   Output: optimal fixed tilt angle in degrees"
  [latitude]
  (+ (* 0.76 (abs latitude)) 3.1))

(defn seasonal-tilt-adjustment
  "Calculate seasonal tilt adjustment for fixed installations.
   
   Inputs:
     latitude - Observer's latitude (degrees)
     season   - One of :summer, :winter, :spring, :fall
   Output: recommended tilt angle in degrees"
  [latitude season]
  (case season
    :summer (- (abs latitude) 15.0)
    :winter (+ (abs latitude) 15.0)
    (:spring :fall) (abs latitude)))

;;; ============================================================
;;; Example Usage and Demonstration
;;; ============================================================

(defn example-calculation
  "Demonstrate calculations for Springfield, IL on March 21 at solar noon.
   Springfield, IL: 39.8°N, 89.6°W
   Central Time Zone standard meridian: -90°"
  []
  (let [;; Location: Springfield, IL
        latitude 39.8
        longitude -89.6
        std-meridian -90.0
        
        ;; Date: March 21 (Spring Equinox)
        year 2026
        month 3
        day 21
        
        ;; Time: approximately solar noon (accounting for longitude offset)
        hour 12
        minute 0
        
        ;; Calculate solar position
        pos (solar-position latitude longitude year month day hour minute std-meridian)
        
        ;; Calculate panel angles
        single-axis (single-axis-tilt pos latitude)
        dual-axis (dual-axis-angles pos)
        fixed-annual (optimal-fixed-tilt latitude)]
    
    (println "=== Solar Position Calculation Example ===")
    (println (format "Location: Springfield, IL (%.1f°N, %.1f°W)" latitude (- longitude)))
    (println (format "Date: %d-%02d-%02d" year month day))
    (println (format "Time: %02d:%02d local time" hour minute))
    (println)
    (println "--- Solar Position ---")
    (println (format "Day of year: %d" (:day-of-year pos)))
    (println (format "Declination: %.2f°" (:declination pos)))
    (println (format "Equation of Time: %.2f minutes" (:equation-of-time pos)))
    (println (format "Local Solar Time: %.2f hours" (:local-solar-time pos)))
    (println (format "Hour Angle: %.2f°" (:hour-angle pos)))
    (println (format "Zenith Angle: %.2f°" (:zenith pos)))
    (println (format "Altitude: %.2f°" (:altitude pos)))
    (println (format "Azimuth: %.2f° (0°=N, 90°=E, 180°=S)" (:azimuth pos)))
    (println)
    (println "--- Optimal Panel Angles ---")
    (println (format "Single-axis tracker rotation: %.2f°" single-axis))
    (println (format "Dual-axis tilt: %.2f°" (:tilt dual-axis)))
    (println (format "Dual-axis panel azimuth: %.2f°" (:panel-azimuth dual-axis)))
    (println (format "Fixed annual optimal tilt: %.1f°" fixed-annual))
    (println)
    
    ;; Return the data for programmatic use
    {:solar-position pos
     :single-axis-rotation single-axis
     :dual-axis dual-axis
     :fixed-optimal-tilt fixed-annual}))

(comment
  ;; Run the example
  (example-calculation)
  
  ;; Calculate position for a specific time
  (solar-position 39.8 -89.6 2026 6 21 14 30 -90.0)
  
  ;; Get dual-axis angles
  (-> (solar-position 39.8 -89.6 2026 6 21 14 30 -90.0)
      (dual-axis-angles))
  
  ;; Calculate for summer solstice at noon
  (let [pos (solar-position 39.8 -89.6 2026 6 21 12 0 -90.0)]
    {:zenith (:zenith pos)
     :summer-tilt (seasonal-tilt-adjustment 39.8 :summer)})
  )
```

---

## Worked Example

**Location:** Springfield, IL (39.8°N, 89.6°W)  
**Date:** March 21, 2026 (Spring Equinox)  
**Time:** Solar Noon (approximately 12:00 local time)

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

4. **Local Solar Time:**
   ```
   LST = 12 + (4 × (-90 - (-89.6))) / 60 + (-7.5) / 60
   LST ≈ 11.9 hours
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

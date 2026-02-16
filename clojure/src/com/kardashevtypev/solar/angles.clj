(ns com.kardashevtypev.solar.angles
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
  (mod angle 360.0))

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
     n              - Day number (1-365)
   Output: Local solar time in decimal hours"
  [local-time std-meridian local-longitude n]
  (let [eot (equation-of-time n)
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
   Output: azimuth in degrees (0° = North, 90° = East, 180° = South, 270° = West)

   Uses the robust atan2-based formula to avoid quadrant ambiguity."
  [latitude declination hour-angle]
  (let [lat-rad (deg->rad latitude)
        dec-rad (deg->rad declination)
        ha-rad (deg->rad hour-angle)
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
        azim (solar-azimuth latitude decl ha)]
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
  [{:keys [hour-angle]} latitude]
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

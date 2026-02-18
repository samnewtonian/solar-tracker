(ns com.kardashevtypev.solar.examples.calculation
  "Demonstrate solar position calculations for Springfield, IL on March 21 at solar noon."
  (:require [com.kardashevtypev.solar.angles :as a])
  (:import [java.time ZonedDateTime ZoneId]))

(defn run
  "Run the example calculation and print results."
  [& _]
  (let [latitude 39.8
        longitude -89.6
        datetime (ZonedDateTime/of 2026 3 21 12 0 0 0 (ZoneId/of "America/Chicago"))
        pos (a/solar-position latitude longitude datetime)
        single-axis (a/single-axis-tilt pos latitude)
        dual-axis (a/dual-axis-angles pos)
        fixed-annual (a/optimal-fixed-tilt latitude)]

    (println "=== Solar Position Calculation Example ===")
    (println (format "Location: Springfield, IL (%.1f°N, %.1f°W)" latitude (- longitude)))
    (println (format "Datetime: %s" datetime))
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
    (println (format "Fixed annual optimal tilt: %.1f°" fixed-annual))))

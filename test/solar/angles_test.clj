(ns solar.angles-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.test.alpha :as stest]
            [solar.angles :as a]
            [solar.angles.spec]))

;; Enable spec instrumentation for tests
(stest/instrument)

(defn approx=
  "Test if two numbers are approximately equal within tolerance."
  ([a b] (approx= a b 0.1))
  ([a b tolerance]
   (< (abs (- a b)) tolerance)))

(deftest day-of-year-test
  (testing "Day of year calculation"
    (is (= 1 (a/day-of-year 2026 1 1)) "Jan 1")
    (is (= 80 (a/day-of-year 2026 3 21)) "March 21 non-leap year")
    (is (= 365 (a/day-of-year 2026 12 31)) "Dec 31 non-leap year")

    ;; Leap year tests
    (is (= 60 (a/day-of-year 2024 2 29)) "Feb 29 leap year")
    (is (= 61 (a/day-of-year 2024 3 1)) "March 1 leap year")
    (is (= 366 (a/day-of-year 2024 12 31)) "Dec 31 leap year")

    ;; Century leap year rules
    (is (= 60 (a/day-of-year 2000 2 29)) "2000 is leap (divisible by 400)")
    (is (= 59 (a/day-of-year 1900 2 28)) "1900 not leap (divisible by 100 but not 400)")))

(deftest normalize-angle-test
  (testing "Angle normalization"
    (is (approx= 0.0 (a/normalize-angle 0.0)))
    (is (approx= 45.0 (a/normalize-angle 45.0)))
    (is (approx= 0.0 (a/normalize-angle 360.0)))
    (is (approx= 1.0 (a/normalize-angle 361.0)))
    (is (approx= 359.0 (a/normalize-angle -1.0)))
    (is (approx= 270.0 (a/normalize-angle -90.0)))
    (is (approx= 45.0 (a/normalize-angle 405.0)))
    (is (approx= 180.0 (a/normalize-angle -180.0)))))

(deftest solar-declination-test
  (testing "Solar declination at key dates"
    ;; Summer solstice (June 21, day ~172)
    (let [decl-summer (a/solar-declination 172)]
      (is (approx= 23.45 decl-summer 0.5) "Summer solstice near +23.45°"))

    ;; Winter solstice (Dec 21, day ~355)
    (let [decl-winter (a/solar-declination 355)]
      (is (approx= -23.45 decl-winter 0.5) "Winter solstice near -23.45°"))

    ;; Spring equinox (March 21, day ~80)
    (let [decl-spring (a/solar-declination 80)]
      (is (approx= 0.0 decl-spring 1.0) "Spring equinox near 0°"))

    ;; Fall equinox (Sept 21, day ~264)
    (let [decl-fall (a/solar-declination 264)]
      (is (approx= 0.0 decl-fall 1.0) "Fall equinox near 0°"))))

(deftest solar-position-springfield-equinox-test
  (testing "Solar position for Springfield, IL at spring equinox solar noon"
    ;; This is the worked example from the architecture docs
    (let [pos (a/solar-position 39.8 -89.6 2026 3 21 12 0 -90.0)]

      (is (= 80 (:day-of-year pos)) "Day of year")

      (is (approx= 0.0 (:declination pos) 1.0)
          "Declination near 0° at equinox")

      (is (approx= -7.5 (:equation-of-time pos) 2.0)
          "Equation of time around -7.5 minutes in mid-March")

      (is (approx= 40.0 (:zenith pos) 2.0)
          "Zenith angle around 40° at Springfield latitude")

      (is (approx= 50.0 (:altitude pos) 2.0)
          "Altitude around 50° (complement of zenith)")

      (is (or (approx= 179.0 (:azimuth pos) 5.0)
              (approx= 180.0 (:azimuth pos) 5.0))
          "Azimuth near 179-180° (nearly due south)"))))

(deftest solar-position-summer-solstice-test
  (testing "Solar position at summer solstice"
    (let [pos (a/solar-position 39.8 -89.6 2026 6 21 12 0 -90.0)]

      (is (approx= 23.45 (:declination pos) 1.0)
          "Declination near +23.45° at summer solstice")

      ;; At summer solstice, sun is higher in sky at noon
      (is (< (:zenith pos) 40.0)
          "Zenith angle less than equinox (sun higher)")

      (is (> (:altitude pos) 50.0)
          "Altitude greater than equinox (sun higher)"))))

(deftest solar-position-winter-solstice-test
  (testing "Solar position at winter solstice"
    (let [pos (a/solar-position 39.8 -89.6 2026 12 21 12 0 -90.0)]

      (is (approx= -23.45 (:declination pos) 1.0)
          "Declination near -23.45° at winter solstice")

      ;; At winter solstice, sun is lower in sky at noon
      (is (> (:zenith pos) 40.0)
          "Zenith angle greater than equinox (sun lower)")

      (is (< (:altitude pos) 50.0)
          "Altitude less than equinox (sun lower)"))))

(deftest single-axis-tilt-test
  (testing "Single-axis tracker rotation"
    ;; At solar noon, hour angle should be near 0°, so rotation should be near 0°
    (let [pos-noon (a/solar-position 39.8 -89.6 2026 3 21 12 0 -90.0)
          tilt-noon (a/single-axis-tilt pos-noon 39.8)]
      (is (approx= 0.0 tilt-noon 5.0)
          "Near-zero rotation at solar noon"))

    ;; Morning/afternoon should have non-zero rotation
    (let [pos-morning (a/solar-position 39.8 -89.6 2026 3 21 9 0 -90.0)
          tilt-morning (a/single-axis-tilt pos-morning 39.8)]
      (is (< tilt-morning 0.0)
          "Negative rotation in morning (tilted east)"))

    (let [pos-afternoon (a/solar-position 39.8 -89.6 2026 3 21 15 0 -90.0)
          tilt-afternoon (a/single-axis-tilt pos-afternoon 39.8)]
      (is (> tilt-afternoon 0.0)
          "Positive rotation in afternoon (tilted west)"))))

(deftest dual-axis-angles-test
  (testing "Dual-axis tracker angles"
    (let [pos (a/solar-position 39.8 -89.6 2026 3 21 12 0 -90.0)
          {:keys [tilt panel-azimuth]} (a/dual-axis-angles pos)]

      (is (approx= (:zenith pos) tilt 0.01)
          "Tilt equals zenith angle")

      ;; Panel azimuth should be opposite of solar azimuth (face the sun)
      ;; Solar azimuth ~179° means panel should face ~359° (nearly north, facing south sun)
      (is (or (approx= 359.0 panel-azimuth 5.0)
              (approx= 0.0 panel-azimuth 5.0))
          "Panel azimuth opposite of sun position"))))

(deftest optimal-fixed-tilt-test
  (testing "Optimal fixed tilt angle"
    ;; For Springfield, IL at 39.8°N
    (let [tilt (a/optimal-fixed-tilt 39.8)]
      (is (approx= 33.3 tilt 1.0)
          "Fixed tilt ~33.3° for 39.8°N latitude"))

    ;; Test formula: 0.76 × |latitude| + 3.1
    (is (approx= 33.5 (a/optimal-fixed-tilt 40.0) 0.1)
        "40°N -> 33.5°")

    (is (approx= 3.1 (a/optimal-fixed-tilt 0.0) 0.1)
        "0° latitude -> 3.1°")

    ;; Southern hemisphere (negative latitude)
    (is (approx= 33.5 (a/optimal-fixed-tilt -40.0) 0.1)
        "-40°S -> 33.5°")))

(deftest seasonal-tilt-adjustment-test
  (testing "Seasonal tilt adjustments"
    (let [lat 40.0]
      (is (approx= 25.0 (a/seasonal-tilt-adjustment lat :summer) 0.1)
          "Summer: latitude - 15°")

      (is (approx= 55.0 (a/seasonal-tilt-adjustment lat :winter) 0.1)
          "Winter: latitude + 15°")

      (is (approx= 40.0 (a/seasonal-tilt-adjustment lat :spring) 0.1)
          "Spring: latitude")

      (is (approx= 40.0 (a/seasonal-tilt-adjustment lat :fall) 0.1)
          "Fall: latitude"))))

(deftest example-calculation-test
  (testing "Example calculation runs without errors"
    (let [result (a/example-calculation)]
      (is (map? result) "Returns a map")
      (is (contains? result :solar-position) "Contains solar position")
      (is (contains? result :single-axis-rotation) "Contains single-axis rotation")
      (is (contains? result :dual-axis) "Contains dual-axis angles")
      (is (contains? result :fixed-optimal-tilt) "Contains fixed tilt"))))

(deftest hour-angle-properties-test
  (testing "Hour angle properties"
    ;; At solar noon (LST = 12), hour angle = 0
    (is (approx= 0.0 (a/hour-angle 12.0) 0.01))

    ;; Morning (LST < 12), hour angle negative
    (is (< (a/hour-angle 10.0) 0.0) "Morning hour angle negative")

    ;; Afternoon (LST > 12), hour angle positive
    (is (> (a/hour-angle 14.0) 0.0) "Afternoon hour angle positive")

    ;; Each hour = 15 degrees
    (is (approx= 15.0 (a/hour-angle 13.0) 0.01) "1 PM = +15°")
    (is (approx= -15.0 (a/hour-angle 11.0) 0.01) "11 AM = -15°")
    (is (approx= 45.0 (a/hour-angle 15.0) 0.01) "3 PM = +45°")))

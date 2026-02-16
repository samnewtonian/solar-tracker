(ns com.kardashevtypev.solar.angles-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.test.alpha :as stest]
            [com.kardashevtypev.solar.angles :as a]
            [com.kardashevtypev.solar.angles.spec])
  (:import [java.time ZonedDateTime ZoneOffset]))

;; Enable spec instrumentation for tests
(stest/instrument)

(defn approx=
  "Test if two numbers are approximately equal within tolerance."
  ([a b] (approx= a b 0.1))
  ([a b tolerance]
   (< (abs (- a b)) tolerance)))

(defn- zdt
  "Construct a ZonedDateTime from year, month, day, hour, minute, and UTC offset hours."
  [yr mo dy hr mn offset-hours]
  (ZonedDateTime/of yr mo dy hr mn 0 0 (ZoneOffset/ofHours offset-hours)))

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
    (let [pos (a/solar-position 39.8 -89.6 (zdt 2026 3 21 12 0 -6))]

      (is (= 80 (:day-of-year pos)) "Day of year")

      (is (approx= 0.0 (:declination pos) 1.0)
          "Declination near 0° at equinox")

      (is (approx= -7.5 (:equation-of-time pos) 2.0)
          "Equation of time around -7.5 minutes in mid-March")

      (is (approx= 40.0 (:zenith pos) 2.0)
          "Zenith angle around 40° at Springfield latitude")

      (is (approx= 50.0 (:altitude pos) 2.0)
          "Altitude around 50° (complement of zenith)")

      (is (<= 174.0 (:azimuth pos) 185.0)
          "Azimuth near 179-180° (nearly due south)"))))

(deftest solar-position-summer-solstice-test
  (testing "Solar position at summer solstice"
    (let [pos (a/solar-position 39.8 -89.6 (zdt 2026 6 21 12 0 -6))]

      (is (approx= 23.45 (:declination pos) 1.0)
          "Declination near +23.45° at summer solstice")

      ;; At summer solstice, sun is higher in sky at noon
      (is (< (:zenith pos) 40.0)
          "Zenith angle less than equinox (sun higher)")

      (is (> (:altitude pos) 50.0)
          "Altitude greater than equinox (sun higher)"))))

(deftest solar-position-winter-solstice-test
  (testing "Solar position at winter solstice"
    (let [pos (a/solar-position 39.8 -89.6 (zdt 2026 12 21 12 0 -6))]

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
    (let [pos-noon (a/solar-position 39.8 -89.6 (zdt 2026 3 21 12 0 -6))
          tilt-noon (a/single-axis-tilt pos-noon 39.8)]
      (is (approx= 0.0 tilt-noon 5.0)
          "Near-zero rotation at solar noon"))

    ;; Morning/afternoon should have non-zero rotation
    (let [pos-morning (a/solar-position 39.8 -89.6 (zdt 2026 3 21 9 0 -6))
          tilt-morning (a/single-axis-tilt pos-morning 39.8)]
      (is (< tilt-morning 0.0)
          "Negative rotation in morning (tilted east)"))

    (let [pos-afternoon (a/solar-position 39.8 -89.6 (zdt 2026 3 21 15 0 -6))
          tilt-afternoon (a/single-axis-tilt pos-afternoon 39.8)]
      (is (> tilt-afternoon 0.0)
          "Positive rotation in afternoon (tilted west)"))))

(deftest dual-axis-angles-test
  (testing "Dual-axis tracker angles"
    (let [pos (a/solar-position 39.8 -89.6 (zdt 2026 3 21 12 0 -6))
          {:keys [tilt panel-azimuth]} (a/dual-axis-angles pos)]

      (is (approx= (:zenith pos) tilt 0.01)
          "Tilt equals zenith angle")

      ;; Panel azimuth should be opposite of solar azimuth (face the sun)
      ;; Solar azimuth ~179° means panel should face ~359° (nearly north, facing south sun)
      (is (or (<= 354.0 panel-azimuth 360.0)
              (<= 0.0 panel-azimuth 5.0))
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

;;; ============================================================
;;; Conversion Utility Tests
;;; ============================================================

(deftest deg-rad-roundtrip-test
  (testing "deg->rad and rad->deg are inverses"
    (doseq [deg [0.0 45.0 90.0 180.0 270.0 360.0 -45.0 -180.0 123.456]]
      (is (approx= deg (a/rad->deg (a/deg->rad deg)) 1e-10)
          (str "Roundtrip for " deg "°"))))

  (testing "Known conversions"
    (is (approx= Math/PI (a/deg->rad 180.0) 1e-10))
    (is (approx= (/ Math/PI 2) (a/deg->rad 90.0) 1e-10))
    (is (approx= 0.0 (a/deg->rad 0.0) 1e-10))
    (is (approx= 180.0 (a/rad->deg Math/PI) 1e-10))))

;;; ============================================================
;;; Edge Case Tests
;;; ============================================================

(deftest normalize-angle-edge-cases-test
  (testing "Large positive and negative angles"
    (is (approx= 0.0 (a/normalize-angle 720.0)) "Two full rotations")
    (is (approx= 90.0 (a/normalize-angle 810.0)) "810° = 2×360 + 90")
    (is (approx= 0.0 (a/normalize-angle -720.0)) "Negative two full rotations")
    (is (approx= 270.0 (a/normalize-angle -450.0)) "-450° = -360 - 90 -> 270"))

  (testing "Small angles near zero"
    (is (approx= 0.001 (a/normalize-angle 0.001) 1e-6))
    (is (approx= 359.999 (a/normalize-angle -0.001) 1e-6))))

(deftest day-of-year-edge-cases-test
  (testing "First day of each month (non-leap year 2026)"
    (let [expected [1 32 60 91 121 152 182 213 244 274 305 335]]
      (doseq [[month exp] (map vector (range 1 13) expected)]
        (is (= exp (a/day-of-year 2026 month 1))
            (str "First day of month " month)))))

  (testing "First day of each month (leap year 2024)"
    (let [expected [1 32 61 92 122 153 183 214 245 275 306 336]]
      (doseq [[month exp] (map vector (range 1 13) expected)]
        (is (= exp (a/day-of-year 2024 month 1))
            (str "First day of month " month " (leap year)"))))))

(deftest equator-solar-noon-equinox-test
  (testing "Sun directly overhead at equator on equinox at solar noon"
    ;; At the equator (0°N) on the equinox, the sun should be nearly overhead
    ;; at solar noon. Using longitude 0° with UTC for simplicity.
    (let [pos (a/solar-position 0.0 0.0 (zdt 2026 3 21 12 0 0))]
      (is (approx= 0.0 (:declination pos) 1.0)
          "Declination near 0° at equinox")
      (is (< (:zenith pos) 5.0)
          "Zenith near 0° (sun nearly overhead)")
      (is (> (:altitude pos) 85.0)
          "Altitude near 90° (sun nearly overhead)"))))

(deftest polar-latitude-test
  (testing "High-latitude behaviour near summer solstice"
    ;; At 70°N on summer solstice around noon, sun should be above horizon
    (let [pos (a/solar-position 70.0 15.0 (zdt 2026 6 21 12 0 1))]
      (is (> (:altitude pos) 0.0)
          "Sun above horizon at 70°N on summer solstice")
      (is (< (:zenith pos) 90.0)
          "Zenith below 90° (sun visible)")))

  (testing "High-latitude behaviour near winter solstice"
    ;; At 70°N on winter solstice around noon, sun should be very low or below horizon
    (let [pos (a/solar-position 70.0 15.0 (zdt 2026 12 21 12 0 1))]
      (is (> (:zenith pos) 85.0)
          "Zenith very high at 70°N winter solstice (sun barely rises)"))))

(deftest southern-hemisphere-test
  (testing "Southern hemisphere seasons are reversed"
    ;; Sydney, Australia: 33.9°S, 151.2°E, UTC+10
    (let [pos-jun (a/solar-position -33.9 151.2 (zdt 2026 6 21 12 0 10))
          pos-dec (a/solar-position -33.9 151.2 (zdt 2026 12 21 12 0 10))]
      ;; June = winter in south, December = summer in south
      (is (> (:zenith pos-jun) (:zenith pos-dec))
          "Sun lower in June (winter) than December (summer) in southern hemisphere")
      (is (< (:altitude pos-jun) (:altitude pos-dec))
          "Altitude lower in winter than summer"))))

(deftest midnight-position-test
  (testing "Sun below horizon at midnight"
    ;; Springfield at midnight on equinox
    (let [pos (a/solar-position 39.8 -89.6 (zdt 2026 3 21 0 0 -6))]
      (is (< (:altitude pos) 0.0) "Sun below horizon at midnight")
      (is (> (:zenith pos) 90.0) "Zenith > 90° when sun is below horizon"))))

;;; ============================================================
;;; Invariant / Property Tests
;;; ============================================================

(deftest zenith-altitude-complement-test
  (testing "Zenith + altitude = 90 for various inputs"
    (doseq [[lat lon yr mo dy hr mn offset]
            [[39.8 -89.6 2026 3 21 12 0 -6]
             [0.0 0.0 2026 6 21 12 0 0]
             [-33.9 151.2 2026 12 21 15 30 10]
             [51.5 -0.1 2026 9 22 8 0 0]
             [70.0 25.0 2026 6 21 18 0 2]]]
      (let [pos (a/solar-position lat lon (zdt yr mo dy hr mn offset))]
        (is (approx= 90.0 (+ (:zenith pos) (:altitude pos)) 1e-10)
            (str "zenith + altitude = 90 for lat=" lat " lon=" lon))))))

(deftest azimuth-always-normalized-test
  (testing "Azimuth always in [0, 360) for various locations and times"
    (doseq [[lat lon yr mo dy hr mn offset]
            [[39.8 -89.6 2026 1 15 8 0 -6]
             [39.8 -89.6 2026 1 15 16 0 -6]
             [39.8 -89.6 2026 7 15 6 0 -6]
             [39.8 -89.6 2026 7 15 20 0 -6]
             [-45.0 170.0 2026 3 21 12 0 12]
             [60.0 10.0 2026 6 21 3 0 1]
             [0.0 0.0 2026 9 22 12 0 0]]]
      (let [pos (a/solar-position lat lon (zdt yr mo dy hr mn offset))]
        (is (<= 0.0 (:azimuth pos)) (str "Azimuth >= 0 for " [lat lon hr]))
        (is (< (:azimuth pos) 360.0) (str "Azimuth < 360 for " [lat lon hr]))))))

(deftest declination-bounded-test
  (testing "Declination always within [-23.45, +23.45] for all days"
    (doseq [n (range 1 366)]
      (let [decl (a/solar-declination n)]
        (is (<= -23.45 decl 23.45)
            (str "Declination out of bounds for day " n))))))

(deftest equation-of-time-bounded-test
  (testing "Equation of time within [-15, +17] minutes for all days"
    (doseq [n (range 1 366)]
      (let [eot (a/equation-of-time n)]
        (is (<= -15.0 eot 17.0)
            (str "EoT out of expected range for day " n ": " eot))))))

(deftest zenith-non-negative-test
  (testing "Zenith angle is always non-negative"
    (doseq [[lat decl ha] [[0.0 0.0 0.0]
                           [45.0 23.45 0.0]
                           [-45.0 -23.45 0.0]
                           [0.0 23.45 90.0]
                           [89.0 23.45 0.0]
                           [-89.0 -23.45 0.0]]]
      (let [z (a/solar-zenith-angle lat decl ha)]
        (is (<= 0.0 z 180.0)
            (str "Zenith out of [0,180] for lat=" lat " decl=" decl " ha=" ha))))))

(deftest dual-axis-panel-azimuth-normalized-test
  (testing "Dual-axis panel azimuth always in [0, 360)"
    (doseq [solar-az [0.0 45.0 90.0 135.0 180.0 225.0 270.0 315.0 359.9]]
      (let [{:keys [panel-azimuth]} (a/dual-axis-angles {:zenith 30.0 :azimuth solar-az})]
        (is (<= 0.0 panel-azimuth) (str "panel-azimuth >= 0 for solar-az=" solar-az))
        (is (< panel-azimuth 360.0) (str "panel-azimuth < 360 for solar-az=" solar-az))))))

;;; ============================================================
;;; Cross-Location Comparison Tests
;;; ============================================================

(deftest multiple-cities-noon-equinox-test
  (testing "Solar position at noon on equinox for various cities"
    ;; At solar noon on equinox, zenith should approximately equal |latitude|
    (let [cities [{:name "London" :lat 51.5 :lon -0.1 :offset 0}
                  {:name "Tokyo" :lat 35.7 :lon 139.7 :offset 9}
                  {:name "Cape Town" :lat -33.9 :lon 18.4 :offset 2}
                  {:name "Quito" :lat -0.2 :lon -78.5 :offset -5}]]
      (doseq [{:keys [name lat lon offset]} cities]
        (let [pos (a/solar-position lat lon (zdt 2026 3 21 12 0 offset))]
          ;; zenith ≈ |latitude| at equinox noon (within a few degrees due to
          ;; EoT and longitude offset from std meridian)
          (is (approx= (abs lat) (:zenith pos) 8.0)
              (str name ": zenith ≈ |latitude| at equinox noon")))))))

(deftest morning-afternoon-symmetry-test
  (testing "Morning and afternoon positions are roughly symmetric around noon"
    ;; At equinox, 3 hours before and after solar noon should give
    ;; similar zenith but mirrored azimuth
    (let [pos-9am (a/solar-position 39.8 -89.6 (zdt 2026 3 21 9 0 -6))
          pos-3pm (a/solar-position 39.8 -89.6 (zdt 2026 3 21 15 0 -6))]
      (is (approx= (:zenith pos-9am) (:zenith pos-3pm) 5.0)
          "Zenith roughly symmetric 3h before/after noon")
      ;; Azimuth should be roughly mirrored around 180° (south)
      ;; Morning: < 180° (east of south), Afternoon: > 180° (west of south)
      (is (< (:azimuth pos-9am) 180.0) "Morning azimuth east of south")
      (is (> (:azimuth pos-3pm) 180.0) "Afternoon azimuth west of south"))))

;;; ============================================================
;;; Panel Angle Edge Cases
;;; ============================================================

(deftest fixed-tilt-symmetry-test
  (testing "Fixed tilt is same for equal north/south latitudes"
    (doseq [lat [10.0 25.0 40.0 55.0 70.0 85.0]]
      (is (approx= (a/optimal-fixed-tilt lat)
                    (a/optimal-fixed-tilt (- lat))
                    1e-10)
          (str "Symmetric for ±" lat "°"))))

  (testing "Fixed tilt increases with latitude"
    (let [tilts (map a/optimal-fixed-tilt [0.0 15.0 30.0 45.0 60.0 75.0 90.0])]
      (is (= tilts (sort tilts))
          "Tilt monotonically increases with latitude"))))

(deftest seasonal-tilt-ordering-test
  (testing "Summer < spring/fall < winter tilt for northern hemisphere"
    (let [lat 40.0]
      (is (< (a/seasonal-tilt-adjustment lat :summer)
             (a/seasonal-tilt-adjustment lat :spring)
             (a/seasonal-tilt-adjustment lat :winter))
          "summer < spring/fall < winter")))

  (testing "Seasonal tilt at equator"
    (is (approx= -15.0 (a/seasonal-tilt-adjustment 0.0 :summer) 0.01)
        "Equator summer tilt = -15°")
    (is (approx= 15.0 (a/seasonal-tilt-adjustment 0.0 :winter) 0.01)
        "Equator winter tilt = +15°")
    (is (approx= 0.0 (a/seasonal-tilt-adjustment 0.0 :spring) 0.01)
        "Equator spring tilt = 0°")))

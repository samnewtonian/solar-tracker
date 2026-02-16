(ns solar.lookup-table-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.alpha :as s]
            [solar.lookup-table :as lt]
            [solar.lookup-table.spec :as lts]
            [solar.angles :as angles]))

(defn approx=
  "Test if two numbers are approximately equal within tolerance."
  ([a b] (approx= a b 0.1))
  ([a b tolerance]
   (< (abs (- a b)) tolerance)))

;;; ============================================================
;;; Config Validation
;;; ============================================================

(deftest config-validation-test
  (testing "Default config conforms to spec"
    (is (s/valid? ::lts/config lt/default-config)
        (s/explain-str ::lts/config lt/default-config))))

;;; ============================================================
;;; Time Utilities
;;; ============================================================

(deftest minutes-time-roundtrip-test
  (testing "minutes->time and time->minutes roundtrip"
    (doseq [m [0 1 59 60 61 120 719 720 721 1439]]
      (is (= m (lt/time->minutes (lt/minutes->time m)))
          (str "Roundtrip for " m " minutes"))))

  (testing "Known conversions"
    (is (= [0 0] (lt/minutes->time 0)) "Midnight")
    (is (= [12 0] (lt/minutes->time 720)) "Noon")
    (is (= [23 59] (lt/minutes->time 1439)) "Last minute")
    (is (= [6 30] (lt/minutes->time 390)) "6:30 AM")))

(deftest intervals-per-day-test
  (testing "Intervals per day calculation"
    (is (= 288 (lt/intervals-per-day 5)) "5-minute intervals")
    (is (= 96 (lt/intervals-per-day 15)) "15-minute intervals")
    (is (= 48 (lt/intervals-per-day 30)) "30-minute intervals")
    (is (= 1440 (lt/intervals-per-day 1)) "1-minute intervals")))

;;; ============================================================
;;; doy->month-day
;;; ============================================================

(deftest doy-month-day-test
  (testing "Known dates roundtrip through day-of-year -> doy->month-day"
    (doseq [[year month day] [[2026 1 1] [2026 3 21] [2026 6 21]
                               [2026 12 31] [2024 2 29] [2024 3 1]
                               [2026 7 4] [2026 11 15]]]
      (let [doy (angles/day-of-year year month day)
            [m d] (lt/doy->month-day year doy)]
        (is (= [month day] [m d])
            (str "Roundtrip for " year "-" month "-" day " (doy=" doy ")")))))

  (testing "Boundary days"
    (is (= [1 1] (lt/doy->month-day 2026 1)) "Day 1 = Jan 1")
    (is (= [12 31] (lt/doy->month-day 2026 365)) "Day 365 = Dec 31")
    (is (= [12 31] (lt/doy->month-day 2024 366)) "Day 366 = Dec 31 (leap)")))

;;; ============================================================
;;; Sunrise/Sunset Estimation
;;; ============================================================

(deftest sunrise-sunset-estimation-test
  (testing "Equinox ~12h daylight at mid-latitudes"
    (let [{:keys [sunrise sunset]} (lt/estimate-sunrise-sunset 39.8 80)]
      (is (approx= 720.0 (/ (+ sunrise sunset) 2.0) 30.0)
          "Midpoint near noon")
      (is (approx= 720.0 (- sunset sunrise) 60.0)
          "About 12 hours of daylight at equinox")))

  (testing "Summer solstice has longer days than equinox"
    (let [equinox (lt/estimate-sunrise-sunset 39.8 80)
          solstice (lt/estimate-sunrise-sunset 39.8 172)]
      (is (> (- (:sunset solstice) (:sunrise solstice))
             (- (:sunset equinox) (:sunrise equinox)))
          "Summer has longer days")))

  (testing "Winter solstice has shorter days than equinox"
    (let [equinox (lt/estimate-sunrise-sunset 39.8 80)
          winter (lt/estimate-sunrise-sunset 39.8 355)]
      (is (< (- (:sunset winter) (:sunrise winter))
             (- (:sunset equinox) (:sunrise equinox)))
          "Winter has shorter days")))

  (testing "Polar day (high latitude summer)"
    (let [{:keys [sunrise sunset]} (lt/estimate-sunrise-sunset 80.0 172)]
      (is (= 0 sunrise) "Sunrise at 0 for polar day")
      (is (= 1440 sunset) "Sunset at 1440 for polar day")))

  (testing "Polar night (high latitude winter)"
    (let [{:keys [sunrise sunset]} (lt/estimate-sunrise-sunset 80.0 355)]
      (is (= sunrise sunset) "No daylight in polar night"))))

;;; ============================================================
;;; Single-Axis Generation
;;; ============================================================

(deftest single-axis-one-day-test
  (testing "Generate single-axis table for one day"
    (let [config (assoc lt/default-config :interval-minutes 15)
          table (lt/generate-single-axis-table config)
          day-80 (nth (:days table) 79)] ;; day 80 = equinox
      (is (= 80 (:day-of-year day-80)))
      (is (seq (:entries day-80)) "Has entries")

      (testing "Entries have required keys"
        (doseq [entry (:entries day-80)]
          (is (contains? entry :minutes) "Has :minutes")
          (is (contains? entry :rotation) "Has :rotation")))

      (testing "Rotation near 0 at noon"
        (let [noon-entry (first (filter #(= 720 (:minutes %)) (:entries day-80)))]
          (when noon-entry
            (is (some? (:rotation noon-entry)) "Noon should be daylight")
            (is (approx= 0.0 (:rotation noon-entry) 5.0)
                "Rotation near 0 at solar noon"))))

      (testing "Morning negative, afternoon positive rotation"
        (let [entries (:entries day-80)
              morning (first (filter #(and (:rotation %) (< (:minutes %) 600)) entries))
              afternoon (first (filter #(and (:rotation %) (> (:minutes %) 840)) entries))]
          (when morning
            (is (< (:rotation morning) 0.0) "Morning rotation is negative"))
          (when afternoon
            (is (> (:rotation afternoon) 0.0) "Afternoon rotation is positive")))))))

;;; ============================================================
;;; Dual-Axis Generation
;;; ============================================================

(deftest dual-axis-one-day-test
  (testing "Generate dual-axis table for one day"
    (let [config (assoc lt/default-config :interval-minutes 15)
          table (lt/generate-dual-axis-table config)
          day-80 (nth (:days table) 79)]
      (is (= 80 (:day-of-year day-80)))
      (is (seq (:entries day-80)) "Has entries")

      (testing "Entries have required keys"
        (doseq [entry (:entries day-80)]
          (is (contains? entry :minutes) "Has :minutes")
          (is (contains? entry :tilt) "Has :tilt")
          (is (contains? entry :panel-azimuth) "Has :panel-azimuth")))

      (testing "Tilt matches zenith at noon"
        (let [noon-entry (first (filter #(= 720 (:minutes %)) (:entries day-80)))]
          (when noon-entry
            (is (some? (:tilt noon-entry)) "Noon tilt present")
            ;; Tilt = zenith for dual-axis; at Springfield equinox noon ~40°
            (is (approx= 40.0 (:tilt noon-entry) 5.0)
                "Tilt near 40° at equinox noon")))))))

;;; ============================================================
;;; Full Year Generation
;;; ============================================================

(deftest full-year-generation-test
  (testing "Generate 365-day table at 30-min intervals"
    (let [config (assoc lt/default-config :interval-minutes 30)
          table (lt/generate-single-axis-table config)]
      (is (= 365 (count (:days table))) "365 days")
      (is (pos? (:total-entries (:metadata table))) "Has entries")
      (is (pos? (:storage-estimate-kb (:metadata table))) "Has storage estimate")

      (testing "Every day has entries"
        (doseq [day (:days table)]
          (is (seq (:entries day))
              (str "Day " (:day-of-year day) " has entries"))))

      (testing "Entry structure consistent"
        (let [sample-day (nth (:days table) 171)] ;; summer solstice
          (doseq [entry (:entries sample-day)]
            (is (int? (:minutes entry)))
            (is (or (nil? (:rotation entry))
                    (number? (:rotation entry))))))))))

;;; ============================================================
;;; Lookup with Interpolation
;;; ============================================================

(deftest lookup-single-axis-test
  (testing "Lookup at exact interval boundary"
    (let [config (assoc lt/default-config :interval-minutes 15)
          table (lt/generate-single-axis-table config)
          ;; Look up at noon on equinox (exact boundary)
          result (lt/lookup-single-axis table 80 720)]
      (is (some? result) "Found result")
      (is (= 720 (:minutes result)))
      (is (some? (:rotation result)) "Has rotation")))

  (testing "Lookup between intervals (interpolated)"
    (let [config (assoc lt/default-config :interval-minutes 15)
          table (lt/generate-single-axis-table config)
          ;; Look up at 12:07 (between 12:00 and 12:15)
          result (lt/lookup-single-axis table 80 727)
          ;; Also get the boundary values
          at-720 (lt/lookup-single-axis table 80 720)
          at-735 (lt/lookup-single-axis table 80 735)]
      (is (some? result) "Found interpolated result")
      (is (= 727 (:minutes result)))
      (when (and (:rotation at-720) (:rotation at-735) (:rotation result))
        (let [r720 (:rotation at-720)
              r735 (:rotation at-735)
              r727 (:rotation result)
              lo (min r720 r735)
              hi (max r720 r735)]
          (is (and (>= r727 (- lo 0.01))
                   (<= r727 (+ hi 0.01)))
              "Interpolated value between neighbors"))))))

(deftest lookup-dual-axis-test
  (testing "Lookup dual-axis at exact boundary"
    (let [config (assoc lt/default-config :interval-minutes 15)
          table (lt/generate-dual-axis-table config)
          result (lt/lookup-dual-axis table 80 720)]
      (is (some? result) "Found result")
      (is (some? (:tilt result)) "Has tilt")
      (is (some? (:panel-azimuth result)) "Has panel-azimuth")))

  (testing "Lookup dual-axis interpolated"
    (let [config (assoc lt/default-config :interval-minutes 15)
          table (lt/generate-dual-axis-table config)
          result (lt/lookup-dual-axis table 80 727)]
      (is (some? result) "Found interpolated result")
      (is (some? (:tilt result)) "Has tilt")
      (is (some? (:panel-azimuth result)) "Has panel-azimuth"))))

;;; ============================================================
;;; Lookup Outside Range
;;; ============================================================

(deftest lookup-outside-range-test
  (testing "Returns nil for nighttime"
    (let [config (assoc lt/default-config :interval-minutes 15)
          table (lt/generate-single-axis-table config)]
      (is (nil? (lt/lookup-single-axis table 80 0))
          "Midnight returns nil")
      (is (nil? (lt/lookup-single-axis table 80 120))
          "2 AM returns nil"))))

;;; ============================================================
;;; Compact Export
;;; ============================================================

(deftest compact-export-test
  (testing "Single-axis compact export"
    (let [config (assoc lt/default-config :interval-minutes 30)
          table (lt/generate-single-axis-table config)
          compact (lt/table->compact table)]
      (is (= 365 (count compact)) "365 days")
      (is (vector? compact) "Is a vector")
      (is (vector? (first compact)) "Inner is a vector")
      ;; Each entry should be a number or nil
      (doseq [day-vals compact]
        (doseq [v day-vals]
          (is (or (nil? v) (number? v))
              "Values are numbers or nil")))))

  (testing "Dual-axis compact export"
    (let [config (assoc lt/default-config :interval-minutes 30)
          table (lt/generate-dual-axis-table config)
          compact (lt/table->compact table)]
      (is (= 365 (count compact)) "365 days")
      (is (vector? compact) "Is a vector")
      ;; Each entry should be a [tilt az] pair
      (let [sample (first (filter some? (first compact)))]
        (is (vector? sample) "Entry is a vector")
        (is (= 2 (count sample)) "Entry has 2 elements")))))

;;; ============================================================
;;; Interpolate-Angle Wraparound
;;; ============================================================

(deftest interpolate-angle-wraparound-test
  (testing "Normal interpolation (no wraparound)"
    (is (approx= 45.0 (lt/interpolate-angle 0.0 90.0 0.5) 0.01)
        "Midpoint of 0-90")
    (is (approx= 0.0 (lt/interpolate-angle 0.0 90.0 0.0) 0.01)
        "Start")
    (is (approx= 90.0 (lt/interpolate-angle 0.0 90.0 1.0) 0.01)
        "End"))

  (testing "Wraparound: 350° to 10° goes through 0°"
    (let [result (lt/interpolate-angle 350.0 10.0 0.5)]
      (is (approx= 0.0 result 0.01)
          "Midpoint of 350-10 wraps through 0")))

  (testing "Wraparound: 10° to 350° goes backward through 0°"
    (let [result (lt/interpolate-angle 10.0 350.0 0.5)]
      (is (approx= 0.0 result 0.01)
          "Midpoint of 10-350 wraps through 0 (shorter arc)")))

  (testing "Returns nil when either input is nil"
    (is (nil? (lt/interpolate-angle nil 10.0 0.5)))
    (is (nil? (lt/interpolate-angle 10.0 nil 0.5)))))

(ns solar.lookup-table
  "Precomputed solar angle lookup table generation and access.
   Generates daylight-only tables for single-axis and dual-axis trackers
   with configurable interval and sunrise/sunset buffers."
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
   :sunrise-buffer-minutes 30
   :sunset-buffer-minutes 30})

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
;;; Date Utilities
;;; ============================================================

(defn doy->month-day
  "Convert day-of-year to [month day] for a given year.
   Walks the cumulative days-in-months vector."
  [year doy]
  (let [leap? (or (zero? (mod year 400))
                  (and (zero? (mod year 4))
                       (not (zero? (mod year 100)))))
        days-in-months [31 (if leap? 29 28) 31 30 31 30 31 31 30 31 30 31]]
    (loop [month 1
           remaining doy]
      (let [dim (nth days-in-months (dec month))]
        (if (<= remaining dim)
          [month remaining]
          (recur (inc month) (- remaining dim)))))))

;;; ============================================================
;;; Sunrise/Sunset Estimation
;;; ============================================================

(defn estimate-sunrise-sunset
  "Estimate sunrise and sunset times for a given day.
   Returns {:sunrise minutes, :sunset minutes} from midnight.

   Uses the hour angle at sunrise/sunset formula:
   cos(h) = -tan(lat) * tan(decl)"
  [latitude day-of-year]
  (let [lat-rad (angles/deg->rad latitude)
        decl (angles/solar-declination day-of-year)
        decl-rad (angles/deg->rad decl)
        cos-h (* -1.0 (math/tan lat-rad) (math/tan decl-rad))]
    (cond
      ;; Polar night: sun never rises
      (>= cos-h 1.0)
      {:sunrise 720 :sunset 720}

      ;; Polar day: sun never sets
      (<= cos-h -1.0)
      {:sunrise 0 :sunset 1440}

      :else
      (let [h-deg (angles/rad->deg (math/acos cos-h))
            half-day-minutes (* (/ h-deg 15.0) 60.0)
            solar-noon-minutes 720]
        {:sunrise (long (- solar-noon-minutes half-day-minutes))
         :sunset (long (+ solar-noon-minutes half-day-minutes))}))))

;;; ============================================================
;;; Interpolation
;;; ============================================================

(defn interpolate-angle
  "Interpolate between two angles, handling 360 deg wraparound."
  [a1 a2 fraction]
  (when (and a1 a2)
    (let [diff (- a2 a1)
          adjusted-diff (cond
                          (> diff 180)  (- diff 360)
                          (< diff -180) (+ diff 360)
                          :else diff)]
      (mod (+ a1 (* adjusted-diff fraction)) 360.0))))

(defn- interpolate-linear
  "Simple linear interpolation between two values."
  [v1 v2 fraction]
  (when (and v1 v2)
    (+ v1 (* fraction (- v2 v1)))))

;;; ============================================================
;;; Single-Axis Table Generation
;;; ============================================================

(defn generate-single-axis-table
  "Generate a single-axis tracker lookup table.

   Returns:
   {:config config
    :days [{:day-of-year 1
            :sunrise-minutes ...
            :sunset-minutes ...
            :entries [{:minutes m :rotation angle-or-nil} ...]}
           ...]
    :metadata {:generated-at ... :total-entries ... :storage-estimate-kb ...}}"
  [config]
  (let [{:keys [interval-minutes latitude longitude std-meridian year
                sunrise-buffer-minutes sunset-buffer-minutes]} config
        days (vec
              (for [doy (range 1 366)]
                (let [{:keys [sunrise sunset]} (estimate-sunrise-sunset latitude doy)
                      start-minute (max 0 (- sunrise sunrise-buffer-minutes))
                      end-minute (min 1439 (+ sunset sunset-buffer-minutes))
                      [month day-of-month] (doy->month-day year doy)
                      entries (vec
                               (for [interval (range (intervals-per-day interval-minutes))
                                     :let [minutes (* interval interval-minutes)]
                                     :when (and (>= minutes start-minute)
                                                (<= minutes end-minute))]
                                 (let [[hour minute] (minutes->time minutes)
                                       pos (angles/solar-position latitude longitude year
                                                                   month day-of-month
                                                                   hour minute std-meridian)
                                       is-daylight? (and (>= minutes sunrise)
                                                         (<= minutes sunset))]
                                   {:minutes minutes
                                    :rotation (when is-daylight?
                                                (angles/single-axis-tilt pos latitude))})))]
                  {:day-of-year doy
                   :sunrise-minutes sunrise
                   :sunset-minutes sunset
                   :entries entries})))
        total-entries (reduce + (map (comp count :entries) days))
        ;; Single-axis: 1 float per entry + minutes index
        storage-kb (/ (* total-entries 4) 1024.0)]
    {:config config
     :days days
     :metadata {:generated-at (str (java.time.Instant/now))
                :total-entries total-entries
                :storage-estimate-kb storage-kb}}))

;;; ============================================================
;;; Dual-Axis Table Generation
;;; ============================================================

(defn generate-dual-axis-table
  "Generate a dual-axis tracker lookup table.

   Returns:
   {:config config
    :days [{:day-of-year 1
            :sunrise-minutes ...
            :sunset-minutes ...
            :entries [{:minutes m :tilt angle :panel-azimuth angle} ...]}
           ...]
    :metadata {:generated-at ... :total-entries ... :storage-estimate-kb ...}}"
  [config]
  (let [{:keys [interval-minutes latitude longitude std-meridian year
                sunrise-buffer-minutes sunset-buffer-minutes]} config
        days (vec
              (for [doy (range 1 366)]
                (let [{:keys [sunrise sunset]} (estimate-sunrise-sunset latitude doy)
                      start-minute (max 0 (- sunrise sunrise-buffer-minutes))
                      end-minute (min 1439 (+ sunset sunset-buffer-minutes))
                      [month day-of-month] (doy->month-day year doy)
                      entries (vec
                               (for [interval (range (intervals-per-day interval-minutes))
                                     :let [minutes (* interval interval-minutes)]
                                     :when (and (>= minutes start-minute)
                                                (<= minutes end-minute))]
                                 (let [[hour minute] (minutes->time minutes)
                                       pos (angles/solar-position latitude longitude year
                                                                   month day-of-month
                                                                   hour minute std-meridian)
                                       is-daylight? (and (>= minutes sunrise)
                                                         (<= minutes sunset))]
                                   (if is-daylight?
                                     (let [da (angles/dual-axis-angles pos)]
                                       {:minutes minutes
                                        :tilt (:tilt da)
                                        :panel-azimuth (:panel-azimuth da)})
                                     {:minutes minutes
                                      :tilt nil
                                      :panel-azimuth nil}))))]
                  {:day-of-year doy
                   :sunrise-minutes sunrise
                   :sunset-minutes sunset
                   :entries entries})))
        total-entries (reduce + (map (comp count :entries) days))
        ;; Dual-axis: 2 floats per entry
        storage-kb (/ (* total-entries 8) 1024.0)]
    {:config config
     :days days
     :metadata {:generated-at (str (java.time.Instant/now))
                :total-entries total-entries
                :storage-estimate-kb storage-kb}}))

;;; ============================================================
;;; Lookup Functions
;;; ============================================================

(defn lookup-single-axis
  "Look up single-axis rotation from table with linear interpolation.

   Returns interpolated {:minutes m :rotation angle} or nil if outside range."
  [table day-of-year minutes]
  (let [day-data (get (:days table) (dec day-of-year))
        entries (:entries day-data)]
    (when (seq entries)
      (let [first-entry (first entries)
            last-entry (peek entries)]
        (when (and (>= minutes (:minutes first-entry))
                   (<= minutes (:minutes last-entry)))
          (let [idx-before (last (keep-indexed
                                  (fn [i e] (when (<= (:minutes e) minutes) i))
                                  entries))
                entry-before (get entries idx-before)
                entry-after (get entries (inc idx-before))]
            (if (or (nil? entry-after)
                    (= minutes (:minutes entry-before)))
              {:minutes minutes :rotation (:rotation entry-before)}
              (let [t0 (:minutes entry-before)
                    t1 (:minutes entry-after)
                    fraction (/ (double (- minutes t0)) (- t1 t0))]
                {:minutes minutes
                 :rotation (interpolate-linear (:rotation entry-before)
                                               (:rotation entry-after)
                                               fraction)}))))))))

(defn lookup-dual-axis
  "Look up dual-axis angles from table with linear interpolation.

   Returns interpolated {:minutes m :tilt angle :panel-azimuth angle} or nil
   if outside range. Uses interpolate-angle for panel-azimuth to handle
   360 deg wraparound."
  [table day-of-year minutes]
  (let [day-data (get (:days table) (dec day-of-year))
        entries (:entries day-data)]
    (when (seq entries)
      (let [first-entry (first entries)
            last-entry (peek entries)]
        (when (and (>= minutes (:minutes first-entry))
                   (<= minutes (:minutes last-entry)))
          (let [idx-before (last (keep-indexed
                                  (fn [i e] (when (<= (:minutes e) minutes) i))
                                  entries))
                entry-before (get entries idx-before)
                entry-after (get entries (inc idx-before))]
            (if (or (nil? entry-after)
                    (= minutes (:minutes entry-before)))
              {:minutes minutes
               :tilt (:tilt entry-before)
               :panel-azimuth (:panel-azimuth entry-before)}
              (let [t0 (:minutes entry-before)
                    t1 (:minutes entry-after)
                    fraction (/ (double (- minutes t0)) (- t1 t0))]
                {:minutes minutes
                 :tilt (interpolate-linear (:tilt entry-before)
                                           (:tilt entry-after)
                                           fraction)
                 :panel-azimuth (interpolate-angle (:panel-azimuth entry-before)
                                                   (:panel-azimuth entry-after)
                                                   fraction)}))))))))

;;; ============================================================
;;; Compact Export
;;; ============================================================

(defn table->compact
  "Strip metadata and return nested vectors of angle values.

   For single-axis tables (entries have :rotation):
     [[rotation ...] ...]

   For dual-axis tables (entries have :tilt and :panel-azimuth):
     [[[tilt panel-azimuth] ...] ...]"
  [table]
  (let [sample-entry (first (:entries (first (:days table))))]
    (if (contains? sample-entry :rotation)
      ;; Single-axis
      (mapv (fn [day]
              (mapv :rotation (:entries day)))
            (:days table))
      ;; Dual-axis
      (mapv (fn [day]
              (mapv (fn [e] [(:tilt e) (:panel-azimuth e)])
                    (:entries day)))
            (:days table)))))

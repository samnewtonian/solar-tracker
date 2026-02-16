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
  (let [dim (angles/days-in-months year)]
    (loop [month 1
           remaining doy]
      (let [d (nth dim (dec month))]
        (if (<= remaining d)
          [month remaining]
          (recur (inc month) (- remaining d)))))))

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
;;; Table Generation
;;; ============================================================

(defn- compute-angles-fast
  "Compute solar angles with precomputed trig values for latitude and declination.
   Mirrors the formulas in angles/solar-zenith-angle and angles/solar-azimuth
   but avoids redundant deg->rad and sin/cos calls on constant values.
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
   at each UTC-minute interval within the daylight window (plus buffers),
   and calls entry-fn to produce each entry map.

   Tables are indexed by UTC minutes. Sunrise/sunset are estimated in local
   solar time, then shifted to UTC using the full correction (longitude + EoT).

   entry-fn: (fn [minutes angles is-daylight?]) -> entry map
     where angles is the map from compute-angles-fast
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
                      ;; Full correction in minutes (longitude + EoT) for UTC↔LST conversion
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
  (let [latitude (:latitude config)]
    (generate-table config
                    (fn [minutes pos is-daylight?]
                      {:minutes minutes
                       :rotation (when is-daylight?
                                   (angles/single-axis-tilt pos latitude))})
                    4)))

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
  (generate-table config
                  (fn [minutes pos is-daylight?]
                    (if is-daylight?
                      (let [da (angles/dual-axis-angles pos)]
                        {:minutes minutes
                         :tilt (:tilt da)
                         :panel-azimuth (:panel-azimuth da)})
                      {:minutes minutes
                       :tilt nil
                       :panel-azimuth nil}))
                  8))

;;; ============================================================
;;; Lookup Functions
;;; ============================================================

(defn- find-bracketing-entries
  "Find the two entries bracketing the given minutes value.
   Returns [entry-before entry-after fraction] or nil if outside range.
   Uses O(1) index computation from the regular interval spacing."
  [entries interval-minutes minutes]
  (when (seq entries)
    (let [first-minutes (:minutes (first entries))
          last-minutes (:minutes (peek entries))]
      (when (and (>= minutes first-minutes)
                 (<= minutes last-minutes))
        (let [idx-before (min (quot (- minutes first-minutes) interval-minutes)
                              (dec (count entries)))
              entry-before (get entries idx-before)
              entry-after (get entries (inc idx-before))
              t0 (:minutes entry-before)]
          (if (or (nil? entry-after) (= minutes t0))
            [entry-before nil 0.0]
            (let [t1 (:minutes entry-after)
                  fraction (/ (double (- minutes t0)) (- t1 t0))]
              [entry-before entry-after fraction])))))))

(defn lookup-single-axis
  "Look up single-axis rotation from table with linear interpolation.

   Returns interpolated {:minutes m :rotation angle} or nil if outside range."
  [table day-of-year minutes]
  (let [entries (:entries (get (:days table) (dec day-of-year)))
        interval-minutes (get-in table [:config :interval-minutes])]
    (when-let [[before after fraction] (find-bracketing-entries entries interval-minutes minutes)]
      (if (nil? after)
        {:minutes minutes :rotation (:rotation before)}
        {:minutes minutes
         :rotation (interpolate-linear (:rotation before)
                                       (:rotation after)
                                       fraction)}))))

(defn lookup-dual-axis
  "Look up dual-axis angles from table with linear interpolation.

   Returns interpolated {:minutes m :tilt angle :panel-azimuth angle} or nil
   if outside range. Uses interpolate-angle for panel-azimuth to handle
   360 deg wraparound."
  [table day-of-year minutes]
  (let [entries (:entries (get (:days table) (dec day-of-year)))
        interval-minutes (get-in table [:config :interval-minutes])]
    (when-let [[before after fraction] (find-bracketing-entries entries interval-minutes minutes)]
      (if (nil? after)
        {:minutes minutes
         :tilt (:tilt before)
         :panel-azimuth (:panel-azimuth before)}
        {:minutes minutes
         :tilt (interpolate-linear (:tilt before)
                                   (:tilt after)
                                   fraction)
         :panel-azimuth (interpolate-angle (:panel-azimuth before)
                                           (:panel-azimuth after)
                                           fraction)}))))

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

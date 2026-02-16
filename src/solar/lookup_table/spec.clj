(ns solar.lookup-table.spec
  "Specs for solar.lookup-table namespace."
  (:require [clojure.spec.alpha :as s]
            [solar.lookup-table :as lt]))

;;; ============================================================
;;; Config Specs
;;; ============================================================

(s/def ::interval-minutes
  (s/and pos-int? #(<= 1 % 1440)))

(s/def ::latitude
  (s/and number? #(<= -90 % 90)))

(s/def ::longitude
  (s/and number? #(<= -180 % 180)))

(s/def ::std-meridian
  (s/and number? #(<= -180 % 180)))

(s/def ::year
  pos-int?)

(s/def ::sunrise-buffer-minutes
  (s/and int? #(>= % 0)))

(s/def ::sunset-buffer-minutes
  (s/and int? #(>= % 0)))

(s/def ::config
  (s/keys :req-un [::interval-minutes
                   ::latitude
                   ::longitude
                   ::std-meridian
                   ::year
                   ::sunrise-buffer-minutes
                   ::sunset-buffer-minutes]))

;;; ============================================================
;;; Entry Specs
;;; ============================================================

(s/def ::minutes
  (s/and int? #(<= 0 % 1439)))

(s/def ::rotation
  (s/nilable (s/and number? #(Double/isFinite %))))

(s/def ::single-axis-entry
  (s/keys :req-un [::minutes ::rotation]))

(s/def ::tilt
  (s/nilable (s/and number? #(<= 0 % 180))))

(s/def ::panel-azimuth
  (s/nilable (s/and number? #(<= 0 % 360))))

(s/def ::dual-axis-entry
  (s/keys :req-un [::minutes ::tilt ::panel-azimuth]))

;;; ============================================================
;;; Day Specs
;;; ============================================================

(s/def ::day-of-year
  (s/int-in 1 367))

(s/def ::sunrise-minutes
  (s/and int? #(<= 0 % 1440)))

(s/def ::sunset-minutes
  (s/and int? #(<= 0 % 1440)))

(s/def ::single-axis-entries
  (s/coll-of ::single-axis-entry :kind vector?))

(s/def ::dual-axis-entries
  (s/coll-of ::dual-axis-entry :kind vector?))

(s/def ::single-axis-day
  (s/keys :req-un [::day-of-year ::sunrise-minutes ::sunset-minutes]
          :opt-un []))

(s/def ::dual-axis-day
  (s/keys :req-un [::day-of-year ::sunrise-minutes ::sunset-minutes]
          :opt-un []))

;;; ============================================================
;;; Table Specs
;;; ============================================================

(s/def ::total-entries
  pos-int?)

(s/def ::storage-estimate-kb
  (s/and number? pos?))

(s/def ::generated-at
  string?)

(s/def ::metadata
  (s/keys :req-un [::generated-at ::total-entries ::storage-estimate-kb]))

(s/def ::single-axis-days
  (s/coll-of ::single-axis-day :kind vector? :min-count 365 :max-count 365))

(s/def ::dual-axis-days
  (s/coll-of ::dual-axis-day :kind vector? :min-count 365 :max-count 365))

(s/def ::single-axis-table
  (s/keys :req-un [::config ::metadata]))

(s/def ::dual-axis-table
  (s/keys :req-un [::config ::metadata]))

;;; ============================================================
;;; Function Specs
;;; ============================================================

(s/fdef lt/minutes->time
  :args (s/cat :total-minutes (s/and int? #(<= 0 % 1439)))
  :ret (s/tuple (s/int-in 0 24) (s/int-in 0 60)))

(s/fdef lt/time->minutes
  :args (s/cat :time (s/tuple (s/int-in 0 24) (s/int-in 0 60)))
  :ret (s/and int? #(<= 0 % 1439)))

(s/fdef lt/intervals-per-day
  :args (s/cat :interval-minutes ::interval-minutes)
  :ret pos-int?)

(s/fdef lt/doy->month-day
  :args (s/cat :year ::year :doy ::day-of-year)
  :ret (s/tuple (s/int-in 1 13) (s/int-in 1 32)))

(s/fdef lt/estimate-sunrise-sunset
  :args (s/cat :latitude ::latitude :day-of-year ::day-of-year)
  :ret (s/keys :req-un [::sunrise-minutes ::sunset-minutes]))

(s/fdef lt/interpolate-angle
  :args (s/cat :a1 number? :a2 number? :fraction (s/and number? #(<= 0 % 1)))
  :ret number?)

(s/fdef lt/generate-single-axis-table
  :args (s/cat :config ::config)
  :ret ::single-axis-table)

(s/fdef lt/generate-dual-axis-table
  :args (s/cat :config ::config)
  :ret ::dual-axis-table)

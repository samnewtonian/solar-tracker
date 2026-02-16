(ns com.kardashevtypev.solar.angles.spec
  "Specs for com.kardashevtypev.solar.angles namespace inputs and outputs."
  (:require [clojure.spec.alpha :as s]
            [com.kardashevtypev.solar.angles :as angles]))

;;; ============================================================
;;; Input Specs
;;; ============================================================

(s/def ::latitude
  (s/and number? #(<= -90 % 90)))

(s/def ::longitude
  (s/and number? #(<= -180 % 180)))

(s/def ::datetime
  #(instance? java.time.ZonedDateTime %))

(s/def ::year
  pos-int?)

(s/def ::month
  (s/int-in 1 13))

(s/def ::day
  (s/int-in 1 32))

(s/def ::day-of-year
  (s/int-in 1 367))

(s/def ::degrees
  (s/and number? #(Double/isFinite %)))

(s/def ::season
  #{:summer :winter :spring :fall})

;;; ============================================================
;;; Output Specs
;;; ============================================================

(s/def ::zenith
  (s/and number? #(<= 0 % 180)))

(s/def ::altitude
  (s/and number? #(<= -90 % 90)))

(s/def ::azimuth
  (s/and number? #(<= 0 % 360)))

(s/def ::declination
  (s/and number? #(<= -23.45 % 23.45)))

(s/def ::hour-angle
  (s/and number? #(<= -180 % 180)))

(s/def ::equation-of-time
  (s/and number? #(<= -20 % 20)))

(s/def ::local-solar-time
  (s/and number? #(Double/isFinite %)))

(s/def ::solar-position-result
  (s/keys :req-un [::day-of-year
                   ::declination
                   ::equation-of-time
                   ::local-solar-time
                   ::hour-angle
                   ::zenith
                   ::altitude
                   ::azimuth]))

(s/def ::tilt
  (s/and number? #(<= 0 % 180)))

(s/def ::panel-azimuth
  (s/and number? #(<= 0 % 360)))

(s/def ::dual-axis-result
  (s/keys :req-un [::tilt ::panel-azimuth]))

;;; ============================================================
;;; Function Specs (fdef)
;;; ============================================================

(s/fdef angles/day-of-year
  :args (s/cat :year ::year :month ::month :day ::day)
  :ret ::day-of-year)

(s/fdef angles/solar-position
  :args (s/cat :latitude ::latitude
               :longitude ::longitude
               :datetime ::datetime)
  :ret ::solar-position-result)

(s/fdef angles/single-axis-tilt
  :args (s/cat :solar-pos (s/keys :req-un [::hour-angle])
               :latitude ::latitude)
  :ret ::degrees)

(s/fdef angles/dual-axis-angles
  :args (s/cat :solar-pos (s/keys :req-un [::zenith ::azimuth]))
  :ret ::dual-axis-result)

(s/fdef angles/optimal-fixed-tilt
  :args (s/cat :latitude ::latitude)
  :ret ::degrees)

(s/fdef angles/seasonal-tilt-adjustment
  :args (s/cat :latitude ::latitude :season ::season)
  :ret ::degrees)

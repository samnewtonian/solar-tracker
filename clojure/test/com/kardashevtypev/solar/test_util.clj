(ns com.kardashevtypev.solar.test-util)

(defn approx=
  "Test if two numbers are approximately equal within tolerance."
  ([a b] (approx= a b 0.1))
  ([a b tolerance]
   (< (abs (- a b)) tolerance)))

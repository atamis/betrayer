(ns brutus.util)

; The array of methods returned by .getDeclaredMethods seems always sorted.
(defn arity-min
  [fun]
  (->> fun
       class
       .getDeclaredMethods
       first
       .getParameterTypes
       alength))

(defn arity-max
  [fun]
  (->> fun
       class
       .getDeclaredMethods
       last
       .getParameterTypes
       alength))

(defn arity-all
  [fun]
  (->> fun
       class
       .getDeclaredMethods
       (map #(alength (.getParameterTypes %1)))))

(defn apply-flex-arity
  [fun args]
  (apply fun (take (arity-max fun) args)))

(defn make-flexible-fn
  [fun]
  (fn [& args] (apply-flex-arity fun args)))

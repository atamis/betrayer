(ns betrayer.util
  "Theoretically reusable utility functions.")

; The array of methods returned by .getDeclaredMethods seems always sorted.


(defn arity-min
  "Return the smallest arity a function can be invoked with."
  [fun]
  (->> fun
       class
       .getDeclaredMethods
       first
       .getParameterTypes
       alength))

(defn arity-max
  "Return the largest arity a function can be invoked with."
  [fun]
  (->> fun
       class
       .getDeclaredMethods
       last
       .getParameterTypes
       alength))

(defn arity-all
  "Returns an array of all the arities a function has"
  [fun]
  (->> fun
       class
       .getDeclaredMethods
       (map #(alength (.getParameterTypes %1)))))

(defn apply-flex-arity
  "Apply as many arguments as possible to this function, but let some arguments
  be ignored."
  [fun args]
  (apply fun (take (arity-max fun) args)))

(defn make-flexible-fn
  "Create a function that takes as many arguments as possible, but ignores extra
  arguments."
  [fun]
  (fn [& args] (apply-flex-arity fun args)))

(defn not-nil!
  "Assert that the argument is not nil, returning it in that case, or throwing a
  `java.lang.AssertionError` if it is, with an optional message. See `assert` for
  more info."
  ([x]
   (not-nil! x "Expected not nil, got nil"))
  ([x msg]
   (assert (some? x) msg)
   x))

(defprotocol IReference? (reference? [this]))

(extend-type java.lang.Object IReference? (reference? [this] false))
(extend-type nil IReference? (reference? [this] false))
(extend-type clojure.lang.Ref IReference? (reference? [this] true))
(extend-type clojure.lang.Agent IReference? (reference? [this] true))
(extend-type clojure.lang.Atom IReference? (reference? [this] true))


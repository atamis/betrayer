(ns betrayer.event
  (:require [betrayer.ecs :as ecs]))

(defprotocol Ireference? (reference? [this]))

(extend-type java.lang.Object Ireference? (reference? [this] false))
(extend-type nil Ireference? (reference? [this] false))
(extend-type clojure.lang.Ref Ireference? (reference? [this] true))
(extend-type clojure.lang.Agent Ireference? (reference? [this] true))

(defn ^:private get-event-atom
  [world]
  (:events (if (reference? world) @world world)))

(defn add-event-system
  [world]
  (assoc world :events (atom [])))

(defn drain-events
  [world]
  (let [[old-events _] (reset-vals! (get-event-atom world) [])]
    old-events))

(defn add-event-internal
  [world event]
  (swap! (get-event-atom world) conj event))

(defn add-event
  ([event] (add-event ecs/current-world-ref event))
  ([world event]
   (add-event-internal world event)))

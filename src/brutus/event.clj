(ns brutus.event
  (:require [brutus.ecs :as ecs]))

(defprotocol Ireference? (reference? [this]))

(extend-type java.lang.Object Ireference? (reference? [this] false))
(extend-type nil Ireference? (reference? [this] false))
(extend-type clojure.lang.Ref Ireference? (reference? [this] true))
(extend-type clojure.lang.Agent Ireference? (reference? [this] true))

(defn ^:private get-event-atom
  [system]
  (:events (if (reference? system) @system system)))

(defn add-event-system
  [system]
  (assoc system :events (atom [])))

(defn drain-events
  [system]
  (let [[old-events _] (reset-vals! (get-event-atom system) [])]
    old-events))

(defn add-event-internal
  [system event]
  (swap! (get-event-atom system) conj event))

(defn add-event
  ([event] (add-event ecs/current-sys-ref event))
  ([system event]
   (add-event-internal system event)))

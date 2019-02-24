(ns betrayer.event
  (:require [betrayer.ecs :as ecs]
            [betrayer.dynamic :as dynamic]
            [betrayer.system :as system]))

(defprotocol Ireference? (reference? [this]))

(extend-type java.lang.Object Ireference? (reference? [this] false))
(extend-type nil Ireference? (reference? [this] false))
(extend-type clojure.lang.Ref Ireference? (reference? [this] true))
(extend-type clojure.lang.Agent Ireference? (reference? [this] true))

(defn ^:private get-event-atom
  [world]
  (:events (if (reference? world) @world world)))

(defn drain-events
  [world]
  (let [[old-events _] (reset-vals! (get-event-atom world) [])]
    old-events))

(defn ^:private group-events
  [events]
  (group-by first events))

(defn ^:private deliver-events
  [dest source]
  (merge (into {} (map #(vector %1 []) (keys dest))) (select-keys source (keys dest)))
  )

(defn ^:private event-system-function
  [world delta]
  (let [events (group-events (drain-events world))
        mapping-fn
        (system/mapping-system :subscription
                               #(deliver-events %1 events))
        ]
    (mapping-fn world delta)))

(defn add-event-system
  [world]
  (-> world
      (assoc :events (atom []))
      (ecs/add-system event-system-function)
      ))

(defn add-event-internal
  [world event]
  (swap! (get-event-atom world) conj event))

(defn add-event
  ([event] (add-event dynamic/current-world-ref event))
  ([world event]
   (add-event-internal world event)))

(defn subscribe
  ([topic] (dosync (alter dynamic/current-world-ref subscribe dynamic/current-entity topic)))
  ([entity topic] (dosync (alter dynamic/current-world-ref subscribe entity topic)))
  ([world entity topic]
   (ecs/add-component
    world
    entity
    :subscription
    (merge-with into
                (or (ecs/get-component world entity :subscription) {})
                {topic []})
   )
  ))

(defn get-events
  ([topic] (get-events @dynamic/current-world-ref dynamic/current-entity topic))
  ([entity topic] (get-events @dynamic/current-world-ref entity topic))
  ([world entity topic]
   ((ecs/get-component world entity :subscription) topic)
   )
  )

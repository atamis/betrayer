(ns betrayer.event
  "A system built on top of ECS worlds to support event subscription and
  deliver. Events are added to a central store, and then copied to subscribed
  entities on world tick. These deliveries are ephemeral, and deleted every
  tick. This reacts poorly with throttled systems."
  (:require [betrayer.ecs :as ecs]
            [betrayer.dynamic :as dynamic]
            [betrayer.system :as system]
            [betrayer.util :as util]))

(defn ^:private get-event-atom
  [world]
  (:events (if (util/reference? world) @world world)))

(defn drain-events
  "Drain events from the world's central event store."
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
  "Add an event store and system to the world. The system delivers events, and
  should be exectued before other system functions."
  [world]
  (-> world
      (assoc :events (atom []))
      (ecs/add-system event-system-function)
      ))

(defn ^:private add-event-internal
  [world event]
  (swap! (get-event-atom world) conj event))

(defn add-event
  "Add an event to the world's event store."
  ([event] (add-event dynamic/current-world-ref event))
  ([world event]
   (add-event-internal world event)))

(defn subscribe
  "Subscribe an entity to an event topic. Subscribed entities get copies of all
  events on the topics they're subscribed to delivered by the event system
  function. Existing events are replaced rather than added to."
  ([topic] (alter dynamic/current-world-ref subscribe dynamic/current-entity topic))
  ([entity topic] (alter dynamic/current-world-ref subscribe entity topic))
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
  "Get the local events on a particular entity for a topic. These events are
  replaced each tick by the event system function, so `get-events` returns
  events published this tick on those topics. This function makes use of
  `betrayer.dynamic` vars to elide some parameters."
  ([topic] (get-events @dynamic/current-world-ref dynamic/current-entity topic))
  ([entity topic] (get-events @dynamic/current-world-ref entity topic))
  ([world entity topic]
   ((ecs/get-component world entity :subscription) topic)
   )
  )

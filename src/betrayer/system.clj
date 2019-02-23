(ns betrayer.system
  (:require [betrayer.ecs :refer :all]
            [betrayer.util :as util]
            [betrayer.dynamic :as dynamic]
            [clojure.math.numeric-tower :as m]
            ))

(defn iterating-system
  "Creates a system function from another function. When the system is invoked,
  fun gets invoked for every entity that has a component of the specified type.
  When fun is invoked, `with-world-context` is used to bind the current world,
  entity, and type, allowing for the shortened versions of world functions to be
  used."
  [type fun]
  (fn [world delta]
    (let [world-ref (ref world)]
      (doseq [entity (get-all-entities-with-component world type)]
        (let [local-entity entity
              local-type   type]
          (dynamic/with-world-context world-ref entity type
            (fun entity))))
      @world-ref)))

(defn add-iterating-system
  "Adds a new iterating system function to the world. See `iterating-system`."
  [world type fun]
  (add-system world (iterating-system type fun)))

(defn mapping-system
  "System mapping strictly over a single component. Function takes
  (fun component) or (fun component delta)."
  [type fun]
  (fn [world delta]
    (let [adapted-fun (util/make-flexible-fn fun)]
      (reduce
       (fn [world entity]
         (update-component world entity type adapted-fun delta))
       world
       (get-all-entities-with-component world type)))))

(defn add-singleton-ref
  "Adds a singleton entity that executes an `iterating-system` function. Internal
  data defaults to an empty map. Returns `[world entity]`, where `entity` is
  the ID of the singleton."
  ([world fun] (add-singleton-ref world {} fun))
  ([world data fun] (add-singleton-ref world (gensym) data fun))
  ([world type data fun]
   (let [entity    (create-entity)
         component data
         world    (-> world
                      (add-entity entity)
                      (add-component entity type component)
                      (add-iterating-system type fun))]
     [world entity])))

(defn add-singleton
  "Adds a singleton entity that executes an iterating system function. Internal
  data defaults to an empty map. Returns the new system."
  ([world fun] (add-singleton world {} fun))
  ([world data fun] (add-singleton world (gensym) data fun))
  ([world type data fun]
   (let [[world entity] (add-singleton-ref world type data fun)]
     world)))

(defn throttled-system
  "Same as `add-system-fn`, but will only execute the `system-fn` after the sum
  of the delta values exceeds `threshold`."
  [threshold fun]
  (let [atom (atom 0)]
    (fn [world delta]
      (swap! atom + delta)
      (loop [world world]
        (if (< @atom threshold)
          world
          (do
            (swap! atom - threshold)
            (recur (fun world threshold))))))))


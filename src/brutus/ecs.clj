(ns brutus.ecs
  "Functions for creating and modifying ECS systems.

  Note that `ecs/current-sys-ref`, `ecs/current-entity`, and `ecs/current-type`
  are dynamic vars that can be set with `with-system-context`. These dynamic
  vars are used in several functions to elide parameters. See
  `with-system-context` for more information."
  (:require
   [brutus.util :as util]
   [clojure.math.numeric-tower :as m]
   ))

;; TODO: rename system to world?

(defn create-system
  "Creates an empty system."
  []
  {;; Nested Map of Component Types -> Entity -> Component Instance
   :entity-components      {}
   ;; Map of Entities -> Set of Component Types
   :entity-component-types {}})

(defn ^:private remove-component-internal
  "Remove a component instance from the ES data structure and returns it"
  ([system entity type]
   (let [system (transient system)
         entity-components (:entity-components system)
         entity-component-types (:entity-component-types system)]
     (-> system
         (assoc! :entity-components
                 (assoc entity-components type (-> entity-components
                                                   (get type)
                                                   (dissoc entity))))
         (assoc! :entity-component-types
                 (assoc entity-component-types entity (-> entity-component-types
                                                          (get entity)
                                                          (disj type))))
         persistent!))))

(defn create-uuid
  "Generate a new random UUID. See `Java.util.UUID/randomUUID`"
  []
  (java.util.UUID/randomUUID))

(defn create-entity
  "Generate a new random UUID. See `create-uuid` and `Java.util.UUID/randomUUID`."
  []
  (create-uuid))

(declare iterating-system)

(def ^:dynamic current-sys-ref
  "Dynamic var representing the current system reference."
  nil)
(def ^:dynamic current-entity
  "Dynamic var representing the current entity UUID."
  nil)
(def ^:dynamic current-type
  "Dynamic var representing the current component type."
  nil)

(defn get-component
  "Get the component from an entity in the system with this type."
  ([type]
   (get-component @current-sys-ref current-entity type))
  ([entity type]
   (get-component @current-sys-ref entity type))
  ([system entity type]
   (-> system :entity-components (get-in [type entity]))))

(defn add-entity
  "Add an entity to the system. Also see `add-entity!` for a more useful function."
  ; TODO: make this not as bad.
  ([] (dosync (alter current-sys-ref #(add-entity %1 (create-entity)))))
  ([entity] (dosync (alter current-sys-ref #(add-entity %1 entity))))
  ([system entity]
   (let [system (transient system)]
     (-> system
         (assoc! :entity-component-types
                 (-> system :entity-component-types (assoc entity #{})))
         persistent!))))

(defn add-entity!
  "Add an entity to the current system (`current-sys-ref`), and return the ID of
  the newly created entity."
  []
  (let [entity (create-entity)]
    (dosync (alter current-sys-ref #(add-entity %1 entity)))
    entity))

(defn add-component
  "Add a component or a specific type to an entity in a system, or updates an
  existing component with new data."
  ([component] (dosync (alter current-sys-ref #(add-component %1 current-entity current-type component))))
  ([type component] (dosync (alter current-sys-ref #(add-component %1 current-entity type component))))
  ([entity type component] (dosync (alter current-sys-ref #(add-component %1 entity type component))))
  ([system entity type instance]
   (let [system (transient system)
         ecs (:entity-components system)
         ects (:entity-component-types system)]
     (-> system
         (assoc! :entity-components (assoc-in ecs [type entity] instance))
         (assoc! :entity-component-types (assoc ects entity (-> ects (get entity) (conj type))))
         persistent!))))

(defn update-component
  "Updates a component of a particular type based on the provided function. The
  function is passed the component data and any additional args. If the function
  returns nil, the component is not updated. Otherwise, the component data is updated
  with the return value."
  [system entity type fun & args]
  (if-let [update (apply fun (get-component system entity type) args)]
    (add-component system entity type update)
    system))

(defn kill-entity
  "Removes an entitity and all its components and returns it."
  ([] (dosync (alter current-sys-ref #(kill-entity %1 current-entity))))
  ([entity] (alter current-sys-ref #(kill-entity %1 entity)))
  ([system entity]
   (let [system (transient system)
         entity-component-types (:entity-component-types system)]
     (-> system
         (assoc! :entity-component-types (dissoc entity-component-types entity))
         (assoc! :entity-components
                 (persistent! (reduce (fn [v type]
                                        (assoc! v type (dissoc (get v type) entity)))
                                      (transient (:entity-components system))
                                      (get entity-component-types entity))))
         persistent!))))

(defn remove-component
  "Remove a component instance from the system and returns it."
  ([] (dosync (alter current-sys-ref #(remove-component %1 current-entity current-type))))
  ([type] (dosync (alter current-sys-ref #(remove-component %1 current-entity type))))
  ([entity type] (dosync (alter current-sys-ref #(remove-component %1 entity type))))
  ([system entity type]
   (let [system (transient system)
         entity-components (:entity-components system)
         entity-component-types (:entity-component-types system)]
     (-> system
         (assoc! :entity-components (assoc entity-components type (-> entity-components (get type) (dissoc entity))))
         (assoc! :entity-component-types (assoc entity-component-types entity (-> entity-component-types (get entity) (disj type))))
         persistent!))))

(defn add-system
  "Adds a new system function to the system. System functions take a system and
  a time delta and return a new system. See `iterating-system` and
  `mapping-system` for more help making more nuanced system functions."
  [system fun]
  (assoc system :system-fns (conj (:system-fns system) fun)))

(defn get-all-entities-with-component
  "Returns all entities (by ID) with a specific component attached."
  [system type]
  (or (-> system :entity-components (get type) keys) []))

(defn add-iterating-system
  "Adds a new iterating system function to the system. See `iterating-system`."
  [system type fun]
  (add-system system (iterating-system type fun)))

(defn process-tick
  "Executes all system functions in sequence with the supplied delta, and returns
  the resulting system. If a system returns nil, it throws a
  `java.lang.AssertionError`."
  [system delta]
  (reduce (fn [sys sys-fn] (util/not-nil! (sys-fn sys delta)
                                          "System function returned nil"))
          system (:system-fns system)))

(defmacro with-system-context
  "Execute the body with the current system, entity, and type bound. The system
  should be a `ref` to the system. Binds the values to the dynamic vars
  `current-sys-ref`, `current-entity`, and `current-type`. With these vars bound
  many ECS functions which normally take these values can leave them out of the
  parameters. These functions use `dosync` and `alter` to update the system ref
  with their changes."
  [sys-ref entity type & body]
  `(binding [current-sys-ref ~sys-ref
            current-entity ~entity
            current-type ~type]
    ~@body))

(defn iterating-system
  "Creates a system function from another function. When the system is invoked,
  fun gets invoked for every entity that has a component of the specified type.
  When fun is invoked, `with-system-context` is used to bind the current system,
  entity, and type, allowing for the shortened versions of system functions to be
  used."
  [type fun]
  (fn [system delta]
    (let [sys-ref (ref system)]
      (doseq [entity (get-all-entities-with-component system type)]
        (let [local-entity entity
              local-type   type]
          (with-system-context sys-ref entity type
            (fun entity))))
      @sys-ref)))

(defn mapping-system
  "System mapping strictly over a single component. Function takes
  (fun component) or (fun component delta)."
  [type fun]
  (fn [system delta]
    (let [adapted-fun (util/make-flexible-fn fun)]
      (reduce
       (fn [system entity]
         (update-component system entity type adapted-fun delta))
       system
       (get-all-entities-with-component system type)))))

(defn add-singleton-ref
  "Adds a singleton entity that executes an iterating system function. Internal
  data defaults to an empty map. Returns `[system entity]`, where `entity` is
  the ID of the singleton."
  ([system fun] (add-singleton-ref system {} fun))
  ([system data fun] (add-singleton-ref system (gensym) data fun))
  ([system type data fun]
   (let [entity    (create-entity)
         component data
         system    (-> system
                       (add-entity entity)
                       (add-component entity type component)
                       (add-iterating-system type fun))]
     [system entity])))

(defn add-singleton
  "Adds a singleton entity that executes an iterating system function. Internal
  data defaults to an empty map. Returns the new system."
  ([system fun] (add-singleton system {} fun))
  ([system data fun] (add-singleton system (gensym) data fun))
  ([system type data fun]
   (let [[system entity] (add-singleton-ref system type data fun)]
     system)))

(defn get-all-entities
  "Returns a list of all the entities. Not that useful in application, but good for debugging/testing"
  [system]
  (if-let [result (-> system :entity-component-types keys)]
    result
    []))

;; TODO: test throttled functions
;; TODO: refactor this into a wrapping function.

(defn ^:private throttled-fn
  "The function that does the actual throttling."
  [system-fn atom threshhold system delta]
  (swap! atom + delta)
  (if (>= @atom threshhold)
    (reduce (fn [v _]                       ;; this takes care of when the framerate
              (swap! atom - threshhold)     ;; is WAY slower than the throttle.
              (system-fn v delta))
            system (-> @atom (/ threshhold) m/floor range))
    system))

(defn add-throttled-system-fn
  "Same as `add-system-fn`, but will only execute the `system-fn` after `threshold` milliseconds has been equalled or passed."
  [system system-fn threshold]
  (add-system system (partial throttled-fn system-fn (atom 0) threshold)))

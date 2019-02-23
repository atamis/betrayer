(ns betrayer.ecs
  "Functions for creating and modifying ECS worlds.

  Note that `ecs/current-sys-ref`, `ecs/current-entity`, and `ecs/current-type`
  are dynamic vars that can be set with `with-world-context`. These dynamic
  vars are used in several functions to elide parameters. See
  `with-world-context` for more information.

  Worlds consist of entities with components attached to them, where components
  are tags (typically symbols) with attached data. Systems are functions which
  take a world and return a new world. See `system/mapping-system` and
  `system/iterating-system`for more nuanced system options.
  "
  (:require
   [betrayer.util :as util]))

(defn create-world
  "Creates an empty world."
  []
  {;; Nested Map of Component Types -> Entity -> Component Instance
   :entity-components      {}
   ;; Map of Entities -> Set of Component Types
   :entity-component-types {}})

(defn ^:private remove-component-internal
  ([world entity type]
   (let [world (transient world)
         entity-components (:entity-components world)
         entity-component-types (:entity-component-types world)]
     (-> world
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
  "Generate a new random ID. See `create-uuid` and `Java.util.UUID/randomUUID`."
  []
  (create-uuid))

(def ^:dynamic current-world-ref
  "Dynamic var representing the current world reference."
  nil)
(def ^:dynamic current-entity
  "Dynamic var representing the current entity UUID."
  nil)
(def ^:dynamic current-type
  "Dynamic var representing the current component type."
  nil)

(defn get-component
  "Get the component from an entity in the world with this type."
  ([type]
   (get-component @current-world-ref current-entity type))
  ([entity type]
   (get-component @current-world-ref entity type))
  ([world entity type]
   (-> world :entity-components (get-in [type entity]))))

(defn add-entity
  "Add an entity to the world. Also see `add-entity!` for a more useful function."
  ; TODO: make this not as bad.
  ([] (dosync (alter current-world-ref #(add-entity %1 (create-entity)))))
  ([entity] (dosync (alter current-world-ref #(add-entity %1 entity))))
  ([world entity]
   (let [world (transient world)]
     (-> world
         (assoc! :entity-component-types
                 (-> world :entity-component-types (assoc entity #{})))
         persistent!))))

(defn add-entity!
  "Add an entity to the current world (`current-world-ref`), and return the ID of
  the newly created entity."
  []
  (let [entity (create-entity)]
    (dosync (alter current-world-ref #(add-entity %1 entity)))
    entity))

(defn add-component
  "Add a component of a specific type to an entity in a world, or updates an
  existing component with new data."
  ([component] (dosync (alter current-world-ref #(add-component %1 current-entity current-type component))))
  ([type component] (dosync (alter current-world-ref #(add-component %1 current-entity type component))))
  ([entity type component] (dosync (alter current-world-ref #(add-component %1 entity type component))))
  ([world entity type instance]
   (let [world (transient world)
         ecs (:entity-components world)
         ects (:entity-component-types world)]
     (-> world
         (assoc! :entity-components (assoc-in ecs [type entity] instance))
         (assoc! :entity-component-types (assoc ects entity (-> ects (get entity) (conj type))))
         persistent!))))

(defn update-component
  "Updates a component of a particular type based on the provided function. The
  function is passed the component data and any additional args. If the function
  returns nil, the component is not updated. Otherwise, the component data is updated
  with the return value."
  [world entity type fun & args]
  (if-let [update (apply fun (get-component world entity type) args)]
    (add-component world entity type update)
    world))

(defn kill-entity
  "Removes an entitity and all its components and returns it."
  ([] (dosync (alter current-world-ref #(kill-entity %1 current-entity))))
  ([entity] (alter current-world-ref #(kill-entity %1 entity)))
  ([world entity]
   (let [world (transient world)
         entity-component-types (:entity-component-types world)]
     (-> world
         (assoc! :entity-component-types (dissoc entity-component-types entity))
         (assoc! :entity-components
                 (persistent! (reduce (fn [v type]
                                        (assoc! v type (dissoc (get v type) entity)))
                                      (transient (:entity-components world))
                                      (get entity-component-types entity))))
         persistent!))))

(defn remove-component
  "Remove a component instance from the world and returns it."
  ([] (dosync (alter current-world-ref #(remove-component %1 current-entity current-type))))
  ([type] (dosync (alter current-world-ref #(remove-component %1 current-entity type))))
  ([entity type] (dosync (alter current-world-ref #(remove-component %1 entity type))))
  ([world entity type]
   (let [world (transient world)
         entity-components (:entity-components world)
         entity-component-types (:entity-component-types world)]
     (-> world
         (assoc! :entity-components (assoc entity-components type (-> entity-components (get type) (dissoc entity))))
         (assoc! :entity-component-types (assoc entity-component-types entity (-> entity-component-types (get entity) (disj type))))
         persistent!))))

(defn add-system
  "Adds a new system to the world. System functions take a world and a time delta
  and return a new world. See `system/iterating-system` and
  `system/mapping-system` for more help making more nuanced system functions."
  [world fun]
  (assoc world :system-fns (conj (:system-fns world) fun)))

(defn get-all-entities-with-component
  "Returns all entities (by ID) with a specific component attached."
  [world type]
  (or (-> world :entity-components (get type) keys) []))

(defn process-tick
  "Executes all system functions in sequence with the supplied delta, and returns
  the resulting world. If a system returns nil, it throws a
  `java.lang.AssertionError`."
  [world delta]
  (reduce (fn [sys sys-fn] (util/not-nil! (sys-fn sys delta)
                                          "System function returned nil"))
          world (:system-fns world)))

(defmacro with-world-context
  "Execute the body with the current world, entity, and type bound. The world
  should be a `ref` to the world. Binds the values to the dynamic vars
  `current-world-ref`, `current-entity`, and `current-type`. With these vars bound
  many ECS functions which normally take these values can leave them out of the
  parameters. These functions use `dosync` and `alter` to update the world ref
  with their changes. This is used automatically in `iterating-system`, but can
  also be used to enable the shorthand functions in your own systems."
  [world-ref entity type & body]
  `(binding [current-world-ref ~world-ref
             current-entity ~entity
             current-type ~type]
     ~@body))

(defn get-all-entities
  "Returns a list of all the entities. Not that useful in application, but good
  for debugging/testing."
  [system]
  (if-let [result (-> system :entity-component-types keys)]
    result
    []))


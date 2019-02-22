(ns brutus.ecs
  (:require 
            [brutus.util :as util]))

(defn create-system
  "Creates the system data structure that will need to be passed to all entity functions"
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
         (assoc! :entity-components (assoc entity-components type (-> entity-components (get type) (dissoc entity))))
         (assoc! :entity-component-types (assoc entity-component-types entity (-> entity-component-types (get entity) (disj type))))
         persistent!))))

(defn create-uuid []
  (java.util.UUID/randomUUID))

(def create-entity create-uuid)

(declare iterating-system)

(def ^:dynamic current-sys-ref nil)
(def ^:dynamic current-entity nil)
(def ^:dynamic current-type nil)

(defn get-component
  ([type]
   (get-component @current-sys-ref current-entity type))
  ([entity type]
   (get-component @current-sys-ref entity type))
  ([system entity type]
   (-> system :entity-components (get-in [type entity]))))

(defn add-entity
  ([] (dosync (alter current-sys-ref #(add-entity %1 (create-entity)))))
  ([entity] (dosync (alter current-sys-ref #(add-entity %1 entity))))
  ([system entity]
   (let [system (transient system)]
     (-> system
         (assoc! :entity-component-types (-> system :entity-component-types (assoc entity #{})))
         persistent!))))

(defn add-component
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
  [system entity type fn & args]
  (if-let [update (apply fn (get-component system entity type) args)]
    (add-component system entity type update)
    system))

(defn kill-entity
  ([] (dosync (alter current-sys-ref #(kill-entity %1 current-entity))))
  ([entity] (alter current-sys-ref #(kill-entity %1 entity)))
  ([system entity]
   (let [system (transient system)
         entity-component-types (:entity-component-types system)]
     (-> system
         (assoc! :entity-component-types (dissoc entity-component-types entity))
         (assoc! :entity-components (persistent! (reduce (fn [v type] (assoc! v type (dissoc (get v type) entity)))
                                                         (transient (:entity-components system)) (get entity-component-types entity))))
         persistent!))))

(defn remove-component
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

(defn add-entity!
  []
  (let [entity (create-entity)]
    (dosync (alter current-sys-ref #(add-entity %1 entity)))
    entity))

(defn add-system
  [system fun]
  (assoc system :system-fns (conj (:system-fns system) fun)))

(defn get-all-entities-with-component [system type] (or (-> system :entity-components (get type) keys) []))
(defn add-iterating-system
  [system type fun]
  (add-system system (iterating-system type fun)))
(defn process-tick
  [system delta]
  (reduce (fn [sys sys-fn] (util/not-nil! (sys-fn sys delta) "System function returned nil"))
          system (:system-fns system)))

(defn iterating-system
  [type fun]
  (fn [system delta]
    (let [sys-ref (ref system)]
      (doseq [entity (get-all-entities-with-component system type)]
        (let [local-entity entity
              local-type   type]
          (binding [current-sys-ref sys-ref
                    current-entity entity
                    current-type type]
            (fun entity))))
      @sys-ref)))

(defn mapping-system
  "System mapping strictly over a single component. Function takes (fun component) or (fun component delta)"
  [type fun]
  (fn [system delta]
    (let [adapted-fun (util/make-flexible-fn fun)]
      (reduce
       (fn [system entity]
         (update-component system entity type adapted-fun delta))
       system
       (get-all-entities-with-component system type)))))

(defn add-singleton-ref
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
  ([system fun] (add-singleton system {} fun))
  ([system data fun] (add-singleton system (gensym) data fun))
  ([system type data fun]
   (let [[system entity] (add-singleton-ref system type data fun)]
     system)))

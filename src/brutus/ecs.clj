(ns brutus.ecs
  (:require [brutus.entity :as entity]
            [brutus.system :as system]
            [brutus.util :as util]))

(defmethod entity/get-component-type clojure.lang.PersistentArrayMap
  [component]
  (:component component))

(defmethod entity/get-component-type clojure.lang.PersistentHashMap
  [component]
  (:component component))

(def get-component-type entity/get-component-type)

(defn create-system
  []
  (entity/create-system))

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

(declare iterating-system)

(def ^:dynamic current-sys-ref nil)
(def ^:dynamic current-entity nil)
(def ^:dynamic current-type nil)

(defn get-component
  ([type]
   (entity/get-component @current-sys-ref current-entity type))
  ([entity type]
   (entity/get-component @current-sys-ref entity type))
  ([system entity type]
   (entity/get-component system entity type)))

(defn add-entity
  ([] (dosync (alter current-sys-ref #(entity/add-entity %1 (entity/create-entity)))))
  ([entity] (dosync (alter current-sys-ref #(entity/add-entity %1 entity))))
  ([system entity] (entity/add-entity system entity)))

(defn add-component
  ([component] (dosync (alter current-sys-ref #(entity/add-component %1 current-entity component))))
  ([entity component] (dosync (alter current-sys-ref #(entity/add-component %1 entity component))))
  ([system entity component] (entity/add-component system entity component)))

(defn kill-entity
  ([] (dosync (alter current-sys-ref #(entity/kill-entity %1 current-entity))))
  ([entity] (alter current-sys-ref #(entity/kill-entity %1 entity)))
  ([system entity] (entity/kill-entity system entity)))

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
  (let [entity (entity/create-entity)]
    (dosync (alter current-sys-ref #(entity/add-entity %1 entity)))
    entity))

(def create-entity entity/create-entity)
(def add-system system/add-system-fn)
(def get-all-entities-with-component entity/get-all-entities-with-component)
(defn add-iterating-system
  [system type fun]
  (add-system system (iterating-system type fun)))
(def process-tick system/process-one-game-tick)

(defn iterating-system
  [type fun]
  (fn [system delta]
    (let [sys-ref (ref system)]
      (doseq [entity (entity/get-all-entities-with-component system type)]
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
         (entity/update-component system entity type adapted-fun delta))
       system
       (get-all-entities-with-component system type)))))

(defn add-singleton-ref
  ([system fun] (add-singleton-ref system {} fun))
  ([system data fun] (add-singleton-ref system (gensym) data fun))
  ([system type data fun]
   (let [entity    (create-entity)
         component (assoc data :component type)
         system    (-> system
                       (add-entity entity)
                       (add-component entity component)
                       (add-iterating-system type fun))]
     [system entity])))

(defn add-singleton
  ([system fun] (add-singleton system {} fun))
  ([system data fun] (add-singleton system (gensym) data fun))
  ([system type data fun]
   (let [[system entity] (add-singleton-ref system type data fun)]
     system)))

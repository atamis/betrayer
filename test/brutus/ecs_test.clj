(ns brutus.ecs-test
  (:require [brutus.ecs :as ecs]
            [clojure.test :as t]
            [brutus.event :as event]
            [clojure.set :as set]
            ))

(def ^:dynamic sys nil)

(defn setup-sys
  [f]
  (binding [sys (ref (event/add-event-system (ecs/create-system)))]
    (f)))

(defn same-elements
  [& seqs]
  (= #{} (apply set/difference (map #(into #{} %1) seqs))))

(t/use-fixtures :each setup-sys)

(defn simple-add-system
  [system type data fun]
  (into [] (reverse (ecs/add-singleton-ref system type data fun))))

(t/deftest iterating-system
  (defn setup-ticking-system
    [system]
    (simple-add-system system :tick {:n 0} (fn [entity]
                                             (-> (ecs/get-component :tick)
                                                 (update :n inc)
                                                 (ecs/add-component)))))
  (t/testing "simple tick system"
    (let [[entity sys] (setup-ticking-system @sys)
          component (ecs/get-component sys entity :tick)]

      (t/is (= 0 (:n component)))
      (def new-sys (ecs/process-tick sys 1))
      (def new-component (ecs/get-component new-sys entity :tick))

      (t/is (not (= component new-component)))
      (t/is (= 1 (:n

                  new-component)))))

  (defn setup-event-system
    [system]
    (simple-add-system system :tick-event {:n 0}
                       (fn [entity]
                         (let [component (ecs/get-component :tick-event)
                               old-n (:n component)
                               new-component (update component :n inc)]
                           (event/add-event old-n)
                           (ecs/add-component new-component)))))

  (t/testing "adding events"
    (let [[entity sys] (setup-event-system @sys)]
      (t/is (= [] @(:events sys)))
      (def new-sys (ecs/process-tick sys 1))
      (t/is (= [0] @(:events sys)))
      (def new-sys (ecs/process-tick new-sys 1))
      (t/is (= [0 1] (event/drain-events sys)))))

  (defn setup-entity-spawner
    [system]
    (simple-add-system system :entity-spawner {:n 0}
                       (fn [entity]
                         (let [component (ecs/get-component :entity-spawner)
                               old-n (:n component)
                               new-component (update component :n inc)
                               new-entity (ecs/add-entity!)]
                           (event/add-event new-entity)
                           (ecs/add-component new-entity {:component :dummy})
                           (ecs/add-component new-component)))))

  (t/testing "adding entities"
    (let [[entity sys] (setup-entity-spawner @sys)]
      ; Ensure there are no existing dummy components
      (t/is (= [] (ecs/get-all-entities-with-component sys :dummy)))

      ; Tick
      (t/is (= {:component :entity-spawner :n 0} (ecs/get-component sys entity :entity-spawner)))
      (def new-sys (ecs/process-tick sys 1))
      (t/is (= {:component :entity-spawner :n 1} (ecs/get-component new-sys entity :entity-spawner)))

      ; Check the first spawned entity
      (def events (event/drain-events new-sys))
      (def new-entity (events 0))

      ; And that it has the component
      (t/is (= events (ecs/get-all-entities-with-component new-sys :dummy)))

      ; Drop and check a bunch.
      (def new-sys (ecs/process-tick new-sys 1))
      (def new-sys (ecs/process-tick new-sys 1))
      (def new-sys (ecs/process-tick new-sys 1))
      (def dummy-entities (conj (event/drain-events new-sys) new-entity))

      (t/is (same-elements dummy-entities (ecs/get-all-entities-with-component new-sys :dummy)))))

  (defn setup-component-remover
    [system]
    (simple-add-system system :self-remover {} (fn [entity]
                                                 (ecs/remove-component entity :self-remover))))

  (t/testing "removing components"
    (let [[entity sys] (setup-component-remover @sys)]
      (t/is (= [entity] (ecs/get-all-entities-with-component sys :self-remover)))
      (def new-sys (ecs/process-tick sys 1))
      (t/is (= [] (ecs/get-all-entities-with-component new-sys :self-remover)))))

  (defn setup-entity-remover
    [system]
    (simple-add-system system :self-destructor {} (fn [entity] (ecs/kill-entity))))

  (t/testing "removing entities"
    (let [[entity sys] (setup-entity-remover @sys)]
      (t/is (some? ((:entity-component-types sys) entity)))
      (def new-sys (ecs/process-tick sys 1))
      (t/is (not (some? ((:entity-component-types new-sys)
                         entity))))))

  (t/testing "mapping systems"
    (def entity (ecs/create-entity))
    (def system (-> @sys
                    (ecs/add-entity entity)
                    (ecs/add-component entity {:component :tick :n 0 :m 0})
                    (ecs/add-system (ecs/mapping-system :tick #(update %1 :n inc)))
                    (ecs/add-system (ecs/mapping-system :tick (fn [component delta] (update component :m #(+ delta %1)))))
                    ))
    (t/is (= 0 (:n (ecs/get-component system entity :tick))))
    (t/is (= 0 (:m (ecs/get-component system entity :tick))))
    ; Note delta = 2
    (def system (ecs/process-tick system 2))
    (t/is (= 1 (:n (ecs/get-component system entity :tick))))
    (t/is (= 2 (:m (ecs/get-component system entity

                                      :tick))))))


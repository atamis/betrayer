(ns betrayer.ecs-test
  (:require [betrayer.ecs :as ecs]
            [clojure.test :as t]
            [betrayer.event :as event]
            [clojure.set :as set]
            [betrayer.system :as system]))

(def ^:dynamic sys nil)

(defn setup-sys
  [f]
  (binding [sys (ref (event/add-event-system (ecs/create-world)))]
    (f)))

(defn same-elements
  [& seqs]
  (= #{} (apply set/difference (map #(into #{} %1) seqs))))

(t/use-fixtures :each setup-sys)

(defn simple-add-system
  [system type data fun]
  (into [] (reverse (system/add-singleton-ref system type data fun))))

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
                           (event/add-event [:topic, old-n])
                           (ecs/add-component new-component)))))

  (t/testing "adding events"
    (let [[entity sys] (setup-event-system @sys)]
      (t/is (= [] @(:events sys)))
      (def new-sys (ecs/process-tick sys 1))
      (t/is (= [0] (map second @(:events sys))))
      (def new-sys (ecs/process-tick new-sys 1))
      (t/is (= [1] (map second (event/drain-events sys))))))

  (defn setup-entity-spawner
    [system]
    (simple-add-system system :entity-spawner {:n 0}
                       (fn [entity]
                         (let [component (ecs/get-component :entity-spawner)
                               old-n (:n component)
                               new-component (update component :n inc)
                               new-entity (ecs/add-entity!)]
                           (event/add-event [:topic new-entity])
                           (ecs/add-component new-entity :dummy :dummy)
                           (ecs/add-component new-component)))))

  (t/testing "adding entities"
    (let [[entity sys] (setup-entity-spawner @sys)]
      ; Ensure there are no existing dummy components
      (t/is (= [] (ecs/get-all-entities-with-component sys :dummy)))

      ; Tick
      (t/is (= {:n 0} (ecs/get-component sys entity :entity-spawner)))
      (def new-sys (ecs/process-tick sys 1))
      (t/is (= {:n 1} (ecs/get-component new-sys entity :entity-spawner)))

      ; Check the first spawned entity
      (def events (map second (event/drain-events new-sys)))
      (def new-entity (nth events 0))

      ; And that it has the component
      (t/is (= events (ecs/get-all-entities-with-component new-sys :dummy)))

      ; Drop and check a bunch.
      (def new-sys (ecs/process-tick new-sys 1))
      (def new-sys (ecs/process-tick new-sys 1))
      (def new-sys (ecs/process-tick new-sys 1))
      (def dummy-entities (conj (map second (event/drain-events new-sys)) new-entity))

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

  (t/testing "update-in-components"
    (let [inc-and-last (fn [n] [(inc n) n])
          [entity sys]
          (simple-add-system
           @sys
           :updater
           0
           (fn [entity]
             (event/add-event [:topic (ecs/update-in-component entity :updater inc-and-last)])
             (event/add-event [:topic (ecs/update-in-component :updater inc-and-last)])
             (event/add-event [:topic (ecs/update-in-component inc-and-last)])))]
      (def new-sys (ecs/process-tick sys 1))
      (t/is (= 3 (ecs/get-component new-sys entity :updater)))
      (t/is (= [0 1 2] (map second (event/drain-events new-sys))))))

  (defn setup-entity-remover
    [system]
    (simple-add-system system :self-destructor {} (fn [entity] (ecs/kill-entity))))

  (t/testing "removing entities"
    (let [[entity sys] (setup-entity-remover @sys)]
      (t/is (some? ((:entity-component-types sys) entity)))
      (def new-sys (ecs/process-tick sys 1))
      (t/is (not (some? ((:entity-component-types new-sys)
                         entity))))))

  (t/testing "get all components"
    (def entity (ecs/create-entity))
    (def new-sys (ecs/add-entity @sys entity))
    (t/is (= {} (ecs/get-all-components new-sys entity)))
    (def new-sys (ecs/add-component new-sys entity :test1 true))
    (t/is (= {:test1 true} (ecs/get-all-components new-sys entity)))
    (def new-sys (ecs/add-component new-sys entity :test2 :asdf))
    (t/is (= {:test1 true :test2 :asdf} (ecs/get-all-components new-sys entity)))
    (def new-sys (ecs/remove-component new-sys entity :test1))
    (t/is (= {:test2 :asdf} (ecs/get-all-components new-sys entity)))
    )

  )



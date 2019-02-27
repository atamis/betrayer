(ns betrayer.system-test
  (:require [betrayer.system :as system]
            [betrayer.ecs :as ecs]
            [betrayer.event :as event]
            [clojure.test :as t]))


(t/deftest systemtest
  (t/testing "mapping systems"
    (def entity (ecs/create-entity))
    (def system (-> (ecs/create-world)
                    (ecs/add-entity entity)
                    (ecs/add-component entity :tick {:n 0 :m 0})
                    (ecs/add-system (system/mapping-system :tick #(update %1 :n inc)))
                    (ecs/add-system (system/mapping-system :tick (fn [component delta] (update component :m #(+ delta %1)))))))
    (t/is (= 0 (:n (ecs/get-component system entity :tick))))
    (t/is (= 0 (:m (ecs/get-component system entity :tick))))
    ; Note delta = 2
    (def system (ecs/process-tick system 2))
    (t/is (= 1 (:n (ecs/get-component system entity :tick))))
    (t/is (= 2 (:m (ecs/get-component system entity
                                      :tick)))))
  (t/testing "throttled-system"
    (def counter (atom 0))
    (def world (-> (ecs/create-world)
                   (ecs/add-system
                    (system/throttled-system
                     10
                     (fn [system delta] (swap! counter + delta) system)))))

    (def world (ecs/process-tick world 1))
    (t/is (= 0 @counter))
    (def world (ecs/process-tick world 1))
    (t/is (= 0 @counter))
    (def world (ecs/process-tick world 8))
    (t/is (= 10 @counter))
    (def world (ecs/process-tick world 10))
    (def world (ecs/process-tick world 10))
    (t/is (= 30 @counter)))

  )

(ns betrayer.event-test
  (:require [betrayer.event :as event]
            [clojure.test :as t]
            [betrayer.ecs :as ecs]
            [betrayer.system :as system]))

(t/deftest event-system
  (t/testing "the event system"
    (let [sys (-> (ecs/create-world) (event/add-event-system) ref)]
      (t/is (= [] (event/drain-events sys)))
      (t/is (= [] (event/drain-events @sys)))
      (event/add-event sys :event1)
      (t/is (= [:event1] (event/drain-events sys)))
      (t/is (= [] (event/drain-events sys)))))

  (t/testing "deliver events internals"
    (def deliver-events #'betrayer.event/deliver-events)

    (t/is (= {:topic [[:topic 1]]} (deliver-events {:topic []} {:topic [[:topic 1]]})))
    (t/is (= {:topic2 []} (deliver-events {:topic2 []} {:topic [[:topic 1]]})))

    )

  (t/testing "subscriptions"
    (def entity (ecs/create-entity))
    (def world (-> (ecs/create-world)
                   (event/add-event-system)
                   (ecs/add-entity entity)
                   (ecs/add-component entity :rebroadcast true)
                   (system/add-iterating-system
                    :rebroadcast
                    (fn [entity]
                      (event/add-event [:topic2 (:topic (ecs/get-component :subscription))])
                      (event/add-event [:topic3 (event/get-events :topic)])
                      )
                    )
                   ))
    (def world (event/subscribe world entity :topic))
    (event/add-event world [:topic 1])
    (def world (ecs/process-tick world 1))
    (t/is (= [[:topic2 [[:topic 1]]] [:topic3 [[:topic 1]]]] (event/drain-events world)))
    )
  )

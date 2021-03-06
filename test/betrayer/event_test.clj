(ns betrayer.event-test
  (:require [betrayer.event :as event]
            [clojure.test :as t]
            [betrayer.ecs :as ecs]))

(t/deftest event-system
  (t/testing "the event system"
    (let [sys (-> (ecs/create-world) (event/add-event-system) ref)]
      (t/is (= [] (event/drain-events sys)))
      (t/is (= [] (event/drain-events @sys)))
      (event/add-event sys :event1)
      (t/is (= [:event1] (event/drain-events sys)))
      (t/is (= [] (event/drain-events sys))))))

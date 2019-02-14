(ns brutus.event-test
  (:require [brutus.event :as event]
            [clojure.test :as t]
            [brutus.ecs :as ecs]))

(t/deftest event-system
  (t/testing "the event system"
    (let [sys (-> (ecs/create-system) (event/add-event-system) ref)]
      (t/is (= [] (event/drain-events sys)))
      (t/is (= [] (event/drain-events @sys)))
      (event/add-event sys :event1)
      (t/is (= [:event1] (event/drain-events sys)))
      (t/is (= [] (event/drain-events sys))))))

(ns betrayer.util-test
  (:require  [clojure.test :as t]
             [betrayer.util :as util]))

(t/deftest util
  (t/testing "arity-first"
    (t/is 0 (util/arity-min (fn [] nil)))
    (t/is 1 (util/arity-min (fn [x] nil)))
    (t/is 2 (util/arity-min (fn [x y] nil)))
    (t/is 1 (util/arity-min (fn ([x] nil) ([x y] nil))))
    (t/is 1 (util/arity-min (fn ([x y] nil) ([x] nil)))))

  (t/testing "arity-all"
    (t/is [0] (util/arity-all (fn [] nil)))
    (t/is [1] (util/arity-all (fn [x] nil)))
    (t/is [2] (util/arity-all (fn [x y] nil)))
    (t/is [1 2] (util/arity-all (fn ([x] nil) ([x y] nil))))
    (t/is [1 2] (util/arity-all (fn ([x y] nil) ([x] nil))))
    (t/is [3 4] (util/arity-all (fn ([x y z] nil) ([x y z w] nil)))))

  (t/testing "arity-max"
    (t/is 0 (util/arity-max (fn [] nil)))
    (t/is 1 (util/arity-max (fn [x] nil)))
    (t/is 2 (util/arity-max (fn [x y] nil)))
    (t/is 2 (util/arity-max (fn ([x] nil) ([x y] nil))))
    (t/is 2 (util/arity-max (fn ([x y] nil) ([x] nil))))
    (t/is 4 (util/arity-max (fn ([x y z] nil) ([x y z w] nil)))))

  (t/testing "apply-flex-arity"
    (def fun (fn ([] 0) ([x] 1) ([x y] 2) ([x y z] 3)))
    (t/is 0 (util/apply-flex-arity fun []))
    (t/is 1 (util/apply-flex-arity fun [nil]))
    (t/is 2 (util/apply-flex-arity fun [nil nil]))
    (t/is 3 (util/apply-flex-arity fun [nil nil nil]))
    (t/is 3 (util/apply-flex-arity fun [nil nil nil nil])))

  (t/testing "make-flexible-fn"
    (def fun (util/make-flexible-fn (fn ([] 0) ([x] 1) ([x y] 2) ([x y z] 3))))
    (t/is 0 (fun))
    (t/is 1 (fun nil))
    (t/is 2 (fun nil nil))
    (t/is 3 (fun nil nil nil))
    (t/is 3 (fun nil nil nil nil))

    (let [f1 (util/make-flexible-fn #(update %1 :n inc))]
      (t/is {:n 1} (f1 {:n 0} 1))
      (t/is {:n 1} (f1 {:n 0})))

    (let [f2 (util/make-flexible-fn (fn [component delta] (update component :m #(+ delta %1))))]
      (t/is (f2 {:m 0} 0))
      (t/is (thrown? clojure.lang.ArityException (f2 {:m 0})))))
  (t/testing "not-nil!"
    (t/is :test (util/not-nil! :test))
    (t/is :test (util/not-nil! :test "Test message"))

    (t/is (thrown? java.lang.AssertionError (util/not-nil! nil)))
    (t/is (thrown? java.lang.AssertionError (util/not-nil! nil "Was nil")))

    )

  (t/testing "reference?"
    (t/is (util/reference? (ref 0)))
    (t/is (util/reference? (atom 0)))
    (t/is (util/reference? (agent 0)))
    (t/is (not (util/reference? true)))
    (t/is (not (util/reference? false)))
    (t/is (not (util/reference? [])))
    (t/is (not (util/reference? {})))
    )
  )

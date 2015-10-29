(ns reagenttest.testinterop
  (:require [cljs.test :as t :refer-macros [is deftest]]
            [reagent.debug :refer-macros [dbg]]
            [reagent.interop :refer-macros [dot-quote dot-bang]]))


(deftest iterop-quote
  (let [o #js{:foo "foo"
              :foobar #js{:bar "bar"}
              :bar-foo "barfoo"}]
    (is (= "foo" (dot-quote o :foo)))
    (is (= "bar" (dot-quote o :foobar.bar)))
    (is (= "barfoo" (dot-quote o :bar-foo)))

    (is (= "foo" (dot-quote o -foo)))
    (is (= "bar" (dot-quote o -foobar.bar)))
    (is (= "barfoo" (dot-quote o -bar-foo)))

    (dot-bang o :foo "foo1")
    (is (= "foo1" (dot-quote o :foo)))

    (dot-bang o -foo "foo2")
    (is (= "foo2" (dot-quote o -foo)))

    (dot-bang o :foobar.bar "bar1")
    (is (= "bar1" (dot-quote o :foobar.bar)))

    (dot-bang o -foobar.bar "bar1")
    (is (= "bar1" (dot-quote o -foobar.bar)))))

(deftest interop-quote-call
  (let [o #js{:bar "bar1"
              :foo (fn [x]
                     (this-as this
                              (str x (dot-quote this :bar))))}
        o2 #js{:o o}]
    (is (= "ybar1" (dot-quote o foo "y")))
    (is (= "xxbar1" (dot-quote o2 o.foo "xx")))
    (is (= "abar1" (-> o2
                       (dot-quote :o)
                       (dot-quote foo "a"))))

    (is (= "bar1" (dot-quote o foo)))
    (is (= "bar1" (dot-quote o2 o.foo)))

    (dot-bang o :bar "bar2")
    (is (= "bar2" (dot-quote o foo)))

    (is (= "1bar2" (dot-quote (dot-quote o :foo)
                       call o 1)))))

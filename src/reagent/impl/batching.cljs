(ns reagent.impl.batching
  (:refer-clojure :exclude [flush])
  (:require [reagent.debug :refer-macros [dbg]]
            [reagent.interop :refer-macros [dot-quote dot-bang]]
            [reagent.ratom :as ratom]
            [reagent.impl.util :refer [is-client]]
            [clojure.string :as string]))

;;; Update batching

(defonce mount-count 0)

(defn next-mount-count []
  (set! mount-count (inc mount-count)))

(defn fake-raf [f]
  (js/setTimeout f 16))

(def next-tick
  (if-not is-client
    fake-raf
    (let [w js/window]
      (or (dot-quote w :requestAnimationFrame)
          (dot-quote w :webkitRequestAnimationFrame)
          (dot-quote w :mozRequestAnimationFrame)
          (dot-quote w :msRequestAnimationFrame)
          fake-raf))))

(defn compare-mount-order [c1 c2]
  (- (dot-quote c1 :cljsMountOrder)
     (dot-quote c2 :cljsMountOrder)))

(defn run-queue [a]
  ;; sort components by mount order, to make sure parents
  ;; are rendered before children
  (.sort a compare-mount-order)
  (dotimes [i (alength a)]
    (let [c (aget a i)]
      (when (dot-quote c :cljsIsDirty)
        (dot-quote c forceUpdate)))))

(defn run-funs [a]
  (dotimes [i (alength a)]
    ((aget a i))))

(deftype RenderQueue [^:mutable queue ^:mutable scheduled?
                      ^:mutable after-render]
  Object
  (queue-render [this c]
    (.push queue c)
    (.schedule this))
  (add-after-render [_ f]
    (.push after-render f))
  (schedule [this]
    (when-not scheduled?
      (set! scheduled? true)
      (next-tick #(.run-queue this))))
  (run-queue [_]
    (let [q queue aq after-render]
      (set! queue (array))
      (set! after-render (array))
      (set! scheduled? false)
      (run-queue q)
      (run-funs aq))))

(def render-queue (RenderQueue. (array) false (array)))

(defn flush []
  (.run-queue render-queue))

(defn queue-render [c]
  (dot-bang c :cljsIsDirty true)
  (.queue-render render-queue c))

(defn mark-rendered [c]
  (dot-bang c :cljsIsDirty false))

(defn do-after-flush [f]
  (.add-after-render render-queue f))

(defn do-later [f]
  (do-after-flush f)
  (.schedule render-queue))

;; Render helper

(defn is-reagent-component [c]
  (some-> c (dot-quote :props) (dot-quote :argv)))

(defn run-reactively [c run]
  (assert (is-reagent-component c))
  (mark-rendered c)
  (let [rat (dot-quote c :cljsRatom)]
    (if (nil? rat)
      (let [res (ratom/capture-derefed run c)
            derefed (ratom/captured c)]
        (when (not (nil? derefed))
          (dot-bang c :cljsRatom
              (ratom/make-reaction run
                                   :auto-run #(queue-render c)
                                   :derefed derefed)))
        res)
      (ratom/run rat))))

(defn dispose [c]
  (some-> (dot-quote c :cljsRatom)
          ratom/dispose!)
  (mark-rendered c))


(ns donut.hooked-test
  (:require
   #?(:clj [clojure.test :refer [deftest is]]
      :cljs [cljs.test :refer [deftest is] :include-macros true])
   [donut.hooked :as hooked]))

(hooked/defhook ::this-gets-set
  "testing this thing"
  int?)
(hooked/defhook ::this-does-not-get-set
  "testing this thing")

(hooked/set-hook-fn! ::this-gets-set (fn [_] 1))

(deftest test-hook-executes
  (is (= 1
         (hooked/call ::this-gets-set 5))))

(deftest unset-hook-call-doesnt-throw
  (hooked/call ::this-does-not-get-set nil))

(deftest validates-arg
  (is (thrown?
       #?(:clj clojure.lang.ExceptionInfo
          :cljs cljs.core/ExceptionInfo)
       (hooked/call ::this-gets-set "not an int"))))

;; still don't know how to do this
#_(deftest throws-on-eval
    (is (thrown?
         #?(:clj clojure.lang.ExceptionInfo
            :cljs cljs.core/ExceptionInfo)
         (hooked/call ::undefined "not an int"))))

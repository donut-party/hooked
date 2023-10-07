(ns donut.hooked
  (:require
   [malli.core :as m]
   [malli.error :as me]))

(def hook-registry
  (atom {}))

(defn defhook
  [hook-name doc & [arg-schema return-schema]]
  (swap! hook-registry assoc hook-name {:doc doc
                                        :arg-schema arg-schema
                                        :return-schema return-schema}))

(defn call*
  "call a handler if it's defined"
  [hook-name arg]
  (let [this-hook (hook-name @hook-registry)]
    (when-not this-hook
      (throw (ex-info "hook not defined" {:hook-name hook-name})))
    (let [{:keys [arg-schema f]} this-hook]
      (when-let [explanation (and arg-schema (m/explain arg-schema arg))]
        (throw (ex-info "invalid argument for hook"
                        {:hook-name hook-name
                         :spec-explain-human (me/humanize explanation)
                         :spex-explain explanation})))
      ;; TODO malli instrument the return val, enabled by global flag
      (when f
        (f arg)))))

;; TODO figure out how to do this with cljs
#?(:clj
   (defmacro call
     "macro so that it will throw on eval if hook isn't defined"
     [hook-name arg]
     (when-not (hook-name @hook-registry)
       (throw (ex-info "Calling undefined hook" {:hook-name hook-name})))
     `(call* ~hook-name ~arg)))

#?(:cljs
   (def call call*))

(defn register-handler
  "installs a function to call when the hook gets called"
  [hook-name f]
  (when-not (hook-name @hook-registry)
    (throw (ex-info "hook not defined" {:hook-name hook-name})))
  (swap! hook-registry assoc-in [hook-name :handler] f))

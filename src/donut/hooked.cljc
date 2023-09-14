(ns donut.hooked
  (:require
   [malli.core :as m]
   [malli.error :as me]))

(def hooks
  (atom {}))

(defn defhook
  [hook-name docstring & [arg-schema]]
  (swap! hooks assoc hook-name {:docstring docstring
                                :arg-schema arg-schema}))

(defn call*
  "call a hook fn if it's defined"
  [hook-name arg]
  (let [this-hook (hook-name @hooks)]
    (when-not this-hook
      (throw (ex-info "hook not defined" {:hook-name hook-name})))
    (let [{:keys [arg-schema f]} this-hook]
      (when-let [explanation (and arg-schema (m/explain arg-schema arg))]
        (throw (ex-info "invalid argument for hook"
                        {:hook-name hook-name
                         :spec-explain-human (me/humanize explanation)
                         :spex-explain explanation})))
      (when f
        (f arg)))))

(defmacro call
  "macro so that it will throw on eval if hook isn't defined"
  [hook-name arg]
  (when-not (hook-name @hooks)
    (throw (ex-info "Calling undefined hook" {:hook-name hook-name})))
  `(call* ~hook-name ~arg))

(defn set-hook-fn!
  "installs a function to call when the hook gets called"
  [hook-name f]
  (when-not (hook-name @hooks)
    (throw (ex-info "hook not defined" {:hook-name hook-name})))
  (swap! hooks assoc-in [hook-name :f] f))

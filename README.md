[![Clojars Project](https://img.shields.io/clojars/v/party.donut/hooked.svg)](https://clojars.org/party.donut/hooked)

# hooked

Clojure's missing hook library. Every other hook library has fallen by the
wayside. But not this one.

## What _is_ this?!?

hooked introduces an extensibility mechanism for optional side effects. It aids
in creating libraries that encapsulate both a workflow, and side effects to
perform at specified points in the workflow. The big goal is to make it possible
to create an ecosystem of libraries and plugins for common tasks like user
signup / auth.

An example workflow is handling a user signup for a web app, where the core
logic includes validating the signup and sending the appropriate response. This
core logic can be written as a pure function, but developers typically also want
to perform side effects like logging failures and sending confirmation emails on
signup success. But if you're writing a user signup handler as part of a library
meant to be used by others, you can't bake those side effects into the library
itself. You need to offer extensibility somehow, and that's where hooked comes
in.

Here's an example that shows a couple hooks being defined for a signup handler,
as well as the hooks being called:

``` clojure
(hooked/defhook ::signup.validation-errors
  "Called when a signup fails because of validation errors"
  [:map [:errors :map]])

(hooked/defhook ::signup.signup-success
  "Called when a user signup is successful"
  [:map [:user :map]])

(defn signup-handler
  [{{:keys [datasource]} :dependencies
    :keys [all-params]
    :as req}]
  (if-let [errors (dsu/feedback (UserSignupSchema datasource) all-params)]
    (do
      (hooked/call ::signup.validation-errors (assoc req :errors errors))
      (der/errors-response errors all-params))
    (let [user (qi/user-signup! datasource all-params)]
      (hooked/call ::signup.signup-success (assoc req :user user))
      (auth-success-response user))))
```

To get `(hooked/call ::signup.validation-errors (assoc req :errors errors))` to
actually do something, you have to set the hook function:

``` clojure
(hooked/set-hook-fn! ::signup.validation-errors
  (fn [{:keys [errors]}]
    (log/info :signup-validation-errors {:errors errors})))
```

If your app includes the above code, then your app will log every time a user's
signup fails because of validation errors.

If you don't set a hook function, then nothing happens when that hook is called.
For example, the `::signup.signup-success` hook function isn't set, so
`(hooked/call ::signup.signup-success (assoc req :user user))` doesn't do
anything.

## How do I use it?

By using:

- `defhook`
- `set-hook-fn!`
- `call`

### `defhook`

``` clojure
(hooked/defhook :my-hook-name
  "docstring"  ;; required
  malli-schema ;; optional
  )
```

`defhook` creates your hook. You must include a docstring, and you can
optionally include a malli schema.

Hooks are inherently wibbly-wobbly, prone to confusion and abuse. A docstring
can help to alleviate that a little. That's why they're required.

If you define a malli schema, it's used to validate the argument sent to the
hook function. Hook functions take one and only one argument.

### `set-hook-fn!`

``` clojure
(hooked/set-hook-fn! ::my-hook-name
  (fn [x] (do-stuff x)))
```

This is how you install behavior for your hook. The function should take one and
only one argument.

Typically library consumers will call this function in this application to
customize the behavior of a library.

### `call`

``` clojure
(hooked/call ::my-hook-name {:x :y})
```

Call takes the hook name and the argument to send to the hook function. If the
hook hasn't been defined with `defhook`, then it will throw. If the hook is
defined but the hook function hasn't been set with `set-hook-fn!`, then nothing
happens.

## Why?

There are other valid ways to provide this kind of functionality, and hooked
isn't strictly necessary to provide it. However, my hope is that this approach
helps you achieve this kind of functionality in a way that's both clear within
your code, and clear to consumers of your library.

Let's look at other approaches and how they might be less than ideal:

### Pass functions in

You could write functions that take arguments for optional hooks, something like
this:

``` clojure
(defn example-pass-functions-in
  [{:keys [hook-1 hook-2]}]
  (if some-predicate
    (do
      (when hook-1 (hook-1 args))
      return-val)
    (do
      (when hook-2 (hook-2 args))
      return-val)))
```

There are two annoying things about this approach:

- You have to change your function to accept arguments that aren't actually
  related to its core behavior
- You always have to check whether the hook exists to call it

### Use builders

Your library could include builder functions, capturing your hooks in a closure
and returning a new function:

``` clojure
(defn example-build-fn
  [hook-1 hook-2]
  (fn []
    (if some-predicate
      (do
        (when hook-1 (hook-1 args))
        return-val)
      (do
        (when hook-2 (hook-2 args))
        return-val))))
```

But ew

### Use dynamic vars

Here's what the dynamic var version of this might look like:

``` clojure
(def ^:dynamic *hook-1*)
(def ^:dynamic *hook-2*)

(defn example-dynamic-vars
  []
  (if some-predicate
    (do
      (when (bound? *hook-1*) (*hook-1* args))
      return-val)
    (do
      (when (bound? *hook-2*) (*hook-2* args))
      return-val)))
```

Dynamic vars have just never struck me as being intended for this purpose The
behavior you're wanting to specify isn't "dynamic" in the way that I think is
meant for dynamic vars. What we're talking about here is extending a library
with app-specific behavior, and that behavior is something you only want to
define once; it's not something that should change as the application runs.

On a practical level, you'll need to bind those vars not just when constructing
the `-main` function for your application, but also in all the relevant tests.
This is something that you could easily forget to do, leading to frustrating
debugging sessions.

Finally, when writing your library you'll need write `(when (bound? *hook*)
...)` every place you want to use the hook, or else introduce a macro for that.

The optionality of hook behavior is a core constraint for them, and I think it
makes sense to introduce a little abstraction that suports that directly and
that makes it clear to everyone what's happening.

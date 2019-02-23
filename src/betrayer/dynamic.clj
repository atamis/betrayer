(ns betrayer.dynamic
  "Holds dynamically bound vars used by `betrayer.ecs` to adjust the behavior of
  its functions to elide extraneous parameters when context can be determined."
  )

(def ^:dynamic current-world-ref
  "Dynamic var representing the current world reference."
  nil)
(def ^:dynamic current-entity
  "Dynamic var representing the current entity UUID."
  nil)
(def ^:dynamic current-type
  "Dynamic var representing the current component type."
  nil)

(defmacro with-world-context
  "Execute the body with the current world, entity, and type bound. The world
  should be a `ref` to the world. Binds the values to the dynamic vars
  `current-world-ref`, `current-entity`, and `current-type`. With these vars bound
  many ECS functions which normally take these values can leave them out of the
  parameters. These functions use `dosync` and `alter` to update the world ref
  with their changes. This is used automatically in `iterating-system`, but can
  also be used to enable the shorthand functions in your own systems."
  [world-ref entity type & body]
  `(binding [current-world-ref ~world-ref
             current-entity ~entity
             current-type ~type]
     ~@body))

(defmacro with-world-upon-context
  "Like `with-world-context`, but takes an ECS world as a value rather than a
  ref, and returns the new world."
  [world entity type & body]
  `(let [world-ref (ref ~world)]
    (with-world-context world-ref ~entity ~type ~@body)
    @world-ref
    )
  )

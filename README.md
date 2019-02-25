# Betrayer

This is a simple and lightweight ECS framework in Clojure. It is a fork of
markmandel's [brute](https://github.com/markmandel/brute), so I had the genius
idea of naming my fork "brutus", but _someone already had that idea_, so this
library is "betrayer".

![Clojars Version](https://clojars.org/betrayer/latest-version.svg?v=2)

You may find [Entity Systems Wiki](http://entity-systems.wikidot.com/) and
[Adam Martin's Blog Post
series](http://t-machine.org/index.php/2007/09/03/entity-systems-are-the-future-of-mmog-development-part-1/)
useful for understanding ECS frameworks.

# Concepts and Types

The primary data structure of Betrayer is the `world`, which contains entities,
components, and systems.

* __Entities__ are uniquely identified objects in the world. Betrayer uses UUIDs
  as unique identifiers. Entities have no inherent data.
* __Components__ are attached to entities and can have data. They are identified
  by a tag, and tags are unique on an entity.
* __Systems__ are functions which take a world and a time delta and return an
  updated world. Systems use component tags to identify relevant entities,
  modify those component's data, add new components, and new entities.
  
  
Entities are UUIDs, components are normal Clojure data identified by keywords,
and systems are normal functions.

# Usage

Betrayer has several namespaces

* __betrayer.ecs__ contains functions for manipulating primary world data
  elements, like entities and components.
* __betrayer.system__ contains helper functions for writing system functions in
  different modes.
* __betrayer.event__ contains functions for subscribing and publishing events.
* __betrayer.dynamic__ contains vars and macros enabling easier world
  manipulation.
* __betrayer.util__ contains utility functions.

## Entities and Components

You can create a new empty world with `ecs/create-world`. With
`ecs/create-entity`, you can create a new unique identifier, and with
`ecs/add-entity`, you can register it with the world. You can add components to
an entity with the `ecs/add-component` function, which takes a world, an entity,
a component tag, and component data. You can query component data with
`ecs/get-component`, which returns nil if the component isn't found. You can
also remove components with `ecs/remove-component`, and kill entities with
`ecs/kill-entity`.

See documentation (`lein codox`) for specific parameters. By default, these
functions take a world and return a world.

You can also query the system en masse with `ecs/get-all-entities` and
`ecs/get-all-entities-with-component`, allowing system functions to operate over
entities tagged with components, rather than the code being attached to
components directly.

You can also register a new systme function with `ecs/add-system`, and execute
all systems in order with `ecs/process-tick`, which also takes a time delta.

## Dynamic Mode

Although maintaining functional purity is useful, it is frequently much more
convenient to see the ECS world less as a single piece of data and more like a
database. In particular, it can be very useful for the world to have an
"identity", in Clojure terms, even when it doesn't have one in the broader
program. Particularly when writing systems, the identity of the world the system
is operating is of paramount importance. Additionally, the identity of the
entity and component you're working on is also really important.

By default, when modifying the world with standard `ecs` functions, you have to
specify the world, the entity ID, and the component type in many cases, which
can get cumbersome when working on a single component in the same world.

So, the `dynamic` namespace offers several dynamic vars, and a macro to bind
them, to represent the current world, entity and component type. The entity and
component type are just data, but the world is a `ref`. `ecs` function are
written such that, if these vars are bound, you can (but don't have to) elide
that data from the function calls, and will update current world ref if a world
isn't specified. You can then use these functions to query, insert, and update
the world as though it were a database rather than simple value.

## Systems

By default, systems are functions that take a world and a delta and return a new
world. This is fine for simple systems, but when attempting to implement
slightly more complex behaviors (like updating all components with a particular
tag) it quickly gets cumbersome and boilerplate heavy. So, `betrayer/system` has
some functions to make common system functions easier.

`iterating-system` iterates over all the components of a particular type,
passing the entity ID to the function. It also enables dynamic mode, making it
easy to update both the current component but also other components on that
entity.

`mapping-system` maps over all the components of a particular type, applying the
function to their data, and updating the component with the return value. It
doesn't enable dynamic mode, and is intended for very simple components that
just need to update in response to game ticks or time deltas, or are updated by
other more complex systems.

`add-singleton` adds a single entity, and a single component with a unique tag,
and adds an `iterating-system` over that unique tag, giving you a function
operating in dynamic mode with its own data store in the component.

`throttled-system` takes a threshold and will only execute the system function
after that much time has elapsed, but otherwise works like a normal system
function.

# Examples

Examples coming soon. See `tests/betrayer/ecs_test.clj` for simple examples.

# Documentation

Documentation can be generated with `lein codox`.

# Differences

Betrayer differs from the original Brute library in 2 major and a few minor
ways. The major way it differs is in components. Brute has the concept of
component types or tags, but hides them from the user, suggesting that the class
of the component data be the tag, and that components should be `defrecord`s or
similar. This is implemented with a multimethod. Betrayer elevates the component
tag to a first class value specified by the user, and assumes you will use plain
Clojure data structures for the component data, if necessary. This is primarily
a conceptual change. In terms of code, it was a comparatively simple change.

The other major change is the introduction of dynamic mode, to make programming
complex systems easier. This is an optional mode that did require many code
changes.

The minor changes are some minor QoL changes, the helper functions in
`betrayer.system`, and the event system in `betrayer.event`.


# License

Copyright © 2019 Andrew Amis

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

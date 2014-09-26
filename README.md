# ns-reload

A Clojure library to reload namespaces correctly.

## Installation

Add `[[com.palletops/ns-reload "0.1.0-SNAPSHOT"]]` to your
`:plugins` in the `:user` profile of `profiles.clj`.

## Usage

```clj
(require '[com.palletops.ns-reload :refer :as deps])
```

To reload a namespace, e.g. your-ns, and all the namespaces that depend on it:

```clj
(deps/reload 'your-ns)
```


## Plugin Based Configuration

The plugin can be configured using the `:ns-reload` project key.
The key is specified as a map which may contain the following keys:

`:options`
: a map with `:verbose` and `:unload` boolean values.  If `:unload` is
  set to true, then namespaces will be unloaded before being reloaded.

`:ns-filters`
: a map (or sequence of pairs) specifying filters to be applied to
  namespaces that are determined to require reloading.  Defaults to
  reloading all namespaces.

`:reload-filters`
: a map (or sequence of pairs) specifying filters to be applied to a
  namespace that has been redefined, to determine if dependent
  namespaces should be loaded.  Defaults to false.  Requires editor
  integration for automated reloading to function.

`:pre-reload-hook`
: a symbol for a function to be called before dependent namespaces are
  reloaded.  Can be used to automatically stop components, etc.

`:post-reload-hook`
: a symbol for a function to be called after dependent namespaces are
  reloaded. Can be used to restart components, etc.

Filters are specified with keyword and value.  Valid filters are:

`:constantly`
: a constant boolean value

`:excludes`
: a sequence of namespace symbols to exclude

`:includes`
: a sequence of namespace symbols to include

`:exclude-regex`
: a regex which will exclude matching namespaces

`:include-regex`
: a regex which will include matching namespaces

As an example:

```clj
:ns-reload
  {:options {:verbose true}
   :ns-filters {:exclude-regex #"clojure.tools.nrepl.*"}
   :reload-filters {:constantly true}}
```

## lein-shorthand integration

The `:plugin.ns-reload/shorthand` profile provides mappings for
`reload` and `reload-dependents` into the `.` namespace.

To use the profile, add it to your :repl profile.

```clj
:repl [:plugin.ns-reload/shorthand]
```

You can then call (./reload 'my-ns) to reload a namespace and all its
dependents.

## Cider Integration

To automatically reload dependent namespaces when loading files in
Cider, add the following to your emacs configuration.

```lisp
(defun my-reload-dependents ()
  (let* ((buf (get-buffer (cider-current-repl-buffer)))
         (ns (with-current-buffer buf nrepl-buffer-ns)))
    (cider-tooling-eval
     (format
      "(when-let [rd (resolve 'com.palletops.ns-reload.repl/ns-reload-hook)]
         (@rd '%s {))"
      (cider-current-ns))
     (cider-interactive-eval-handler buf)
     ns)))

(add-hook 'cider-file-loaded-hook 'my-reload-dependents)
```

## How it Works

`ns-reload` works by tracking dependencies using information
returned by `ns-refers`, `ns-aliases` and `ns-imports`.

There are two sources of missing dependency information with this approach.

Firstly if a namespaces `require`s another namespace without an alias
and without referring anything, then the dependency is not recorded
anywhere.  `ns-reload` works around this by hooking
`clojure.core/load-lib` and always adding an alias in this situation.

Secondly, macros can inject dependencies into a namespace.
`ns-reload` provides a hook for `clojure.core/defmacro` that
adds a require for the namespace of any fully qualified symbol
returned by the macro.

These hooks are installed by the plugin for the repl task.

## Comparison with tools.namespace

`ns-reload` works by tracking dependencies from the data
available in namespaces themselves, without touching any files on
disk.  `tools.namespace` works by reading the `ns` forms from the
source files.


## License

Copyright © 2014 Hugo Duncan

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

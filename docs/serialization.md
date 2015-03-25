# Serialization

Since Firebase is a JSON-like store, we automatically convert keys in nested
maps to strings. No metadata about initial type is stored, and keys are
always read as keywords.

Maps are not restricted to just keywords and strings though, but the case of
rich keys has not been handled. On the JVM passing using such values will
result in a `ClassCastException`, and in JS you can always cast, so you'll
pull back a keyword, like `:32` or perhaps `:[object Object]`.

Coming to values, you're at the mercy of the platform and little work has
been done to manage the semantics. For basic data types they are at least
consistent between platforms, and mostly lossless or "almost-lossless".

We leverage `java.util.Collection` and `cljs->js` respectively between the
JVM and JS. That means that almost everything becomes an array, and most
primitives stay the same. The most notable difference is that `cljs->js`
turns keywords into strings, but we're making no such cast on the JVM.

Strings, booleans and keywords are stable, and stored either as the
associated JSON type, or as an EDN string in the case of keywords. We
may support symbols, dates etc also as EDN in future.

Numbers are mostly stable. For JS, floats-only is a gotcha but par for the course.
On the JVM Numbers are stable for the core cases of Long and Double, although  more
exotic types like `java.math.BigDec` will be cast down. One strange behaviour is that while
`4.0` will read back as a Double, `4M` will read back as a Long.

Records are saved as regular maps. Types defined with `deftype` also cast
down to maps, but their attributes for some reason are `under_scored` rather
than `kebab-styled`.

For more advanced types, you're likely to have a bad time. JavaScript will
probably do something very lossy, and Java to throw a type error.

If there's interest, some extensible system for adding readers/writers for
custom types could be added, probably looking something like those found in
the EDN or Transit libraries.

Otherwise you can manually wrap your writes and callbacks / channels.

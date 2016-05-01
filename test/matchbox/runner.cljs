(ns matchbox.runner
  (:require
    [doo.runner :refer-macros [doo-all-tests]]
    [matchbox.atom-test]
    [matchbox.common-test]
    [matchbox.core-test]
    [matchbox.registry-test]
    [matchbox.serialization-test]
    [matchbox.utils-test]))

(doo-all-tests)

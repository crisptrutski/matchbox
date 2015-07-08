(ns matchbox.runner
  (:require [matchbox.coerce-test]
            [matchbox.core-test]
            [doo.runner :refer-macros [doo-tests]]))

(enable-console-print!)

(doo-tests)

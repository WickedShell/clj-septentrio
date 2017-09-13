# clj-septentrio

A Clojure library to parse Septentrio binary protocol files

## Usage

Data is read from an input stream, and decoded messages are published on a channel.
The channel will be closed when the input stream ends

```
user=> (with-open [stream (io/input-stream "ASTER129.SBF")]
         (septentrio/parse stream ch))
```

## Supported Messages

| Message ID | Message Name | Notes |
| ---------- | ------------ | ----- |
| 4038       | ExtEventPVTGeodetic | Rev1 fields are not parsed |

## License

Copyright © 2017 Michael du Breuil

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

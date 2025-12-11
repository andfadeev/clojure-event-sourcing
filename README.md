# clojure-event-sourcing

This repo contains an example implementation of event sourcing in Clojure. We start with the most simple approach when we store a sequence of events and build resources in runtime on request. 

TODO: add select from events table

The next step is to introduce persistent projections which are calculated as we insert new events, also we will validate resources payloads with Malli schemas so we don't store invalid data.

TODO: add select from resources table

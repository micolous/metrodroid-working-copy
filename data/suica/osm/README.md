# osm

OSM extracts can be built with [Overpass Turbo][].

Queries can also be sent to `https://overpass-api.de/api/interpreter?data=`.

## Main query

Note: This returns about 8 MiB of data, and takes a couple of minutes to run.

```
// Setup a boundary for Japan
area["ISO3166-1"="JP"][admin_level=2][boundary=administrative]->.jp;

// Select all train routes in Japan with English and Japanese names.
(
  // route*=train: The route that a train service takes
  // https://wiki.openstreetmap.org/wiki/Tag:route%3Dtrain
  rel[route=train]["name"]["name:en"](area.jp);
  
  // route=railway: The physical infrastructure
  // (useful as a fallback)
  rel[route=railway]["name"]["name:en"](area.jp);

  // Other types of rail
  rel[route=light_rail]["name"]["name:en"](area.jp);
  rel[route=subway]["name"]["name:en"](area.jp);
) -> .routes;

// Also select parent route_masters
(
  rel(br.routes)[route_master];
  rel.routes;
) -> .routes;

// Output union
(
  // All train routes
  rel.routes;
  
  // All stations on those routes with English and Japanese names.
  node(r.routes)[railway=station]["name"]["name:en"];

  // Other types to include
  node(r.routes)[station]["name"]["name:en"];
  node(r.routes)[light_rail=yes]["name"]["name:en"];
  node(r.routes)[subway=yes]["name"]["name:en"];

  // Sometimes a station is listed as just a different sort of stop.
  // eg: https://www.openstreetmap.org/relation/5419188
  node(r.routes:"stop")["name"]["name:en"];
);

out;
```

## Example relations

* JR East
  * Yamanote line (loop)
    * inner track: https://www.openstreetmap.org/relation/1972960
    * outer track: https://www.openstreetmap.org/relation/1972920
    * parent relation: https://www.openstreetmap.org/relation/1139468
    * Harajuku station: https://www.openstreetmap.org/node/1579675430

* Kobe Municipal Transportation Bureau
  * Seishin-Yamate line (subway)
    * westbound: https://www.openstreetmap.org/relation/7720727
    * eastbound: https://www.openstreetmap.org/relation/7720717
    * parent relation: https://www.openstreetmap.org/relation/7720729


[overpass turbo]: https://overpass-turbo.eu/

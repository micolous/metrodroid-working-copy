#!/usr/bin/env python3.7
# -*- mode: python; indent-tabs-mode: nil; tab-width: 2; coding: utf-8 -*-
"""
data/suica/osm/sfcardfan_osm.py - Reads in SFCardFan data and attempts matching to OSM data

Copyright 2018-2019 Michael Farrell <micolous+git@gmail.com>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
"""

from __future__ import annotations

from argparse import ArgumentParser, FileType
from csv import DictWriter
from decimal import Decimal
from dataclasses import dataclass, field
from typing import Dict, Iterator, Optional, Set, Text, Union
import xml.etree.ElementTree as ET

# Data normalisation
STATION = '駅'  # eki
INSIDE_STATION = '駅構内'

HON = '本'
MAIN_LINE = '本線'
LINE = '線'

RAILWAY = '鉄道'
RAILWAY_LINE = '鉄道線'


def get_osm_tag(elem, key: Text) -> Optional[Text]:
    e = elem.find(f'.//tag[@k=\'{key}\']')
    if e is None:
        return None
    return e.attrib['v']


@dataclass
class OsmNode:
    osm_id: int
    lat: Decimal
    lon: Decimal
    name_en: Text
    name_ja: Text

    db: Optional[OsmDatabase] = None

    @classmethod
    def from_xml(cls, elem, db: Optional[OsmDatabase] = None) -> OsmNode:
        return cls(
            osm_id=int(elem.get('id')),
            lat=Decimal(elem.get('lat')),
            lon=Decimal(elem.get('lon')),
            name_en=get_osm_tag(elem, 'name:en'),
            name_ja=get_osm_tag(elem, 'name:ja') or get_osm_tag(elem, 'name'),
            db=db,
        )

    def __str__(self):
        return f'Stop #{self.osm_id}: {self.name_en} ({self.name_ja})'


@dataclass
class OsmRelation:
    osm_id: int
    train_route: bool
    route_master: bool
    name_en: Text
    name_ja: Text
    stop_ids: Set[int]
    relation_ids: Set[int]

    db: Optional[OsmDatabase] = None

    @classmethod
    def from_xml(cls, elem, db: Optional[OsmDatabase] = None):
        return cls(
            osm_id=int(elem.get('id')),
            train_route=get_osm_tag(elem, 'route') == 'train',
            route_master=get_osm_tag(elem, 'route_master') == 'train',
            name_en=get_osm_tag(elem, 'name:en'),
            name_ja=get_osm_tag(elem, 'name:ja') or get_osm_tag(elem, 'name'),
            stop_ids=frozenset(
                int(e.attrib['ref'])
                for e in elem.iterfind('.//member[@type=\'node\']')),
            relation_ids=frozenset(
                int(e.attrib['ref'])
                for e in elem.iterfind('.//member[@type=\'relation\']')),
            db=db,
        )

    @property
    def stops(self) -> Iterator[Union[OsmNode, int]]:
        """
        Iterates over stops, looking them up in db.

        If the stop id could not be found, returns int.
        """
        for stop_id in self.stop_ids:
            stop = self.db.nodes.get(stop_id)
            yield stop or stop_id

    @property
    def relations(self) -> Iterator[Union[OsmRelation, int]]:
        """
        Iterates over relations, looking them up in db.

        If the relation id could not be found, returns int.
        """
        for relation_id in self.relation_ids:
            relation = self.db.relations.get(relation_id)
            yield relation or relation_id

    def __str__(self):
        o = f'Relation #{self.osm_id}: {self.name_en} ({self.name_ja})\n'

        if self.route_master:
            o += 'Route master\n'
        if self.train_route:
            o += 'Train route\n'

        for r in self.relations:
            if isinstance(r, int):
                o += f'- Relation #{r}: (unknown)\n'
                continue

            first = True
            for line in str(r).split('\n'):
                if first:
                    o += f'- {line}\n'
                    first = False
                else:
                    o += f'  {line}\n'

        for s in self.stops:
            if isinstance(s, int):
                o += f'- Stop #{s}: (unknown)\n'
                continue

            first = True
            for line in str(s).split('\n'):
                if first:
                    o += f'- {line}\n'
                    first = False
                else:
                    o += f'  {line}\n'

        return o


@dataclass
class OsmDatabase:
    nodes: Dict[int, OsmNode] = field(default_factory=dict)
    relations: Dict[int, OsmRelation] = field(default_factory=dict)

    @classmethod
    def from_xml(cls, root) -> OsmDatabase:
        o = cls()

        for elem in root.iter('node'):
            node = OsmNode.from_xml(elem, db=o)
            o.nodes[node.osm_id] = node

        for elem in root.iter('relation'):
            relation = OsmRelation.from_xml(elem, db=o)
            o.relations[relation.osm_id] = relation

        return o

    @property
    def route_masters(self) -> Iterator[OsmRelation]:
        return filter(lambda r: r.route_master, self.relations.values())

    def __str__(self):
        return '<OsmDatabase>'


def read_osmdata(osm_xml_fn):
    tree = ET.parse(osm_xml_fn)
    db = OsmDatabase.from_xml(tree.getroot())

    for route_master in db.route_masters:
        print(route_master)






def main():
    parser = ArgumentParser()
    parser.add_argument('-x', '--osm_xml', required=True)
    # parser.add_argument('-s', '--sfcardfan_csv')
    options = parser.parse_args()

    read_osmdata(options.osm_xml)


if __name__ == '__main__':
    main()

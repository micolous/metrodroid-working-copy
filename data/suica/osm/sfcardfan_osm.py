#!/usr/bin/env python3.7
# -*- mode: python; indent-tabs-mode: nil; tab-width: 4; coding: utf-8 -*-
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
from csv import DictReader
from decimal import Decimal
from dataclasses import dataclass, field
from typing import Dict, Iterator, MutableSet, Optional, Set, Text, Tuple, Union
import xml.etree.ElementTree as ET

# Data normalisation
STATION = '駅'  # eki
INSIDE_STATION = '駅構内'

HON = '本'
MAIN_LINE = '本線'
LINE = '線'

RAILWAY = '鉄道'
RAILWAY_LINE = '鉄道線'
RAPID_LINE = '線快速'


ALT_NAMES = {
    '東海道本線': [  # Tokaido Main Line
        # JR East
        'JR東北本線',        # JR Tohoku Main Line
        'JR京浜東北線',      # JR Keihin-Tohoku Line
        'JR湘南新宿ライン',  # JR Shonan-Shinjuku Line

        # JR West
        'JR琵琶湖線',        # JR Biwako Line
    ],

    'いわて銀河': [
        # Iwate Galaxy Railway Line
        'アイジーアールいわて銀河鉄道いわて銀河鉄道線',
    ],

    '阿佐': [
        # Tosa Kuroshio Railway Asa Line
        '土佐くろしお鉄道阿佐線',
    ],

    '中村': [  # Nakamura
        # Tosa Kuroshio Railway Nakamura Line
        '土佐くろしお鉄道中村線',
    ],

    '鶴見': [  # Tsurumi
        # Nagahori Tsurumi-Ryokuchi (Osaka)
        '長堀鶴見緑地',
        # Tsurumi Line (Tokyo)
        'JR鶴見線',
    ],

    '江差': [  # Esashi (ex. JR Hokkaido)
        # TODO: fix company name
        # Dōnan Isaribi Tetsudō Line (South Hokkaido Railway)
        '道南いさりび鉄道線',
    ],

    '会津': [ # Aizu
        '会津鉄道会津線', # Aizu Railway Aizu Line
    ],

    '赤羽': [ # Akabane (station)
        # Data error, station there is Jujo (which only has Saikyo line)
        'JR埼京線',  # Saikyo line
    ],

    '真岡': [  # Mooka
        '真岡鐵道真岡線', # Mooka Railway Mooka Line
    ],

    '天竜浜名湖': [
        # Tenryu Hamanako Railroad
        '天竜浜名湖鉄道天竜浜名湖線',
    ],

    # '鹿児島本線': [
    #     'JR鹿児島本線',     # JR Kagoshima
    # ],
    # '鹿児島線': [
    #     'JR鹿児島本線',     # JR Kagoshima Main Line
    # ],
    # '日豊本線': [   # Nippo main line
    #     'JR日豊線',   # JR Nippo line
    # ],
    # '山陰本線': [   # Sanin Main Line
    #     'JR山陰線',  # JR Sanin Line
    # ],
    # '紀勢本線': [  # Kisei main line
    #     'JR紀勢線',  # JR Kisei line
    # ],
    #
    # '石北本線': [  # Sekihoku main line
    #     '石北線',  # Sekihoku line
    # ],
    # '根室本線': [  # Nemuro main line
    #     '根室線',
    # ],
}

# Characters that aren't important for testing
JUNK_CHARS = '・'

IGNORED_LINE_PREFIXES = (
    'JR ',  # double-named items
    'JR',
)

IGNORED_LINE_SUFFIXES = (
    MAIN_LINE,
    RAPID_LINE,
    RAILWAY_LINE,
    RAILWAY,
    HON,
    LINE,
)

# If a SFCardFan record contains one of these words, then ignore it
IGNORED_WORDS = (
    '試験',  # test
    'Test',
    '携帯端末',  # TODO: Mobile terminal
    'Suica',     # TODO: Mobile/internet suica
    '臨時窓口',  # TODO: temporary window

    # TODO: missing data in OSM:
    '天竜川',  # Tenryugawa
    '新居町',  # Araimachi
    '西小坂井',  # Nishi-Kozakai
    '幸田',    # Koda
    '名古屋',  # Nagoya
    '清洲',    # Kiyosu
    '岐阜',    # Gifu

    'ふるさと銀河',  # Furusato Ginga Line (closed)
)

IGNORED_WORDS_EXACT = (
    # TODO: Missing OSM data
    '彦根',  # Hikone
    '河瀬',  # Kawase
    '津軽',  # Tsugaru line (missing data)
    '海峡',  # Kaikyo line (missing data)
    '東海交通事業',  # Tokai Transport Service Company (missing data)
    '能登',  # Noto line (closed)
    '宮島航路',  # Miyajima ferry

)


def clean_sf_station(i: Text) -> Text:
    """Cleans up a SFCardFan station name to make it consistent."""
    if i.endswith(STATION):
        # If it ends with "station", remove the suffix
        i = i[:-len(STATION)]
    elif i.endswith(INSIDE_STATION):
        # If it ends with "inside station", remove the suffix
        i = i[:-len(INSIDE_STATION)]

    return i


def clean_sf_line(i: Text) -> Text:
    """Cleans up a SFCardFan line name to make it consistent with OSM."""
    if not i.endswith(LINE):
        # TODO: can probably replace previous thing with this.
        i += LINE
    for prefix in IGNORED_LINE_PREFIXES:
        if i.startswith(prefix):
            i = i[len(prefix):]

    for suffix in IGNORED_LINE_SUFFIXES:
        if i.endswith(suffix):
            i = i[:-len(suffix)]

    for c in JUNK_CHARS:
        if c in i:
            i = i.replace(c, '')

    return i


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
    wikidata: Optional[Text]

    db: Optional[OsmDatabase] = None

    @classmethod
    def from_xml(cls, elem, db: Optional[OsmDatabase] = None) -> OsmNode:
        return cls(
            osm_id=int(elem.get('id')),
            lat=Decimal(elem.get('lat')),
            lon=Decimal(elem.get('lon')),
            name_en=get_osm_tag(elem, 'name:en'),
            name_ja=get_osm_tag(elem, 'name:ja') or get_osm_tag(elem, 'name'),
            wikidata=get_osm_tag(elem, 'wikidata'),
            db=db,
        )

    def __str__(self):
        return f'Node #{self.osm_id}: {self.name_en} ({self.name_ja})'

    def __repr__(self):
        return (f'<OsmNode: #{self.osm_id}, en={self.name_en}, '
                f'ja={self.name_ja}>')

@dataclass
class OsmRelation:
    osm_id: int
    route_master: bool
    route: bool
    name_en: Text
    name_ja: Text
    stop_ids: Set[int]
    relation_ids: Set[int]
    wikidata: Optional[Text]

    operator_en: Optional[Text]
    operator_ja: Optional[Text]

    db: Optional[OsmDatabase] = None

    @classmethod
    def from_xml(cls, elem, db: Optional[OsmDatabase] = None):
        return cls(
            osm_id=int(elem.get('id')),
            route_master=get_osm_tag(elem, 'type') == 'route_master',
            route=get_osm_tag(elem, 'type') == 'route',
            name_en=get_osm_tag(elem, 'name:en'),
            name_ja=get_osm_tag(elem, 'name:ja') or get_osm_tag(elem, 'name'),
            operator_en=get_osm_tag(elem, 'operator:en'),
            operator_ja=(get_osm_tag(elem, 'operator:ja') or
                         get_osm_tag(elem, 'operator')),
            stop_ids=frozenset(
                int(e.attrib['ref'])
                for e in elem.iterfind('.//member[@type=\'node\']')),
            relation_ids=frozenset(
                int(e.attrib['ref'])
                for e in elem.iterfind('.//member[@type=\'relation\']')),
            wikidata=get_osm_tag(elem, 'wikidata'),
            db=db,
        )

    def all_stop_ids(self, _current_depth: int = 0) -> Set[int]:
        """Recursively fetch all stop IDs under this relation."""
        stops = set(self.stop_ids)
        _current_depth += 1

        if _current_depth > 10:
            # Limit recursion.
            return stops

        for rel in self.relations:
            if isinstance(rel, OsmRelation):
                stops |= rel.all_stop_ids(_current_depth=_current_depth)

        return stops

    def _lookup_stops(self, stop_ids: Iterator[int]) -> Iterator[OsmNode]:
        """Looks up stops, skipping any unknown stops."""
        for stop_id in stop_ids:
            stop = self.db.nodes.get(stop_id)
            if stop:
                yield stop

    @property
    def stops(self) -> Iterator[OsmNode]:
        """
        Iterates over stops, looking them up in db.

        If the stop id could not be found, returns int.
        """
        return self._lookup_stops(self.stop_ids)

    def all_stops(self) -> Iterator[OsmNode]:
        """Recursively fetch all stop IDs under this relation."""
        return self._lookup_stops(self.all_stop_ids())

    def name_matches(self, v: Text) -> bool:
        if v == self.name_ja:
            return True

        # These are sometimes swapped
        alt_names = ALT_NAMES.get(v)
        if alt_names and (self.name_ja in alt_names):
            return True

        name = self.name_ja

        # try some alternates
        for prefix in IGNORED_LINE_PREFIXES:
            lp = len(prefix)
            if v.startswith(prefix) and v[lp:] == self.name_ja:
                return True
            if name.startswith(prefix):
                name = name[lp:]
                if v == name:
                    return True

        for suffix in IGNORED_LINE_SUFFIXES:
            ls = -len(suffix)
            # if v.endswith(suffix) and v[:ls] == self.name_ja:
            #     return True
            if name.endswith(suffix) and v == name[:ls]:
                return True

        # no match
        return False


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

    @classmethod
    def read_file(cls, fn: Text) -> OsmDatabase:
        tree = ET.parse(fn)
        return cls.from_xml(tree.getroot())

    def route_masters(self) -> Iterator[OsmRelation]:
        return filter(lambda r: r.route_master, self.relations.values())

    def routes(self) -> Iterator[OsmRelation]:
        return filter(lambda r: r.route, self.relations.values())

    def __repr__(self):
        return '<OsmDatabase>'


@dataclass
class OperatorInfo:
    name_en: Text
    name_ja: Text

    long_name_en: Optional[Text] = None
    long_name_ja: Optional[Text] = None

    alt_names: Set[Text] = field(default_factory=frozenset)

    @classmethod
    def read_row(cls, row: Dict[Text, Text]):
        alt_names = row['alt_names'] or ''
        return cls(
            name_en=row['name_en'],
            name_ja=row['name_ja'],
            long_name_en=row['long_name_en'],
            long_name_ja=row['long_name_ja'],
            alt_names=frozenset(n.strip() for n in alt_names.split('|')),
        )

    @property
    def all_names(self) -> Set[Text]:
        """Returns all of the names of this operator."""
        s = set([self.name_en, self.name_ja])
        s |= self.alt_names

        if self.long_name_en is not None:
            s.add(self.long_name_en)
        if self.long_name_ja is not None:
            s.add(self.long_name_ja)

        return s


class Operators:
    """Database of all OperatorInfo"""

    def __init__(self, fh):
        c = DictReader(fh)
        self._operators = [OperatorInfo.read_row(row) for row in c]

        # Build lookup table
        self._name_dict = {}  # type: Dict[Text, OperatorInfo]
        for operator in self._operators:
            for name in operator.all_names:
                self._name_dict[name] = operator

    def get_operator(self, name: Text) -> Optional[OperatorInfo]:
        return self._name_dict.get(name)


def read_osmdata(
    osm_xml_fn: Text, sfcard_csv_fn: Text, operators_csv_fn: Text):

    with open(operators_csv_fn, newline='') as operators_csv:
        operators_db = Operators(operators_csv)

    osm_db = OsmDatabase.read_file(osm_xml_fn)


    with open(sfcard_csv_fn, newline='') as sfcard_csv:
        sfcard_db = DictReader(sfcard_csv)

        last_company = None
        last_line = None
        last_rms = None
        last_operator_info = None

        for row in sfcard_db:
            if row['src'] != 'suica_rail':
                # TODO
                continue

            # Select distinct area+line code
            company_name = row['company_name']
            line_name = clean_sf_line(row['line_name'])
            station_name = clean_sf_station(row['station_name'])

            all_names = ' '.join([company_name, line_name, station_name])

            if any(word in all_names for word in IGNORED_WORDS):
                # bad word, skip record
                continue
            if any(word in (company_name, line_name, station_name)
                   for word in IGNORED_WORDS_EXACT):
                # bad word
                continue

            # TODO: implement other things, this is Yamanote
            # if '山手' not in line_name:
            #     continue

            if last_company == company_name and last_line == line_name:
                rms = last_rms
                operator_info = last_operator_info
            else:
                # Find the route_master
                rms = [m for m in osm_db.route_masters()
                       if m.name_matches(line_name)] + [
                    m for m in osm_db.routes()
                    if m.name_matches(line_name)]

                if not rms:
                    print(f'company: {company_name}, line: {line_name}, station: {station_name}')
                    print(f'area/line/station: '
                          f'{row["area_code"]},{row["line_code"]},'
                          f'{row["station_code"]}')

                    raise ValueError(f'no route_master found')

                last_rms = rms
                last_company = company_name
                last_line = line_name
                operator_info = last_operator_info = operators_db.get_operator(
                    company_name)

            # Now look for the station in this route_master
            stations = []
            for rm in rms:
                stations += [s for s in rm.all_stops()
                             if s.name_ja == station_name]

            if not stations:

                print(f'company: {company_name}, line: {line_name}, station: {station_name}')
                print(f'area/line/station: '
                      f'{row["area_code"]},{row["line_code"]},'
                      f'{row["station_code"]}')
                continue  # FIXME

                for rm in rms:
                    print(f'  route_master #{rm.osm_id}: {rm.name_en} '
                          f'({rm.name_ja})')
                    print(str(rm))
                raise ValueError(f'station not found {station_name}')

            # Pick the first station
            stations.sort(key=lambda s: s.osm_id)
            station = stations[0]

            # pick the correct route_master
            rm = [r for r in rms if station.osm_id in r.all_stop_ids()][0]
            if not operator_info:
                operator_info = last_operator_info = OperatorInfo(
                    name_en=rm.operator_en,
                    name_ja=rm.operator_ja or company_name)


        # now dump all details
            print(f'area_code: {row["area_code"]}, '
                  f'line_code: {row["line_code"]}, '
                  f'station_code: {row["station_code"]}')
            print(f'  rmaster#{rm.osm_id}: {rm.name_en} ({rm.name_ja})')
            print(f'  operator: {operator_info.name_en} '
                  f'({operator_info.name_ja})')
            print(f'  station#{station.osm_id}: {station.name_en}'
                  f' ({station.name_ja})')

            print(f'  {station.lat}, {station.lon}')


    # Now do matching?
    return




    for route_master in osm_db.route_masters():
        print(route_master)






def main():
    parser = ArgumentParser()
    parser.add_argument('-x', '--osm_xml', required=True)
    parser.add_argument('-s', '--sfcardfan_csv', required=True)
    parser.add_argument('-O', '--operators_csv', required=True)
    options = parser.parse_args()

    read_osmdata(options.osm_xml, options.sfcardfan_csv, options.operators_csv)


if __name__ == '__main__':
    main()

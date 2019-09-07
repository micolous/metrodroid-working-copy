#!/usr/bin/env python3
# -*- mode: python; indent-tabs-mode: nil; tab-width: 2 -*-
"""
translator_credits.py

This script scans through Metrodroid's resources directory to find who has
actively used translations in Metrodroid, as well as their email address.

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

import hashlib
import os
import os.path
import subprocess
import yaml

METRODROID_HOME = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESDIR = os.path.join(METRODROID_HOME, 'src', 'main', 'res')
XMLS = ('strings.xml', 'market.xml')
SETTINGS_FN = os.path.join(METRODROID_HOME, 'extra', 'translator_credits.yaml')


class TranslatorSettings:
    def __init__(self):
        with open(SETTINGS_FN, 'rb') as fh:
            self._s = yaml.safe_load(fh)

        self._l = {}
        for e in self._s['translator_credits']:
            for m in e['match']:
                self._l[m] = e

    def get(self, d):
        return self._l.get(d)



def resources_dirs():
    with os.scandir(RESDIR) as it:
        for p in it:
            s = os.path.join(p.path, XMLS[0])
            if (p.is_dir(follow_symlinks=False) and os.path.isfile(s) and
                not os.path.islink(s) and p.name.startswith('values-') and
                not p.name.startswith('values-en') and 'rXA' not in p.name and
                'rXB' not in p.name):
                yield p


def get_authors_for_file(filename):
    p = subprocess.run(['git', 'annotate', '--incremental', filename],
                       check=True, input='', capture_output=True, text=True)

    authors = set()
    userids = set()
    author = None
    for l in p.stdout.split('\n'):
        if l.startswith('author '):
            author = l[7:]
            continue
        elif l.startswith('author-mail '):
            if author is None:
                raise Exception('Unexpected author-mail before author')

            # partial hash of: author-mail <{mail}>
            userid = hashlib.sha1((l).encode()).hexdigest()[5:15]
            if userid in userids:
                continue

            userids.add(userid)
            authors.add((author, userid))
            author = None

    return authors


settings = TranslatorSettings()

for d in sorted(resources_dirs(), key=lambda x: x.name):
    lang = d.name[7:]

    authors = set()

    for x in XMLS:
        s = os.path.join(d.path, x)
        if os.path.isfile(s) and not os.path.islink(s):
            authors |= get_authors_for_file(s)

    links = set()
    for name, userid in authors:
        prefs = settings.get(userid)
        if prefs:
            if 'only' in prefs and lang not in prefs['only']:
                continue
            if 'handle' in prefs and prefs['handle']:
                name = prefs['github']

            links.add(f'[{name}](https://github.com/{prefs["github"]})')
        else:
            print(f'No preference found for {name} ({userid})')
            links.add(name)

    if not links:
        continue

    print(f'* **{lang}**: {", ".join(sorted(links))}')

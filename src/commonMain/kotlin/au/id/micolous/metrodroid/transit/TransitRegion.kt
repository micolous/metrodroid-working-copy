/*
 * TransitRegion.kt
 *
 * Copyright 2019 Google
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package au.id.micolous.metrodroid.transit

import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.multi.StringResource
import au.id.micolous.metrodroid.util.iso3166AlphaToName
import au.id.micolous.metrodroid.util.Collator

sealed class TransitRegion {
    abstract val translatedName: String
    open val sortingKey: Pair<Int, String>
        get() = Pair(0, translatedName)

    data class Iso (val code: String): TransitRegion () {
        override val translatedName: String
            get() = iso3166AlphaToName(code) ?: code
    }

    data class Custom(val res: StringResource): TransitRegion () {
        override val translatedName: String
            get() = Localizer.localizeString(res)
    }

    object China : TransitRegion () {
        override val translatedName: String
            get() = Localizer.localizeString(R.string.location_china_mainland)
        override val sortingKey
            get() = Pair(0, iso3166AlphaToName("CN") ?: translatedName)
    }

    object RegionComparator : Comparator<TransitRegion> {
        val collator = Collator.collator
        override fun compare(a: TransitRegion, b: TransitRegion): Int {
            val ak = a.sortingKey
            val bk = b.sortingKey
            if (ak.first != bk.first) {
                return ak.first.compareTo(bk.first)
            }
            return collator.compare(ak.second, bk.second)
        }
    }

    data class SectionItem(val res: StringResource,
                           val section: Int): TransitRegion () {
        override val translatedName: String
            get() = Localizer.localizeString(res)
        override val sortingKey
            get() = Pair(section, translatedName)
    }

    companion object {
        val XX = SectionItem(R.string.unknown, -2)
        val AUSTRALIA = Iso("AU")
        val BELGIUM = Iso("BE")
        val BRAZIL = Iso("BR")
        val CANADA = Iso("CA")
        val CHILE = Iso("CL")
        val CHINA = China
        val CRIMEA = Custom(R.string.location_crimea)
        val DENMARK = Iso("DK")
        val ESTONIA = Iso("EE")
        val FINLAND = Iso("FI")
        val FRANCE = Iso("FR")
        val GEORGIA = Iso("GE")
        val GERMANY = Iso("DE")
        val HONG_KONG = Iso("HK")
        val INDONESIA = Iso("ID")
        val IRELAND = Iso("IE")
        val ISRAEL = Iso("IL")
        val ITALY = Iso("IT")
        val JAPAN = Iso("JP")
        val MALAYSIA = Iso("MY")
        val NETHERLANDS = Iso("NL")
        val NEW_ZEALAND = Iso("NZ")
        val POLAND = Iso("PL")
        val PORTUGAL = Iso("PT")
        val RUSSIA = Iso("RU")
        val SINGAPORE = Iso("SG")
        val SOUTH_AFRICA = Iso("ZA")
        val SOUTH_KOREA = Iso("KR")
        val SPAIN = Iso("ES")
        val SWEDEN = Iso("SE")
        val SWITZERLAND = Iso("CH")
        val TAIPEI = Custom(R.string.location_taipei)
        val TURKEY = Iso("TR")
        val UAE = Iso("AE")
        val UK = Iso("GB")
        val UKRAINE = Iso("UA")
        val USA = Iso("US")
        val WORLDWIDE = SectionItem(R.string.location_worldwide, -1)
    }
}

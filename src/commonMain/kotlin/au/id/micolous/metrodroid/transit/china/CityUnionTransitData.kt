/*
 * NewShenzhenTransitData.kt
 *
 * Copyright 2018 Google
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

package au.id.micolous.metrodroid.transit.china

import au.id.micolous.metrodroid.card.CardType
import au.id.micolous.metrodroid.card.china.ChinaCard
import au.id.micolous.metrodroid.card.china.ChinaCardTransitFactory
import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.transit.*
import au.id.micolous.metrodroid.ui.ListItem
import au.id.micolous.metrodroid.util.ImmutableByteArray

// Reference: https://github.com/sinpolib/nfcard/blob/master/src/com/sinpo/xnfc/nfc/reader/pboc/CityUnion.java
@Parcelize
class CityUnionTransitData (val validityStart: Int?,
                            val validityEnd: Int?,
                            override val trips: List<ChinaTrip>?,
                            val mBalance: Int?,
                            private val mSerial: Int?,
                            private val mCity: Int?): TransitData() {

    public override val balance: TransitBalance?
        get() = if (mBalance != null)
            TransitBalanceStored(TransitCurrency.CNY(mBalance),
                    null,
                    ChinaTransitData.parseHexDate(validityStart),
                    ChinaTransitData.parseHexDate(validityEnd))
        else
            null

    override val serialNumber: String
        get() = mSerial.toString()

    override val cardName: String
        get() = nameCity(mCity)

    override val info: List<ListItem>?
        get() {
            if (mCity == null)
                return null
            val locId = cities[mCity]?.locationId
            if (locId != null)
                return listOf(ListItem (R.string.city_union_city, locId))
            return listOf(ListItem (R.string.city_union_city,
                    Localizer.localizeString(R.string.unknown_format, mCity.toString(16))))
        }

    companion object {
        private const val SHANGHAI = 0x2000
        private val cities = mapOf(
                SHANGHAI to CardInfo(
                        name = R.string.card_name_shanghai,
                        locationId = R.string.location_shanghai,
                        imageId = R.drawable.shanghai,
                        imageAlphaId = R.drawable.iso7810_id1_alpha,
                        cardType = CardType.ISO7816,
                        region = TransitRegion.CHINA,
                        preview = true
                )

        )
        private fun parse (card: ChinaCard): CityUnionTransitData {
            val file15 = ChinaTransitData.getFile(card, 0x15)?.binaryData
            val (serial, city) = parseSerialAndCity(card)

            return CityUnionTransitData(mSerial = serial,
                    validityStart = file15?.byteArrayToInt(20, 4),
                    validityEnd = file15?.byteArrayToInt(24, 4),
                    mCity = city,
                    mBalance = ChinaTransitData.parseBalance(card),
                    trips = ChinaTransitData.parseTrips(card) { ChinaTrip(it)} )
        }

        private val CARD_INFO = CardInfo(
                name = R.string.card_name_cityunion,
                locationId = R.string.location_china_mainland,
                imageId = R.drawable.city_union,
                cardType = CardType.ISO7816,
                region = TransitRegion.CHINA,
                preview = true)

        val FACTORY: ChinaCardTransitFactory = object : ChinaCardTransitFactory {
            override val appNames: List<ImmutableByteArray>
                get() = listOf(ImmutableByteArray.fromHex("A00000000386980701"))

            override val allCards: List<CardInfo>
                get() = listOf(CARD_INFO) + cities.values

            override fun parseTransitIdentity(card: ChinaCard): TransitIdentity {
                val (serial, city) = parseSerialAndCity(card)
                return TransitIdentity(nameCity(city), serial.toString())
            }

            override fun parseTransitData(card: ChinaCard) = parse(card)
        }

        private fun nameCity(city: Int?): String = cities[city]?.name ?: Localizer.localizeString(R.string.card_name_cityunion)

        private fun parseSerialAndCity(card: ChinaCard): Pair<Int?, Int?> {
            val file15 = ChinaTransitData.getFile(card, 0x15)?.binaryData
            val city = file15?.byteArrayToInt(2, 2)
            if (city == SHANGHAI)
                return Pair(file15.byteArrayToInt(16, 4), city)
            return Pair(file15?.byteArrayToIntReversed(16, 4) ?: 0, city)
        }
    }
}

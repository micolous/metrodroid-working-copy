/*
 * MspGotoTransitData.kt
 *
 * Copyright 2018-2019 Google
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

package au.id.micolous.metrodroid.transit.msp_goto

import au.id.micolous.metrodroid.card.CardType
import au.id.micolous.metrodroid.card.classic.ClassicCard
import au.id.micolous.metrodroid.card.classic.ClassicCardTransitFactory
import au.id.micolous.metrodroid.card.classic.ClassicSector
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.time.MetroTimeZone
import au.id.micolous.metrodroid.transit.CardInfo
import au.id.micolous.metrodroid.transit.TransitCurrency.Companion.USD
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.transit.TransitRegion
import au.id.micolous.metrodroid.transit.nextfare.NextfareTransitData
import au.id.micolous.metrodroid.transit.nextfare.NextfareTransitDataCapsule
import au.id.micolous.metrodroid.transit.nextfare.NextfareTripCapsule
import au.id.micolous.metrodroid.util.ImmutableByteArray

@Parcelize
class MspGotoTransitData (override val capsule: NextfareTransitDataCapsule): NextfareTransitData() {

    override val cardName: String
        get() = NAME

    override val timezone: MetroTimeZone
        get() = TIME_ZONE

    override val currency
        get() = ::USD

    companion object {
        private const val NAME = "Go-To card"
        private val BLOCK1 = ImmutableByteArray.fromHex(
                "16181A1B1C1D1E1F010101010101"
        )
        val BLOCK2 = ImmutableByteArray.fromHex(
                "3f332211c0ccddee3f33221101fe01fe"
        )

        private val CARD_INFO = CardInfo(
                // Using the short name (Goto) may be ambiguous
                name = NAME,
                locationId = R.string.location_minneapolis,
                cardType = CardType.MifareClassic,
                imageId = R.drawable.msp_goto_card,
                imageAlphaId = R.drawable.iso7810_id1_alpha,
                keysRequired = true,
                region = TransitRegion.USA,
                preview = true)

        private val TIME_ZONE = MetroTimeZone.CHICAGO

        val FACTORY: ClassicCardTransitFactory = object : NextfareTransitData.NextFareTransitFactory() {

            override val allCards: List<CardInfo>
                get() = listOf(CARD_INFO)

            override fun parseTransitIdentity(card: ClassicCard): TransitIdentity {
                return super.parseTransitIdentity(card, NAME)
            }

            override fun earlyCheck(sectors: List<ClassicSector>): Boolean {
                val sector0 = sectors[0]
                val block1 = sector0.getBlock(1).data
                if (!block1.copyOfRange(1, 15).contentEquals(BLOCK1)) {
                    return false
                }

                val block2 = sector0.getBlock(2).data
                return block2.contentEquals(BLOCK2)
            }

            override fun parseTransitData(card: ClassicCard): TransitData {
                val capsule = parse(
                        card = card,
                        timeZone = TIME_ZONE,
                        newTrip = { capsule -> MspGotoTrip(capsule) },
                        newRefill = { MspGotoTrip(NextfareTripCapsule(it)) },
                        shouldMergeJourneys = false)
                return MspGotoTransitData(capsule)
            }
        }
    }
}

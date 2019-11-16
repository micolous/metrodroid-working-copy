/*
 * SmartCard.kt
 *
 * Copyright 2019 Michael Farrell <micolous+git@gmail.com>
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
package au.id.micolous.metrodroid.card

import au.id.micolous.metrodroid.card.classic.ClassicCardTech
import au.id.micolous.metrodroid.key.ClassicSectorKey
import au.id.micolous.metrodroid.util.ImmutableByteArray


class JavaClassicTransceiver private constructor(
    transceiver: JavaCardTransceiver) : ClassicCardTech {
    private val transceiver = PN53xTransceiver(transceiver)

    override suspend fun authenticate(sectorIndex: Int, key: ClassicSectorKey): Boolean {
        val cmd = ImmutableByteArray.ofB(
            when (key.type) {
                ClassicSectorKey.KeyType.B -> 0x61
                else -> 0x60
            },
            sectorToBlock(sectorIndex)
        ) + tagId.sliceOffLen(0, 4) + key.key.sliceOffLen(0, 6)

        return transceiver.transceive(cmd).isNotEmpty()
    }

    override val sectorCount: Int
        get() = 16 // TODO
    override val tagId: ImmutableByteArray
        get() = transceiver.uid!!

    override suspend fun readBlock(block: Int): ImmutableByteArray {
        require(block in 0..sectorToBlock(sectorCount)) {
            "block must be in 0-${sectorToBlock(sectorCount)}" }
        val cmd = ImmutableByteArray.ofB(0x30, block)
        return transceiver.transceive(cmd)
    }

    override fun getBlockCountInSector(sectorIndex: Int): Int {
        require(sectorIndex in 0..40) { "sector must be 0-40" }
        return when {
            sectorIndex < 32 -> 4
            else -> 16
        }
    }

    override fun sectorToBlock(sectorIndex: Int): Int {
        require(sectorIndex in 0..40) { "sector must be 0-40" }
        return when {
            sectorIndex < 32 -> sectorIndex * 4
            else -> (sectorIndex * 16) - 384
        }
    }


    private fun requireClassic() {
        require(transceiver.cardType == CardType.MifareClassic) { "cardType != MifareClassic" }
    }

    companion object {
        fun wrap(transceiver: JavaCardTransceiver): JavaClassicTransceiver {
            val v = JavaClassicTransceiver(transceiver)
            v.requireClassic()
            return v
        }
    }
}

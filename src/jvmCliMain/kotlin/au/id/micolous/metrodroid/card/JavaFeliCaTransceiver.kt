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

import au.id.micolous.metrodroid.card.felica.FelicaTransceiver
import au.id.micolous.metrodroid.util.ImmutableByteArray

class JavaFeliCaTransceiver private constructor(
    transceiver: JavaCardTransceiver) : FelicaTransceiver {
    private val transceiver = PN53xTransceiver(transceiver)

    override val uid: ImmutableByteArray?
        get() = transceiver.uid

    override suspend fun transceive(data: ImmutableByteArray): ImmutableByteArray {
        requireFeliCa()
        return transceiver.transceive(data)
    }

    override val defaultSystemCode: Int? = null // TODO

    private fun requireFeliCa() {
        require(transceiver.cardType == CardType.FeliCa) { "cardType != FeliCa" }
        val uid = transceiver.uid
        require(uid != null) { "IDm (uid) must be set for FeliCa"}
        require(uid.count() == 8) {
            "IDm (uid) must be 8 bytes for FeliCa, got: [${uid.getHexString()}]"}
    }

    companion object {
        fun wrap(transceiver: JavaCardTransceiver): JavaFeliCaTransceiver {
            val v = JavaFeliCaTransceiver(transceiver)
            v.requireFeliCa()
            return v
        }
    }
}

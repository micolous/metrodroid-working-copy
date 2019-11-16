/*
 * PN53xTransceiver.kt
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

import au.id.micolous.metrodroid.util.ImmutableByteArray

private val PN53X_RX = ImmutableByteArray.fromHex("d541")
private val PN53X_TX = ImmutableByteArray.fromHex("d44001")
private val DIRECT_COMMS = ImmutableByteArray.fromHex("ff000000")

internal class PN53xTransceiver(private val transceiver: JavaCardTransceiver)
    : JavaCardTransceieverIntf by transceiver {
    override suspend fun transceive(data: ImmutableByteArray): ImmutableByteArray {
        val tx = DIRECT_COMMS + (PN53X_TX.count() + data.count()).toByte() +
            PN53X_TX + data

        val rx = transceiver.transceive(tx)
        return if (rx.startsWith(PN53X_RX) && rx.count() >= 5) {
            rx.sliceOffLen(3, rx.count() - 5)
        } else {
            throw CardTransceiveException("Unexpected response: ${rx.toHexString()}")
        }
    }
}


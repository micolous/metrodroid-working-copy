package au.id.micolous.metrodroid.cli

import au.id.micolous.kotlin.pcsc.*
import au.id.micolous.metrodroid.util.ImmutableByteArray

private val CLEAR_SCREEN = ImmutableByteArray.fromHex("ff00600000").toByteArray()
private val WRITE_TEXT = ImmutableByteArray.fromHex("ff0068").toByteArray()

class Acr123(private val card: Card) {
    private fun control(sendBuffer: ByteArray? = null, recvBufferSize: Int = 300)
        = card.control(0x3136b0, sendBuffer, recvBufferSize)

    fun clearScreen() {
        control(CLEAR_SCREEN)
    }

    fun writeText(text: String, x: Int = 0, y: Int = 0) {
        val position = (((y and 0x7) shl 4) or (x and 0xf)).toByte()
        val encodedText = ImmutableByteArray.fromASCII(text)
        val bytesText = if (encodedText.size > 16) {
            encodedText.sliceOffLen(0, 16)
        } else {
            encodedText
        }.toByteArray()

        val textLength = bytesText.size.toByte()

        control(WRITE_TEXT + byteArrayOf(position, textLength) + bytesText)
    }

    companion object {
        fun connect(context: Context) : Acr123 {
            val reader = "ACS ACR123 3S Reader [ACR123U-PICC] (1.00.xx) 00 00" // "ACS ACR123 3S Reader(1)"
            return Acr123(context.connect(reader, ShareMode.Direct, Protocol.Raw))
        }
    }
}

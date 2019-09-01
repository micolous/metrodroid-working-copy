/*
 * RkfTransitData.kt
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

package au.id.micolous.metrodroid.transit.rkf

import au.id.micolous.metrodroid.card.classic.ClassicCard
import au.id.micolous.metrodroid.card.classic.ClassicCardTransitFactory
import au.id.micolous.metrodroid.card.classic.ClassicSector
import au.id.micolous.metrodroid.card.CardType
import au.id.micolous.metrodroid.multi.*
import au.id.micolous.metrodroid.time.Timestamp
import au.id.micolous.metrodroid.time.TimestampFormatter
import au.id.micolous.metrodroid.transit.*
import au.id.micolous.metrodroid.transit.en1545.*
import au.id.micolous.metrodroid.ui.ListItem
import au.id.micolous.metrodroid.transit.en1545.En1545FixedInteger
import au.id.micolous.metrodroid.ui.HeaderListItem
import au.id.micolous.metrodroid.util.HashUtils
import au.id.micolous.metrodroid.util.NumberUtils
import au.id.micolous.metrodroid.util.ImmutableByteArray

@Parcelize
internal data class RkfSerial(val mCompany: Int, val mCustomerNumber: Long, val mHwSerial: Long) : Parcelable {
    val formatted: String
        get() = when (mCompany) {
            RkfLookup.REJSEKORT -> {
                val main = "30843" + NumberUtils.formatNumber(mCustomerNumber, " ", 1, 3, 3, 3)
                main + " " + NumberUtils.calculateLuhn(main.replace(" ", ""))
            }
            RkfLookup.SLACCESS -> {
                NumberUtils.formatNumber(mHwSerial, " ", 5, 5)
            }
            else -> mHwSerial.toString()
        }
}

sealed class RkfRecord

data class RkfSimpleRecord(val raw: ImmutableByteArray) : RkfRecord()

data class RkfTctoRecord(val chunks: List<List<ImmutableByteArray>>) : RkfRecord()

// Specification: https://github.com/mchro/RejsekortReader/tree/master/resekortsforeningen
@Parcelize
data class RkfTransitData internal constructor(
        private val mTcci: En1545Parsed,
        private val mTrips: List<Trip>,
        private val mBalances: List<RkfPurse>,
        private val mLookup: RkfLookup,
        private val mTccps: List<En1545Parsed>,
        private val mSerial: RkfSerial,
        override val subscriptions: List<RkfTicket>) : TransitData() {
    override val cardName get(): String = issuerMap[aid]?.name ?: "RKF"

    private val aid
        get() = mTcci.getIntOrZero(En1545TransitData.ENV_APPLICATION_ISSUER_ID)

    override val serialNumber get() = mSerial.formatted

    override val trips get() = mTrips

    // Filter out ghost purse on Rejsekort unless it was ever used (is it ever?)
    override val balances get() = mBalances.withIndex().filter { (idx, bal) ->
        aid != RkfLookup.REJSEKORT
                || idx != 1 || bal.transactionNumber != 0
    }
            .map { (_, bal) -> bal.balance }

    @VisibleForTesting
    val issuer
        get() = mLookup.getAgencyName(mTcci.getIntOrZero(En1545TransitData.ENV_APPLICATION_ISSUER_ID), false)

    private val expiryDate: Timestamp?
        get() = mTcci.getTimeStamp(En1545TransitData.ENV_APPLICATION_VALIDITY_END, mLookup.timeZone)

    @VisibleForTesting
    val cardStatus: StringResource
        get() = when (mTcci.getIntOrZero(STATUS)) {
            0x01 -> R.string.rkf_status_ok
            0x21 -> R.string.rkf_status_action_pending
            0x3f -> R.string.rkf_status_temp_disabled
            0x58 -> R.string.rkf_status_not_ok
            else -> R.string.unknown_format
        }

    private val expiryDateInfo: ListItem?
        get() {
            return ListItem(R.string.expiry_date,
                    TimestampFormatter.longDateFormat(expiryDate ?: return null))
        }
    override val info get() = listOfNotNull(expiryDateInfo) + listOf(ListItem(R.string.card_issuer, issuer),
            if (cardStatus == R.string.unknown_format) {
                ListItem(R.string.rkf_card_status, Localizer.localizeString(R.string.unknown_format,
                        NumberUtils.intToHex(mTcci.getIntOrZero(STATUS))))
            } else {
                ListItem(R.string.rkf_card_status, cardStatus)
            })

    override fun getRawFields(level: RawLevel): List<ListItem>? {
        return mBalances.mapIndexed { index, rkfPurse -> listOf(HeaderListItem("Purse $index")) + rkfPurse.getRawFields(level) }.flatten() +
                subscriptions.mapIndexed { index, rkfPurse -> listOf(HeaderListItem("Ticket $index")) + rkfPurse.getRawFields(level).orEmpty() }.flatten()
    }

    companion object {
        private val issuerMap = mapOf(
                RkfLookup.SLACCESS to CardInfo(
                        name = "SLaccess",
                        locationId = R.string.location_stockholm,
                        cardType = CardType.MifareClassic,
                        imageId = R.drawable.slaccess,
                        keysRequired = true, keyBundle = "slaccess",
                        region = TransitRegion.SWEDEN,
                        preview = true),
                RkfLookup.REJSEKORT to CardInfo(
                        name = "Rejsekort",
                        locationId = R.string.location_denmark,
                        imageId = R.drawable.rejsekort,
                        cardType = CardType.MifareClassic,
                        keysRequired = true, keyBundle = "rejsekort",
                        region = TransitRegion.DENMARK,
                        preview = true)
        )

        val FACTORY = object : ClassicCardTransitFactory {
            override fun earlyCardInfo(sectors: List<ClassicSector>) = issuerMap[getIssuer(sectors[0])]

            override fun earlyCheck(sectors: List<ClassicSector>) =
                    HashUtils.checkKeyHash(sectors[0], "rkf",
                            // Most cards
                            "b9ae9b2f6855aa199b4af7bdc130ba1c",
                            "2107bb612627fb1dfe57348fea8a8b58",
                            // Jo-jo
                            "f40bb9394d94c7040c1dd19997b4f5e8") >= 0

            override val earlySectors get() = 1

            override val allCards get() = issuerMap.values.toList()

            override fun parseTransitIdentity(card: ClassicCard): TransitIdentity {
                val serial = getSerial(card)
                val issuerName = issuerMap[serial.mCompany]?.name ?: "RKF"
                return TransitIdentity(issuerName, serial.formatted)
            }

            override fun parseTransitData(card: ClassicCard): RkfTransitData {
                val tcciRaw = card[0, 1].data
                val tcci = En1545Parser.parseLeBits(tcciRaw, 0, TCCI_FIELDS)
                val tripVersion = tcci.getIntOrZero(EVENT_LOG_VERSION)
                val currency = tcci.getIntOrZero(CURRENCY)
                val company = tcci.getIntOrZero(En1545TransitData.ENV_APPLICATION_ISSUER_ID)
                val lookup = RkfLookup(currency, company)
                val transactions = mutableListOf<RkfTransaction>()
                val balances = mutableListOf<RkfPurse>()
                val tccps = mutableListOf<En1545Parsed>()
                val unfilteredTrips = mutableListOf<RkfTCSTTrip>()
                val records = getRecords(card)
                recordloop@ for (record in records.filterIsInstance<RkfSimpleRecord>())
                    when (record.raw[0].toInt() and 0xff) {
                        0x84 -> transactions += RkfTransaction.parseTransaction(record.raw, lookup, tripVersion) ?: continue@recordloop
                        0x85 -> balances += RkfPurse.parse(record.raw, lookup)
                        0xa2 -> tccps += En1545Parser.parseLeBits(record.raw, TCCP_FIELDS)
                        0xa3 -> unfilteredTrips += RkfTCSTTrip.parse(record.raw, lookup) ?: continue@recordloop
                    }
                val tickets = records.filterIsInstance<RkfTctoRecord>().map { RkfTicket.parse(it, lookup) }
                transactions.sortBy { it.timestamp?.timeInMillis }
                unfilteredTrips.sortBy { it.startTimestamp?.timeInMillis }
                val trips = mutableListOf<RkfTCSTTrip>()
                // Check if unfinished trip is superseeded by finished one
                for ((idx, trip) in unfilteredTrips.withIndex()) {
                    if (idx > 0 && unfilteredTrips[idx - 1].startTimestamp?.timeInMillis == trip.startTimestamp?.timeInMillis
                            && unfilteredTrips[idx - 1].checkoutCompleted && !trip.checkoutCompleted)
                        continue
                    if (idx < unfilteredTrips.size - 1 && unfilteredTrips[idx + 1].startTimestamp?.timeInMillis == trip.startTimestamp?.timeInMillis
                            && unfilteredTrips[idx + 1].checkoutCompleted && !trip.checkoutCompleted)
                        continue
                    trips.add(trip)
                }
                val nonTripTransactions = transactions.filter { it.isOther() }
                val tripTransactions = transactions.filter { !it.isOther() }
                val remainingTransactions = mutableListOf<RkfTransaction>()
                var i = 0
                for (trip in trips)
                    while (i < tripTransactions.size) {
                        val transaction = tripTransactions[i]
                        val transactionTimestamp = clearSeconds(transaction.timestamp?.timeInMillis ?: 0)
                        if (transactionTimestamp > clearSeconds(trip.endTimestamp?.timeInMillis ?: 0))
                            break
                        i++
                        if (transactionTimestamp < clearSeconds(trip.startTimestamp?.timeInMillis ?: 0)) {
                            remainingTransactions.add(transaction)
                            continue
                        }
                        trip.addTransaction(transaction)
                    }
                if (i < tripTransactions.size)
                    remainingTransactions.addAll(tripTransactions.subList(i, tripTransactions.size))
                return RkfTransitData(mTcci = tcci,
                        mTrips = TransactionTripLastPrice.merge(nonTripTransactions + remainingTransactions)
                                + trips.map { it.tripLegs }.flatten(),
                        mBalances = balances, mLookup = lookup, mTccps = tccps, mSerial = getSerial(card),
                        subscriptions = tickets)
            }
        }

        internal fun clearSeconds(timeInMillis: Long) = timeInMillis / 60000 * 60000

        private fun getRecords(card: ClassicCard): List<RkfRecord> {
            val records = mutableListOf<RkfRecord>()
            var sector = 3
            var block = 0

            while (sector < card.sectors.size) {
                // FIXME: we should also check TCDI entry but TCDI doesn't match the spec apparently,
                // so for now just use id byte
                val type = card[sector, block].data.getBitsFromBufferLeBits(0, 8)
                if (type == 0) {
                    sector++
                    block = 0
                    continue
                }
                var first = true
                val oldSector = sector
                var oldBlockCount = -1

                while (sector < card.sectors.size && (first || block != 0)) {
                    first = false
                    val blockData = card[sector, block].data
                    val newType = blockData.getBitsFromBufferLeBits(0, 8)
                    // Some Rejsekort skip slot in the middle of the sector
                    if (newType == 0 && block + oldBlockCount < card[sector].blocks.size - 1) {
                        block += oldBlockCount
                        continue
                    }
                    if (newType != type)
                        break
                    val version = blockData.getBitsFromBufferLeBits(8, 6)
                    if (type in 0x86..0x87) {
                        val chunks = mutableListOf<List<ImmutableByteArray>>()
                        while (true) {
                            if (card[sector, block].data[0] == 0.toByte() && block < card[sector].blocks.size - 2) {
                                block++
                                continue
                            }
                            if (card[sector, block].data[0].toInt() and 0xff !in 0x86..0x88)
                                break
                            var ptr = 0
                            val tags = mutableListOf<ImmutableByteArray>()
                            while (true) {
                                val subType = card[sector, block].data[ptr].toInt() and 0xff
                                var l = getTccoTagSize(subType, version)
                                if (l == -1)
                                    break
                                var tag = ImmutableByteArray.empty()
                                while (l > 0) {
                                    if (ptr == 16) {
                                        ptr = 0
                                        block++
                                        if (block >= card[sector].blocks.size - 1) {
                                            sector++
                                            block = 0
                                        }
                                    }
                                    val c = minOf(16 - ptr, l)
                                    tag += card[sector, block].data.sliceOffLen(ptr, c)
                                    l -= c
                                    ptr += c
                                }
                                tags += tag
                            }
                            chunks += listOf(tags)
                            if (ptr != 0) {
                                block++
                                if (block >= card[sector].blocks.size - 1) {
                                    sector++
                                    block = 0
                                }
                            }
                        }
                        records.add(RkfTctoRecord(chunks))
                    } else {
                        val blockCount = getBlockCount(type, version)
                        if (blockCount == -1) {
                            break
                        }
                        oldBlockCount = blockCount
                        var dat = ImmutableByteArray(0)

                        repeat(blockCount) {
                            dat += card[sector, block].data
                            block++
                            if (block >= card[sector].blocks.size - 1) {
                                sector++
                                block = 0
                            }
                        }

                        records.add(RkfSimpleRecord(dat))
                    }
                }
                if (block != 0 || sector == oldSector) {
                    sector++
                    block = 0
                }
            }
            return records
        }

        private fun getTccoTagSize(type: Int, version: Int) = when (type) {
            0x86 -> 2
            0x87 -> when (version) {
                1, 2 -> 2
                else -> 17 // No idea how it's actually supposed to be parsed but this works
            }
            0x88 -> 3 // tested: version 3
            0x89 -> 11 // tested: version 3
            0x8a -> 1
            0x93 -> 4
            0x94 -> 4
            0x95 -> 2
            0x96 -> when (version) {
                1, 2 -> 15
                else -> 21 // tested: 3
            }
            0x97 -> 18
            0x98 -> 4
            0x99 -> 5
            0x9a -> 7
            0x9c -> 7 // tested: version 3
            0x9d -> 9
            0x9e -> 5
            0x9f -> 2
            else -> -1
        }

        private fun getBlockCount(type: Int, version: Int) = when (type) {
            0x84 -> 1
            0x85 -> when (version) {
                // Only 3 is tested
                1, 2, 3, 4, 5 -> 3
                else -> 6
            }
            0xa2 -> 2
            0xa3 -> when (version) {
                // Only 2 is tested
                1, 2 -> 3
                // Only 5 is tested
                // 3 seems already have size 6
                else -> 6
            }
            else -> -1
        }

        private fun getSerial(card: ClassicCard): RkfSerial {
            val issuer = getIssuer(card[0])

            val hwSerial = card[0, 0].data.byteArrayToLongReversed(0, 4)

            for (record in getRecords(card).filterIsInstance<RkfSimpleRecord>())
                if ((record.raw[0].toInt() and 0xff) == 0xa2) {
                    val low = record.raw.getBitsFromBufferLeBits(34, 20).toLong()
                    val high = record.raw.getBitsFromBufferLeBits(54, 14).toLong()
                    return RkfSerial(mCompany = issuer, mHwSerial = hwSerial, mCustomerNumber = (high shl 20) or low)
                }
            return RkfSerial(mCompany = issuer, mHwSerial = hwSerial, mCustomerNumber = 0)
        }

        private fun getIssuer(sector0: ClassicSector) = sector0[1].data.getBitsFromBufferLeBits(22, 12)

        internal const val COMPANY = "Company"
        internal const val STATUS = "Status"
        internal val ID_FIELD = En1545FixedInteger("Identifier", 8)
        internal val VERSION_FIELD = En1545FixedInteger("Version", 6)
        internal val HEADER = En1545Container(
                ID_FIELD,
                VERSION_FIELD,
                En1545FixedInteger(COMPANY, 12)
        )

        internal val STATUS_FIELD = En1545FixedInteger(STATUS, 8)
        internal val MAC = En1545Container(
                En1545FixedInteger("MACAlgorithmIdentifier", 2),
                En1545FixedInteger("MACKeyIdentifier", 6),
                En1545FixedInteger("MACAuthenticator", 16)
        )

        private const val CURRENCY = "CardCurrencyUnit"
        private const val EVENT_LOG_VERSION = "EventLogVersionNumber"
        private val TCCI_FIELDS = En1545Container(
                En1545FixedInteger("MADindicator", 16),
                En1545FixedInteger("CardVersion", 6),
                En1545FixedInteger(En1545TransitData.ENV_APPLICATION_ISSUER_ID, 12),
                En1545FixedInteger.date(En1545TransitData.ENV_APPLICATION_VALIDITY_END),
                STATUS_FIELD,
                En1545FixedInteger(CURRENCY, 16),
                En1545FixedInteger(EVENT_LOG_VERSION, 6),
                En1545FixedInteger("A", 26),
                MAC
        )
        private val TCCP_FIELDS = En1545Container(
                HEADER,
                STATUS_FIELD,
                En1545Container(
                        // This is actually a single field. Split is only
                        // because of limitations of parser
                        En1545FixedInteger("CustomerNumberLow", 20),
                        En1545FixedInteger("CustomerNumberHigh", 14)
                )
                // Rest unknown
        )
    }
}

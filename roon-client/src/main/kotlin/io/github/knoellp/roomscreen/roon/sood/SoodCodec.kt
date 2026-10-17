package io.github.knoellp.roomscreen.roon.sood

import java.io.ByteArrayOutputStream

/** A decoded SOOD datagram. A property value of `null` is the wire-level null (length 0xFFFF). */
data class SoodMessage(val type: Char, val props: Map<String, String?>)

/**
 * SOOD v2 wire format (see node-roon-api sood.js):
 * "SOOD", version 0x02, type 'Q' or 'R', then properties until the end of the datagram.
 * Property: name_len (1 byte, > 0), name, value_len (2 bytes big endian, 0xFFFF = null), value.
 */
object SoodCodec {
    const val VERSION = 2
    private const val NULL_LENGTH = 0xFFFF

    fun encode(type: Char, props: Map<String, String?>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("SOOD".toByteArray(Charsets.US_ASCII))
        out.write(VERSION)
        out.write(type.code)
        for ((name, value) in props) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            require(nameBytes.size in 1..255) { "bad property name length for '$name'" }
            out.write(nameBytes.size)
            out.write(nameBytes)
            if (value == null) {
                out.write(NULL_LENGTH shr 8)
                out.write(NULL_LENGTH and 0xFF)
            } else {
                val valueBytes = value.toByteArray(Charsets.UTF_8)
                require(valueBytes.size < NULL_LENGTH) { "value too long for '$name'" }
                out.write(valueBytes.size shr 8)
                out.write(valueBytes.size and 0xFF)
                out.write(valueBytes)
            }
        }
        return out.toByteArray()
    }

    /** Returns null for anything that is not a well-formed SOOD v2 packet (including truncated ones). */
    fun decode(data: ByteArray, length: Int = data.size): SoodMessage? {
        if (length < 6) return null
        if (data[0] != 'S'.code.toByte() || data[1] != 'O'.code.toByte() ||
            data[2] != 'O'.code.toByte() || data[3] != 'D'.code.toByte()
        ) return null
        if (data[4].toInt() != VERSION) return null
        val type = (data[5].toInt() and 0xFF).toChar()
        if (type != 'Q' && type != 'R') return null

        val props = LinkedHashMap<String, String?>()
        var pos = 6
        while (pos < length) {
            val nameLen = data[pos++].toInt() and 0xFF
            if (nameLen == 0 || pos + nameLen > length) return null
            val name = String(data, pos, nameLen, Charsets.UTF_8)
            pos += nameLen
            if (pos + 2 > length) return null
            val valueLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            pos += 2
            if (valueLen == NULL_LENGTH) {
                props[name] = null
            } else {
                if (pos + valueLen > length) return null
                props[name] = String(data, pos, valueLen, Charsets.UTF_8)
                pos += valueLen
            }
        }
        return SoodMessage(type, props)
    }
}

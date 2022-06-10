package app.nir.nfcbridge

import java.nio.ByteBuffer

fun ByteBuffer.toArray(): ByteArray {
    val buf = this.asReadOnlyBuffer()
    buf.flip()
    val bytesArray = ByteArray(buf.remaining())
    buf.get(bytesArray, 0, bytesArray.size)
    return bytesArray
}

fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte)
}


// From https://stackoverflow.com/a/66614516
fun String.fromHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }

    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
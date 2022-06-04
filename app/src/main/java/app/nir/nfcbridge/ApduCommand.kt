package app.nir.nfcbridge

import java.nio.ByteBuffer

fun packApduCommand(cla: Int, ins: Int, p1: Int, p2: Int, rest: ByteArray): ByteArray {
    val bb = ByteBuffer.allocate(6)
    bb.put(cla.toByte())
    bb.put(ins.toByte())
    bb.put(p1.toByte())
    bb.put(p2.toByte())
    bb.put(rest)
    return bb.toArray()
}
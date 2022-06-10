package app.nir.nfcbridge

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

val HCEQueue = ConcurrentLinkedQueue<ByteArray>()

class HostCardEmulatorService : HostApduService() {
    private val TAG = "Bridge/HCE"
    private val STATUS_FAILED = "6F00".fromHex()
    private val HCE_TIMEOUT_MILLIS = 2000

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "Deactivated: $reason")
    }

    override fun processCommandApdu(
        commandApdu: ByteArray?,
        extras: Bundle?
    ): ByteArray {
        if (commandApdu == null) {
            Log.d(TAG, "commandApdu is null")
            return STATUS_FAILED
        }
        val commandApduHex = commandApdu.toHex()
        Log.d(TAG, "Sending $commandApduHex")
        Websocket.sendMessage(commandApduHex)
        Log.d(TAG, "Waiting for a response")

        val start = System.currentTimeMillis()
        val end = start + HCE_TIMEOUT_MILLIS
        while (true) {
            val resp = HCEQueue.poll()

            if (resp != null) {
                return resp
            }

            if (System.currentTimeMillis() > end) {
                Log.d("Bridge/HCE", "No response from websocket, aborting")
                return STATUS_FAILED
            }
        }
    }
}
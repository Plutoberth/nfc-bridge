package app.nir.nfcbridge

import WebSocketManager
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import java.util.concurrent.ConcurrentLinkedQueue

val HCEQueue =  ConcurrentLinkedQueue<ByteArray>()

class HostCardEmulatorService: HostApduService() {
    companion object {
        val TAG = "Host Card Emulator"
        val STATUS_SUCCESS = "9000".fromHex();
        val STATUS_FAILED = "6F00".fromHex();
        val CLA_NOT_SUPPORTED = "6E00".fromHex();
        val INS_NOT_SUPPORTED = "6D00".fromHex();
        val AID = "A0000002471001".fromHex();
        val SELECT_INS = "A4".fromHex();
        val DEFAULT_CLA = "00".fromHex();
        val MIN_APDU_LENGTH = 6
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "Deactivated: $reason")
    }

    override fun processCommandApdu(commandApdu: ByteArray?,
                                    extras: Bundle?): ByteArray {
        if (commandApdu == null) {
            Log.d("Bridge/HCE", "wtf, commandApdu is null")
            return STATUS_FAILED
        }
        val commandApduHex = commandApdu.toHex()
        Log.d("Bridge/HCE", "Sending $commandApduHex")
        WebSocketManager.sendMessage(commandApduHex)
        Log.d("Bridge/HCE", "Waiting for a response")

        val start = System.currentTimeMillis()
        val end = start + 2500
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
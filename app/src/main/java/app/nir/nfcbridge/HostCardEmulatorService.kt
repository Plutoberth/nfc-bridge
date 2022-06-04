package app.nir.nfcbridge

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log



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

        return STATUS_FAILED
    }
}
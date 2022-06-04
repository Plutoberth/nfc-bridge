package app.nir.nfcbridge

import MessageListener
import android.annotation.SuppressLint
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback, MessageListener {
    private var nfcAdapter: NfcAdapter? = null
    private var nfcTextView: TextView? = null
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private var hostSwitch: Switch? = null
    private var card: IsoDep? = null

    private val serverUrl = "wss://6946-85-65-157-57.eu.ngrok.io"

    companion object APDU_COMMANDS {
        val SELECT = packApduCommand(0x94, 0xa4, 0, 0  , "02".fromHex())
        val BALANCE_FILE = "202B00".fromHex()
        val READ_RECORD = packApduCommand(0x94, 0xb2, 0x01, 0x04, "1D".fromHex())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        nfcTextView = findViewById(R.id.nfcLog)
        hostSwitch = findViewById(R.id.hostSwitch)
        WebSocketManager.init(serverUrl, this)
    }

    override fun onResume() {
        super.onResume()
        // if we're not on host mode
        if (!this.hostSwitch!!.isChecked)
        {
            nfcAdapter!!.enableReaderMode(this, this,
                NfcAdapter.FLAG_READER_NFC_A or
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or
                        NfcAdapter.FLAG_READER_NFC_V or
                        NfcAdapter.FLAG_READER_NFC_BARCODE or
                        NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter!!.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag?) {
        runOnUiThread {
            nfcTextView?.append("Card Connected!")
        }

        Log.d("SELECT Command", (SELECT + BALANCE_FILE).toHex())
        Log.d("READ Command", READ_RECORD.toHex())

        val isoDep = IsoDep.get(tag)
        isoDep.connect()
        card = isoDep

        val response = isoDep.transceive(SELECT + BALANCE_FILE)

        Log.d("Card SELECT Response", response.toHex())

        val readResp = isoDep.transceive(READ_RECORD)
        Log.d("Card READ Response", readResp.toHex())

        runOnUiThread { nfcTextView?.append("\nRead: "
                + readResp.toHex()) }



        isoDep.close()
    }

    override fun onConnectSuccess() {
        Log.d("Websockets", "Connected")
    }

    override fun onConnectFailed() {
        Log.d("Websockets", "Connection Failed")
    }

    override fun onClose() {
        Log.d("Websockets", "Connection Closed")
    }

    override fun onMessage(text: String?) {
        if (text == null) {
            Log.d("Websockets", "null text?")
            return
        }

        Log.d("Websockets", "received $text")
        val messageraw = text.fromHex();

        if (this.card == null) {
            Log.d("Websockets", "No card available")
            return
        }

        val resp = this.card!!.transceive(messageraw)
        val respHex = resp.toHex()
        Log.d("Websockets", "Response from card: $respHex")
        WebSocketManager.sendMessage(respHex)
    }
}
package app.nir.nfcbridge

import MessageListener
import android.annotation.SuppressLint
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback, MessageListener {
    private var nfcAdapter: NfcAdapter? = null
    private var nfcTextView: TextView? = null
    private var ipAddrTextBox: EditText? = null
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private var hceSwitch: Switch? = null
    private var connectButton: Button? = null
    private var cardDetectedCheckBox: CheckBox? = null
    private var card: IsoDep? = null

    private val DEFAULT_IP_ADDR = "85.65.157.57";

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
        nfcTextView!!.movementMethod = ScrollingMovementMethod()
        ipAddrTextBox = findViewById(R.id.serverIP)

        connectButton = findViewById(R.id.connectWebsocketButton)
        connectButton!!.setOnClickListener {
            connectWebsocket()
        }

        hceSwitch = findViewById(R.id.hceSwitch)
        hceSwitch!!.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                disableNfcReader()
            } else {
                enableNfcReader()
            }
        }

        cardDetectedCheckBox = findViewById(R.id.cardDetectedCheckbox)
        ipAddrTextBox!!.setText(DEFAULT_IP_ADDR)
        this.connectWebsocket()
    }

    private fun connectWebsocket() {
        val server =  "ws://${ipAddrTextBox!!.text}:8765"
        WebSocketManager.init(server, this)
        WebSocketManager.close()
        WebSocketManager.connect()
    }

    private fun isHCE(): Boolean {
        return this.hceSwitch!!.isChecked
    }

    private fun enableNfcReader() {
        nfcAdapter!!.enableReaderMode(this, this,
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null)
        cardDetectedCheckBox!!.isEnabled = true
    }

    private fun disableNfcReader() {
        nfcAdapter!!.disableReaderMode(this)
        card = null
        cardDetectedCheckBox!!.isChecked = false
        cardDetectedCheckBox!!.isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        // if we're not on host mode
        if (!isHCE())
        {
            enableNfcReader()
        }
    }

    override fun onPause() {
        super.onPause()
        disableNfcReader()
    }

    override fun onTagDiscovered(tag: Tag?) {
        this.log("NFC" , "Tag Discovered")

        val isoDep = IsoDep.get(tag)
        isoDep.connect()
        card = isoDep
        cardDetectedCheckBox!!.isChecked = true
    }

    override fun onConnectSuccess() {
        this.log("Websockets", "Connected")
    }

    override fun onConnectFailed() {
        this.log("Websockets", "Connection Failed")
    }

    override fun onClose() {
        this.log("Websockets", "Connection Closed")
    }

    override fun onMessage(text: String?) {
        if (text == null) {
            Log.d("Websockets", "null text?")
            return
        }

        this.log("Websockets", "received $text")
        val messageraw = text.fromHex();

        if (isHCE()) {
            HCEQueue.add(messageraw)
        } else {
            if (this.card == null) {
                this.log("Websockets", "No card available")
                return
            }

            val resp: ByteArray;
            try {
                resp = this.card!!.transceive(messageraw)
            } catch (e: TagLostException) {
                // Failure
                WebSocketManager.sendMessage("6F00")
                this.log("NFC", "Connection Lost")
                card = null;
                cardDetectedCheckBox!!.isChecked = true
                return
            }
            val respHex = resp.toHex()
            this.log("NFC", "Response from card: $respHex")
            WebSocketManager.sendMessage(respHex)
        }
    }

    fun log(tag: String, text: String) {
        Log.d("Bridge/$tag", text)
        runOnUiThread {
            nfcTextView?.append("$tag: $text\n")
        }
    }
}
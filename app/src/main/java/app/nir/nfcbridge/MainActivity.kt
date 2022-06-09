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
    private var cardDetectedCheckbox: CheckBox? = null
    private var peerConnectedCheckbox: CheckBox? = null
    private var serverConnectedCheckbox: CheckBox? = null
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

        cardDetectedCheckbox = findViewById(R.id.cardDetectedCheckbox)
        peerConnectedCheckbox = findViewById(R.id.peerConnectedCheckbox)
        serverConnectedCheckbox = findViewById(R.id.serverConnectedCheckbox)
        ipAddrTextBox!!.setText(DEFAULT_IP_ADDR)

        connectWebsocket()
    }

    private fun checkServerConnected() {
        runOnUiThread {
            serverConnectedCheckbox!!.isChecked = true
            peerConnectedCheckbox!!.isEnabled = true
        }
    }

    private fun uncheckServerConnected() {
        runOnUiThread {
            serverConnectedCheckbox!!.isChecked = false
            peerConnectedCheckbox!!.isEnabled = false
            uncheckPeerConnected()
        }
    }

    private fun checkPeerConnected() {
        runOnUiThread {
            peerConnectedCheckbox!!.isChecked = true
        }
    }

    private fun uncheckPeerConnected() {
        runOnUiThread {
            peerConnectedCheckbox!!.isChecked = false
        }
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
        cardDetectedCheckbox!!.isEnabled = true
    }

    private fun disableNfcReader() {
        nfcAdapter!!.disableReaderMode(this)
        card = null
        cardDetectedCheckbox!!.isChecked = false
        cardDetectedCheckbox!!.isEnabled = false
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
        this.log("NFC", "Tag Discovered")

        val isoDep = IsoDep.get(tag)
        isoDep.connect()
        card = isoDep
        cardDetectedCheckbox!!.isChecked = true

        val message = WebsocketCommand(CommandType.CARD_DETECTED, "")
        WebSocketManager.sendMessage(message.encode())
    }

    override fun onConnectSuccess() {
        this.log("Websockets", "Connected")
        //WebSocketManager.sendMessage(WebsocketCommand(CommandType.PING, "").encode())
        checkServerConnected()
    }

    override fun onConnectFailed() {
        this.log("Websockets", "Connection Failed")
        uncheckServerConnected()
    }

    override fun onClose() {
        this.log("Websockets", "Connection Closed")
        uncheckServerConnected()
    }

    override fun onMessage(text: String?) {
        if (text == null) {
            Log.d("Websockets", "null text?")
            return
        }

        val cmd = decodeWebsocketCommand(text)

        this.log("Websockets", "received command ${cmd.type}, data: ${cmd.data}")
        val messageraw = text.fromHex();

        when (cmd.type) {
            CommandType.PING -> {
            }
            CommandType.CARD_DETECTED -> {
                if (isHCE()) {
                    cardDetectedCheckbox!!.isChecked = true
                } else {
                    this.log("Protocol", "both peers are cardholders")
                }
            }
            CommandType.CARD_LOST -> {
                if (isHCE()) {
                    cardDetectedCheckbox!!.isChecked = false
                } else {
                    this.log("Protocol", "both peers are cardholders")
                }
            }
            CommandType.HCE_REQUEST -> {
                if (isHCE()) {
                    this.log("Protocol", "both peers are card emulators")
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
                        val resp = WebsocketCommand(CommandType.CARD_LOST, "")
                        WebSocketManager.sendMessage(resp.encode())
                        this.log("NFC", "Connection Lost")
                        card = null;
                        cardDetectedCheckbox!!.isChecked = true
                        return
                    }
                    val respHex = resp.toHex()
                    this.log("NFC", "Response from card: $respHex")

                    val respObj = WebsocketCommand(CommandType.CARD_RESPONSE, respHex)
                    WebSocketManager.sendMessage(respObj.encode())
                }
            }
            CommandType.CARD_RESPONSE -> {
                if (isHCE()) {
                    HCEQueue.add(messageraw)
                } else {
                    this.log("Protocol", "both peers are cardholders")
                }
            }
        }
    }

    fun log(tag: String, text: String) {
        Log.d("Bridge/$tag", text)
        runOnUiThread {
            nfcTextView?.append("$tag: $text\n")
        }
    }
}
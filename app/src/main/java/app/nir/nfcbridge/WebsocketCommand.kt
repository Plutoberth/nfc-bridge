package app.nir.nfcbridge

enum class CommandType {
    PING,
    CARD_DETECTED,
    CARD_LOST,
    HCE_REQUEST,
    CARD_RESPONSE
}

enum class PING_DEV_TYPE {
    CARDHOLDER,
    EMULATOR
}

class WebsocketCommand(val type: CommandType, val data: String) {
    fun encode(): String {
        val cmd = type.ordinal;
        return "$cmd:$data"
    }
}

fun decodeWebsocketCommand(encoded: String): WebsocketCommand {
    val (cmd_str, buf) = encoded.split(":")
    val cmd = CommandType.values()[cmd_str.toInt()]
    return WebsocketCommand(cmd, buf)
}

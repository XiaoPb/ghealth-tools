package com.ghealth.tools.ble.protocol

interface ProtocolParser {
    fun encode(key: String, param: ByteArray = ByteArray(0), secure: Boolean = false): ByteArray
    fun decode(raw: ByteArray): List<Result<ParseResult>>
    fun decodeGFrame(param: ByteArray): List<GhFuncFrame>
    fun resetDecoder()
}

class KotlinProtocolParser : ProtocolParser {
    private val frameParser = FrameParser()
    private val frameBuilder = FrameBuilder()
    private val gFrameDecoder = GFrameDecoder()

    override fun encode(key: String, param: ByteArray, secure: Boolean): ByteArray {
        return frameBuilder.build(key = key, param = param, secure = secure)
    }

    override fun decode(raw: ByteArray): List<Result<ParseResult>> {
        return frameParser.process(raw)
    }

    override fun decodeGFrame(param: ByteArray): List<GhFuncFrame> {
        return gFrameDecoder.decode(param)
    }

    override fun resetDecoder() {
        frameParser.reset()
        gFrameDecoder.reset()
    }
}

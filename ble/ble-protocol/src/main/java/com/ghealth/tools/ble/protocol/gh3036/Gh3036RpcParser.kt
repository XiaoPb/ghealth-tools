package com.ghealth.tools.ble.protocol.gh3036

import com.ghealth.tools.ble.protocol.rpccore.FrameBuilder
import com.ghealth.tools.ble.protocol.rpccore.FrameParser
import com.ghealth.tools.ble.protocol.rpccore.ParseResult
import com.ghealth.tools.ble.protocol.rpccore.RpcParser

class Gh3036RpcParser : RpcParser {
    private val frameParser = FrameParser()
    private val frameBuilder = FrameBuilder()

    override fun encode(key: String, param: ByteArray, secure: Boolean): ByteArray {
        return frameBuilder.build(key = key, param = param, secure = secure)
    }

    override fun decode(raw: ByteArray): List<Result<ParseResult>> {
        return frameParser.process(raw)
    }

    override fun reset() {
        frameParser.reset()
    }
}

package com.ghealth.tools.ble.protocol.rpccore

class FrameParser {
    private var state = ParseState.FrameHeader
    private var frameLen = 0
    private var typeKey = TypeKey(0)
    private val keyData = mutableListOf<Byte>()
    private var keyExpectedLen = 0
    private var frameIndex = FrameIndex()
    private val paramData = mutableListOf<Byte>()
    private var crc: Byte = 0
    private var headerPos = 0
    private var indexFirst = true

    fun reset() {
        state = ParseState.FrameHeader
        frameLen = 0
        typeKey = TypeKey(0)
        keyData.clear()
        keyExpectedLen = 0
        frameIndex = FrameIndex()
        paramData.clear()
        crc = 0
        headerPos = 0
        indexFirst = true
    }

    fun process(data: ByteArray): List<Result<ParseResult>> {
        val results = mutableListOf<Result<ParseResult>>()
        for (byte in data) {
            val r = processByte(byte)
            when {
                r.isSuccess && r.getOrNull() != null -> results.add(Result.success(r.getOrNull()!!))
                r.isFailure -> {
                    reset()
                    results.add(Result.failure(r.exceptionOrNull()!!))
                }
            }
        }
        return results
    }

    private fun processByte(byte: Byte): Result<ParseResult?> {
        return when (state) {
            ParseState.FrameHeader -> { processFrameHeader(byte); Result.success(null) }
            ParseState.CheckLength -> { frameLen = byte.toInt() and 0xFF; state = ParseState.CheckTypeKey; Result.success(null) }
            ParseState.CheckTypeKey -> { typeKey = TypeKey(byte); crc = byte; frameLen--; keyExpectedLen = if (typeKey.isArray) 0 else 1; state = ParseState.CheckKey; Result.success(null) }
            ParseState.CheckKey -> processKey(byte)
            ParseState.CheckIndex -> processIndex(byte)
            ParseState.CheckParam -> processParam(byte)
            ParseState.CheckCrc -> processCrc(byte)
        }
    }

    private fun processFrameHeader(byte: Byte) {
        val expected = if (headerPos == 0) FRAME_HEADER_0 else FRAME_HEADER_1
        if (byte == expected) {
            headerPos++
            if (headerPos >= 2) { state = ParseState.CheckLength; headerPos = 0 }
        } else {
            headerPos = if (byte == FRAME_HEADER_0) 1 else 0
        }
    }

    private fun processKey(byte: Byte): Result<ParseResult?> {
        crc = (crc + byte).toByte(); frameLen--
        if (typeKey.isArray) {
            if (keyData.isEmpty()) {
                if ((byte.toInt() and 0xFF) > MAX_KEY_SIZE - 1) return Result.failure(ProtocolError.KeyOverMaxSize)
                keyExpectedLen = byte.toInt() and 0xFF; keyData.add(byte)
            } else {
                keyData.add(byte)
                if (keyData.size > keyExpectedLen) transitionAfterKey()
            }
        } else { keyData.add(byte); transitionAfterKey() }
        return Result.success(null)
    }

    private fun transitionAfterKey() {
        val check = ((if (typeKey.isSecure) 1 else 0) shl 1) or (if (typeKey.isFin) 1 else 0)
        if (check == 1) {
            frameIndex.frameIdx = LAST_FRAME_INDEX
            state = if (frameLen == 0) ParseState.CheckCrc else ParseState.CheckParam
        } else { state = ParseState.CheckIndex; indexFirst = true }
    }

    private fun processIndex(byte: Byte): Result<ParseResult?> {
        val check = ((if (typeKey.isSecure) 1 else 0) shl 1) or (if (typeKey.isFin) 1 else 0)
        when (check) {
            0 -> { frameIndex.frameIdx = byte; crc = (crc + byte).toByte(); frameLen--; state = if (frameLen == 0) ParseState.CheckCrc else ParseState.CheckParam }
            2 -> {
                if (indexFirst) { frameIndex.invokeIdx = byte; crc = (crc + byte).toByte(); frameLen--; indexFirst = false; return Result.success(null) }
                else { frameIndex.frameIdx = byte; crc = (crc + byte).toByte(); frameLen--; state = if (frameLen == 0) ParseState.CheckCrc else ParseState.CheckParam }
            }
            3 -> { frameIndex.invokeIdx = byte; frameIndex.frameIdx = LAST_FRAME_INDEX; crc = (crc + byte).toByte(); frameLen--; state = if (frameLen == 0) ParseState.CheckCrc else ParseState.CheckParam }
            else -> return Result.failure(ProtocolError.FormatError)
        }
        return Result.success(null)
    }

    private fun processParam(byte: Byte): Result<ParseResult?> {
        if (frameLen == 0) return processCrc(byte)
        paramData.add(byte); crc = (crc + byte).toByte(); frameLen--
        if (frameLen == 0) state = ParseState.CheckCrc
        return Result.success(null)
    }

    private fun processCrc(byte: Byte): Result<ParseResult?> {
        if (byte != crc) return Result.failure(ProtocolError.CrcMismatch)
        val key = if (typeKey.isArray && keyData.size > 1) String(keyData.drop(1).toByteArray(), Charsets.UTF_8)
        else if (keyData.isNotEmpty()) String(keyData.toByteArray(), Charsets.UTF_8) else ""
        val result = ParseResult(key = key, param = paramData.toByteArray(), isSecure = typeKey.isSecure, isFin = typeKey.isFin, invokeIdx = frameIndex.invokeIdx, frameIdx = frameIndex.frameIdx)
        reset()
        return Result.success(result)
    }
}

@file:Suppress("unused")
package com.ghealth.tools.ble.protocol

typealias ParseResult = com.ghealth.tools.ble.protocol.rpccore.ParseResult
typealias TypeKey = com.ghealth.tools.ble.protocol.rpccore.TypeKey
typealias FrameIndex = com.ghealth.tools.ble.protocol.rpccore.FrameIndex
typealias ParseState = com.ghealth.tools.ble.protocol.rpccore.ParseState

const val FRAME_HEADER_0: Byte = com.ghealth.tools.ble.protocol.rpccore.FRAME_HEADER_0
const val FRAME_HEADER_1: Byte = com.ghealth.tools.ble.protocol.rpccore.FRAME_HEADER_1
const val MAX_FRAME_SIZE = com.ghealth.tools.ble.protocol.rpccore.MAX_FRAME_SIZE
const val MAX_KEY_SIZE = com.ghealth.tools.ble.protocol.rpccore.MAX_KEY_SIZE
const val LAST_FRAME_INDEX: Byte = com.ghealth.tools.ble.protocol.rpccore.LAST_FRAME_INDEX

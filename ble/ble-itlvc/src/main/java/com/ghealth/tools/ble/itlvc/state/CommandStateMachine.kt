package com.ghealth.tools.ble.itlvc.state

enum class CommandState { CREATED, PENDING_SEND, AWAITING_RESPONSE, TIMED_OUT, COMPLETED, FAILED }

/** 单个命令的生命周期状态机。 */
class CommandStateMachine {
    var state: CommandState = CommandState.CREATED
        private set
    var attempts: Int = 0
        private set

    fun onEnqueued() { state = CommandState.PENDING_SEND }
    fun onSent() { state = CommandState.AWAITING_RESPONSE; attempts++ }
    fun onTimeout() { state = CommandState.TIMED_OUT }
    fun onFailure() { state = CommandState.FAILED }
    fun onResponse() { state = CommandState.COMPLETED }
}

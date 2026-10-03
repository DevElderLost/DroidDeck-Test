package com.droiddeck.launcher.session

/** Used on the service's audio worker. Restore each output's original state, including pre-muted sinks. */
internal class PipAudioMute(private val outputs: List<Output>) {
    interface Output {
        fun muted(): Boolean?
        fun mute(value: Boolean): Boolean
    }
    private var original: List<Boolean>? = null

    fun setMuted(value: Boolean): Boolean {
        if (outputs.isEmpty()) return false
        val before = outputs.map { it.muted() ?: return false }
        if (original == null) original = before
        var success = true
        outputs.forEach { if (!it.mute(value)) success = false }
        if (!success) outputs.zip(before).forEach { (output, muted) -> output.mute(muted) }
        return success
    }

    fun restore(): Boolean {
        val saved = original ?: return true
        var success = true
        outputs.zip(saved).forEach { (output, muted) -> if (!output.mute(muted)) success = false }
        if (success) original = null
        return success
    }
}

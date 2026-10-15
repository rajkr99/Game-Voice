package com.gamervoice.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamervoice.app.VoiceApp
import com.gamervoice.app.data.AppSettings
import com.gamervoice.app.data.JoinError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CallViewModel(app: Application) : AndroidViewModel(app) {
    private val va = app as VoiceApp
    val call = va.callManager.state
    val settings: StateFlow<AppSettings> =
        va.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val joining = MutableStateFlow(false)
    val joinError = MutableStateFlow<String?>(null)

    private val nameRegex = Regex("^[\\p{L}\\p{N} _.-]{1,32}$")
    private val roomRegex = Regex("^[A-Za-z0-9_-]{3,32}$")

    fun join(name: String, room: String) {
        val n = name.trim(); val r = room.trim().uppercase()
        joinError.value = when {
            !nameRegex.matches(n) -> "Enter a name (1-32 letters/numbers)."
            !roomRegex.matches(r) -> "Room code must be 3-32 letters or numbers."
            else -> null
        }
        if (joinError.value != null) return
        joining.value = true
        viewModelScope.launch {
            try { va.callManager.join(r, n) }
            catch (e: JoinError) { joinError.value = e.message }
            catch (e: Exception) { joinError.value = "Couldn't connect. Try again." }
            finally { joining.value = false }
        }
    }

    fun leave() = va.callManager.leave()
    fun toggleMic() = va.callManager.toggleMic()
    fun setMic(on: Boolean) = va.callManager.setMic(on)
    fun setPushToTalk(v: Boolean) = va.callManager.setPushToTalk(v)
    fun selectOutput(l: String) = va.callManager.selectOutput(l)
    fun refreshOutputs() = va.callManager.refreshOutputs()
    fun setVolume(id: String, v: Float) = va.callManager.setVolume(id, v)

    fun setNoiseLevel(v: Int) = viewModelScope.launch { va.settings.setNoiseLevel(v) }
    fun setClearVoice(v: Boolean) = viewModelScope.launch { va.settings.setClearVoice(v) }
    fun setDataSaver(v: Boolean) = viewModelScope.launch { va.settings.setDataSaver(v) }
    fun setServerUrl(v: String) = viewModelScope.launch { va.settings.setServerUrl(v) }
}

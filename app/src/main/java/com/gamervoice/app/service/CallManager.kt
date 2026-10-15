package com.gamervoice.app.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.gamervoice.app.data.JoinError
import com.gamervoice.app.data.NoiseLevel
import com.gamervoice.app.data.SettingsRepository
import com.gamervoice.app.data.TokenApi
import io.livekit.android.AudioOptions
import io.livekit.android.AudioType
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.audio.AudioSwitchHandler
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.AudioTrackPublishDefaults
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.RemoteAudioTrack
import com.twilio.audioswitch.AudioDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnState { Idle, Connecting, Connected, Reconnecting, Disconnected }

data class Person(
    val id: String,
    val name: String,
    val isLocal: Boolean,
    val speaking: Boolean,
    val muted: Boolean,
    val volume: Float = 1f,
)

data class CallState(
    val conn: ConnState = ConnState.Idle,
    val room: String = "",
    val people: List<Person> = emptyList(),
    val micOn: Boolean = false,
    val pushToTalk: Boolean = false,
    val outputs: List<String> = emptyList(),
    val selectedOutput: String? = null,
    val error: String? = null,
)

/**
 * Owns the single LiveKit Room for the whole process. The UI and the foreground
 * service both observe [state]; the service keeps the process alive in the background.
 */
class CallManager(private val app: Context, private val settings: SettingsRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenApi = TokenApi()
    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state.asStateFlow()

    private var room: Room? = null
    private var eventsJob: Job? = null
    private val volumes = mutableMapOf<String, Float>()
    private var mutedByPhoneCall = false
    private var phoneListener: Any? = null

    /** Fetch token, connect audio-only, publish mic. Throws [JoinError] for the UI. */
    suspend fun join(roomCode: String, name: String) {
        if (room != null) leave()
        val s = settings.current()
        _state.value = CallState(conn = ConnState.Connecting, room = roomCode, pushToTalk = s.pushToTalk)
        val token = try {
            tokenApi.fetchToken(s.serverUrl, roomCode, name)
        } catch (e: JoinError) {
            _state.value = CallState(error = e.message); throw e
        }

        val bitrate = when {
            s.dataSaver -> 12_000
            s.clearVoice -> 32_000
            else -> 20_000
        }
        val nsOn = s.noiseLevel != NoiseLevel.OFF
        val r = LiveKit.create(
            appContext = app,
            options = RoomOptions(
                adaptiveStream = false,
                dynacast = false, // video-only optimisation; off
                audioTrackCaptureDefaults = LocalAudioTrackOptions(
                    noiseSuppression = nsOn,
                    // Echo cancellation matters most in media mode: the mic can hear the game on speaker.
                    echoCancellation = nsOn,
                    autoGainControl = nsOn,
                    highPassFilter = nsOn,
                    typingNoiseDetection = s.noiseLevel == NoiseLevel.STRONG,
                ),
                audioTrackPublishDefaults = AudioTrackPublishDefaults(
                    audioBitrate = bitrate,
                    dtx = true, // silence = almost no data
                    red = !s.dataSaver, // redundancy improves packet-loss resilience
                ),
            ),
            overrides = LiveKitOverrides(
                // MEDIA mode: voice plays on the same stream/volume as the game, so the game is not
                // pushed into call mode (no crushed bitrate, no ducking). The mic still uses the
                // voice-communication source so echo/noise processing keeps working.
                audioOptions = AudioOptions(audioOutputType = AudioType.MediaAudioType()),
            ),
        )
        room = r
        // Don't grab audio focus: the game keeps playing at full volume instead of being paused/ducked.
        // (Real phone calls are still handled by listenForPhoneCalls below.)
        (r.audioHandler as? AudioSwitchHandler)?.manageAudioFocus = false
        eventsJob = scope.launch { r.events.collect { onEvent(it) } }

        try {
            r.connect(token.url, token.token)
            // Push-to-talk starts muted; normal mode starts live.
            r.localParticipant.setMicrophoneEnabled(!s.pushToTalk)
            _state.update { it.copy(conn = ConnState.Connected, micOn = !s.pushToTalk) }
            settings.saveLastJoin(name, roomCode)
            refreshPeople(); refreshOutputs()
            startService()
            listenForPhoneCalls()
        } catch (e: Exception) {
            cleanup()
            val err = JoinError.ServerDown
            _state.value = CallState(error = err.message)
            throw err
        }
    }

    private fun onEvent(e: RoomEvent) {
        when (e) {
            is RoomEvent.Reconnecting -> _state.update { it.copy(conn = ConnState.Reconnecting) }
            is RoomEvent.Reconnected -> _state.update { it.copy(conn = ConnState.Connected) }
            is RoomEvent.Disconnected -> {
                val wasActive = _state.value.conn != ConnState.Idle
                cleanup()
                _state.update { it.copy(conn = ConnState.Disconnected) }
                if (wasActive && e.reason != null) CallService.notifyDropped(app, _state.value.room)
            }
            is RoomEvent.ParticipantConnected,
            is RoomEvent.ParticipantDisconnected,
            is RoomEvent.ActiveSpeakersChanged,
            is RoomEvent.TrackMuted,
            is RoomEvent.TrackUnmuted,
            is RoomEvent.TrackSubscribed -> {
                if (e is RoomEvent.TrackSubscribed) applyVolume(e.participant.identity?.value)
                refreshPeople()
            }
            else -> Unit
        }
    }

    private fun refreshPeople() {
        val r = room ?: return
        val speakers = r.activeSpeakers.map { it.sid }.toSet()
        fun Participant.toPerson(local: Boolean) = Person(
            id = identity?.value ?: sid.value,
            name = name ?: identity?.value ?: "?",
            isLocal = local,
            speaking = sid in speakers,
            muted = !isMicrophoneEnabled(),
            volume = volumes[identity?.value] ?: 1f,
        )
        val list = listOf(r.localParticipant.toPerson(true)) +
            r.remoteParticipants.values.map { it.toPerson(false) }
        _state.update { it.copy(people = list) }
        CallService.update(app)
    }

    // ---------- mic ----------
    fun setMic(on: Boolean) {
        val r = room ?: return
        scope.launch {
            r.localParticipant.setMicrophoneEnabled(on)
            _state.update { it.copy(micOn = on) }
            refreshPeople()
        }
    }
    fun toggleMic() = setMic(!_state.value.micOn)

    fun setPushToTalk(enabled: Boolean) {
        _state.update { it.copy(pushToTalk = enabled) }
        scope.launch { settings.setPushToTalk(enabled) }
        if (enabled) setMic(false)
    }

    // ---------- audio routing ----------
    private val audioSwitch get() = room?.audioHandler as? AudioSwitchHandler

    fun refreshOutputs() {
        val h = audioSwitch ?: return
        _state.update {
            it.copy(
                outputs = h.availableAudioDevices.map { d -> d.label() },
                selectedOutput = h.selectedAudioDevice?.label(),
            )
        }
    }

    fun selectOutput(label: String) {
        val h = audioSwitch ?: return
        h.availableAudioDevices.firstOrNull { it.label() == label }?.let { h.selectDevice(it) }
        refreshOutputs()
    }

    private fun AudioDevice.label() = when (this) {
        is AudioDevice.Speakerphone -> "Speaker"
        is AudioDevice.Earpiece -> "Earpiece"
        is AudioDevice.BluetoothHeadset -> "Bluetooth"
        is AudioDevice.WiredHeadset -> "Wired"
        else -> name
    }

    // ---------- per-person volume ----------
    fun setVolume(identity: String, v: Float) {
        volumes[identity] = v
        applyVolume(identity)
        refreshPeople()
    }

    private fun applyVolume(identity: String?) {
        identity ?: return
        val p = room?.remoteParticipants?.values?.firstOrNull { it.identity?.value == identity } ?: return
        val vol = (volumes[identity] ?: 1f).toDouble()
        p.audioTrackPublications.forEach { (_, track) -> (track as? RemoteAudioTrack)?.setVolume(vol) }
    }

    // ---------- phone call handling ----------
    @Suppress("DEPRECATION")
    private fun listenForPhoneCalls() {
        val tm = app.getSystemService(TelephonyManager::class.java) ?: return
        val granted = ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_PHONE_STATE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return // AudioSwitch still loses focus to the call; we just can't auto-restore mic.
        val handle: (Int) -> Unit = { s ->
            if (s != TelephonyManager.CALL_STATE_IDLE && _state.value.micOn) {
                mutedByPhoneCall = true; setMic(false)
            } else if (s == TelephonyManager.CALL_STATE_IDLE && mutedByPhoneCall) {
                mutedByPhoneCall = false; setMic(true)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = handle(state)
            }
            tm.registerTelephonyCallback(ContextCompat.getMainExecutor(app), cb); phoneListener = cb
        } else {
            val l = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, n: String?) = handle(state)
            }
            tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE); phoneListener = l
        }
    }

    @Suppress("DEPRECATION")
    private fun stopPhoneListener() {
        val tm = app.getSystemService(TelephonyManager::class.java) ?: return
        when (val l = phoneListener) {
            is PhoneStateListener -> tm.listen(l, PhoneStateListener.LISTEN_NONE)
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && l is TelephonyCallback) tm.unregisterTelephonyCallback(l)
        }
        phoneListener = null
    }

    // ---------- lifecycle ----------
    private fun startService() {
        ContextCompat.startForegroundService(app, Intent(app, CallService::class.java))
    }

    fun leave() {
        val r = room
        _state.value = CallState(conn = ConnState.Idle)
        cleanup()
        r?.disconnect()
    }

    private fun cleanup() {
        eventsJob?.cancel(); eventsJob = null
        room?.release(); room = null
        volumes.clear()
        stopPhoneListener()
        app.stopService(Intent(app, CallService::class.java))
        (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).mode = AudioManager.MODE_NORMAL
    }

    val isInCall get() = room != null
}

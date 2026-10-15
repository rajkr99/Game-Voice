package com.gamervoice.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gamervoice.app.ui.AppRoot
import com.gamervoice.app.ui.theme.GamerVoiceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rejoin = intent?.getStringExtra("rejoin")
        setContent { GamerVoiceTheme { AppRoot(rejoinRoom = rejoin) } }
    }

    /** Wired/Bluetooth headset button toggles mute while the app is in front. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val m = (application as VoiceApp).callManager
        if (m.isInCall && (keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            m.toggleMic(); return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

# GamerVoice
 
Lightweight, audio-only voice chat for gamers. Rooms of up to 10 people with very low CPU, battery and data usage, so your game doesn't lag.
 
Built with **Kotlin + Jetpack Compose** on Android, **LiveKit** for real-time audio, and a small **Node.js token server** that hands out short-lived join passes.
 
---
 
## Features
 
- Audio-only rooms (up to 10 people), join with a name and a room code
- Random room code generator, copy and share buttons
- Voice plays on the **media stream**, so game volume and quality don't drop because of a call
- Does not take audio focus, so your game is not paused or ducked when voice starts
- Noise cancellation modes: **Off / Standard / Strong**
  - Standard: echo cancellation, noise suppression, auto gain control, high-pass filter
  - Strong: Standard plus keyboard and finger-tap noise detection
- **Clear voice** toggle (higher bitrate, 32 kbps instead of 20 kbps)
- **Data saver** mode (lowest bitrate, takes priority over clear voice)
- Per-user volume sliders in the room screen
- Foreground service keeps the call alive in the background
- Mic auto-mutes during a real phone call and comes back after it ends
- In-app "Keep call alive" help for phone brands that kill background apps
- Dark, flat UI with no animations or heavy images
## Why it's light
 
- Audio only: no camera, no video code paths
- Mono Opus at about 20 kbps (about 12 kbps in data saver)
- DTX: almost nothing is sent while you are silent
- RED redundancy for lossy mobile networks (off in data saver)
- Simulcast, dynacast and adaptive stream are off
---
 
## Project structure
 
```
/app      Android app (Kotlin + Jetpack Compose)
/server   Token server (Node.js)
```
 
```
app/src/main/java/com/gamervoice/app/
  MainActivity.kt, VoiceApp.kt
  data/      SettingsRepository.kt, TokenApi.kt
  service/   CallManager.kt, CallService.kt
  ui/        AppRoot, JoinScreen, CallScreen, CallViewModel,
             SettingsScreen, KeepAliveHelpScreen, theme/
  util/      SystemSettings.kt
```
 
## How it works
 
1. The app asks your **token server** for a pass.
2. The server checks the room is not full and returns a short-lived token.
3. The app joins the **LiveKit** voice server using that token.
Your LiveKit secret key lives only on the token server and is never inside the app.
 
---
 
## Setup
 
### 1. Create a free LiveKit Cloud project
 
1. Go to [cloud.livekit.io](https://cloud.livekit.io) and sign up.
2. Create a project.
3. Open **Settings > Keys** and copy:
   - WebSocket URL (looks like `wss://myproject-abc123.livekit.cloud`) as `LIVEKIT_URL`
   - API Key as `LIVEKIT_API_KEY`
   - API Secret (shown only once) as `LIVEKIT_API_SECRET`
### 2. Run or deploy the token server
 
The server needs three environment variables:
 
| Variable | Description |
|---|---|
| `LIVEKIT_URL` | Your LiveKit WebSocket URL (`wss://...`) |
| `LIVEKIT_API_KEY` | LiveKit API key |
| `LIVEKIT_API_SECRET` | LiveKit API secret |
 
**Run locally**
 
```bash
cd server
npm install
cp .env.example .env     # then fill in the three values
node index.js
```
 
**Deploy on Render (free tier)**
 
1. Push the `server` folder to a GitHub repo (with `package.json` at the repo root).
2. On [render.com](https://render.com): **New + > Web Service**, pick the repo.
3. Build command: `npm install`, start command: `node index.js`, instance type: Free.
4. Add the three environment variables, then deploy.
5. Copy the service URL (for example `https://your-server.onrender.com`).
> Render's free tier sleeps after about 15 minutes of inactivity. The first join after that can take 30 to 60 seconds. Use a ping service such as UptimeRobot or a paid plan to avoid this.
 
### 3. Configure the Android app
 
Open `gradle.properties` in the project root and set your server URL (no trailing slash):
 
```properties
TOKEN_SERVER_URL=https://your-server.onrender.com
```
 
### 4. Build and run
 
1. Open the project root (the folder that contains `settings.gradle.kts`) in **Android Studio**.
2. Wait for the Gradle sync to finish.
3. Create a run configuration: **Add Configuration > + > Android App**, module `app`.
4. Pick a device and press **Run**.
Requirements: JDK 17, minSdk 24, compile/target SDK 35.
 
> Test on a **real phone** when possible. Emulators are unreliable for microphone and audio routing.
 
---
 
## Testing checklist (use 2 phones, run a game on one)
 
1. Game audio and voice are both audible on speaker, and the game does not pause or get quieter.
2. With headphones on, the teammate does not hear the game audio.
3. Compare keyboard and tap noise in **Standard** and **Strong** modes.
4. A real incoming call mutes the mic, and it turns back on after the call.
5. Play for 20 minutes: no lag or unusual battery drain.
## Tips and limits
 
- Use **headphones**: on phone speaker the mic picks up the game's sound.
- On Bluetooth earbuds, the phone's mic is used. Earbuds are for listening only.
- **Strong** mode currently uses WebRTC filters only. A true AI denoiser (RNNoise) needs native code and is planned as a separate step.
- LiveKit Cloud free plan has a monthly participant-minute quota. Check [livekit.io/pricing](https://livekit.io/pricing). You can self-host LiveKit to remove the limit and just change the three environment variables on the token server.
## Release build
 
Release signing reads from `keystore.properties` in the project root (not committed to git). Create it with your keystore path, passwords and key alias, then build an App Bundle (`.aab`) for Google Play. Play will ask you to declare the microphone foreground service usage ("voice call").
 
## Security
 
Never commit these files (already in `.gitignore`):
 
- `local.properties`
- `keystore.properties`, `*.jks`, `*.keystore`
- `.env`
If a secret is ever pushed by mistake, rotate it immediately (create a new LiveKit API key).
 
## Roadmap
 
- RNNoise AI noise suppression
- Accounts, friends and lobbies (Supabase)
## Tech stack
 
- Android: Kotlin, Jetpack Compose, DataStore, foreground service
- Voice: [LiveKit](https://livekit.io) Android SDK
- Server: Node.js (token generation)
- Hosting: Render (token server), LiveKit Cloud (media)
 

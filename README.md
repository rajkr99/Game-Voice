# GamerVoice — lightweight voice chat for gamers

Audio-only rooms (up to 10 people) that use very little CPU, battery and data, so your game doesn't lag.

```
/app      Android app (Kotlin + Jetpack Compose)
/server   Token server (Node.js) — gives the app permission to join a room
```

How it works: the app asks **your token server** for a pass → the server checks the room isn't full and returns a short-lived pass → the app joins the **LiveKit** voice server with it. Your secret key lives only on the token server.

---

## 1. Create a free LiveKit Cloud project
1. Go to https://cloud.livekit.io and sign up (free).
2. Create a project (any name).
3. Open **Settings → Keys**. Copy:
   - **WebSocket URL** (looks like `wss://myproject-abc123.livekit.cloud`) → `LIVEKIT_URL`
   - **API Key** → `LIVEKIT_API_KEY`
   - **API Secret** (only shown once!) → `LIVEKIT_API_SECRET`

## 2. Deploy the token server on Render (free)
1. Put this folder in a GitHub repository.
2. Go to https://render.com → **New → Web Service** → connect the repo.
3. Settings:
   - Root directory: `server`
   - Build command: `npm install`
   - Start command: `npm start`
   - Instance type: **Free**
4. **Environment** tab → add `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` (values from step 1).
5. Deploy. Open `https://YOUR-NAME.onrender.com/health` — you should see `{"ok":true}`.

> Free Render servers sleep after 15 min idle; the first join after that can take ~30–50 s. That's normal.

Test locally instead: `cd server && cp .env.example .env` (fill it in), then `npm install && node --env-file=.env index.js`.

## 3. Run the app on your phone
1. Install **Android Studio** (latest) from https://developer.android.com/studio.
2. **File → Open** → choose this folder. Let Gradle sync (first time takes a few minutes).
   - If asked to create a Gradle wrapper, accept (or run `gradle wrapper` once).
3. Open `gradle.properties` and set:
   `TOKEN_SERVER_URL=https://YOUR-NAME.onrender.com` (no trailing slash).
   You can also change it later inside the app: **Settings → Advanced**.
4. On your phone: **Settings → About phone** → tap **Build number** 7 times → **Developer options → USB debugging ON**.
5. Plug the phone in, pick it at the top of Android Studio, press ▶ **Run**.

## 4. Build a signed APK to share with friends
1. Android Studio → **Build → Generate Signed App Bundle / APK** → **APK** → Next.
2. **Create new…** keystore: pick a file (e.g. `gamervoice.jks`), set passwords, alias `gamervoice`. **Back this file up** — you need it for every update.
3. Choose **release** → Finish. The APK appears in `app/release/app-release.apk`.
   (Alternative: create `keystore.properties` in the project root with `storeFile`, `storePassword`, `keyAlias`, `keyPassword` and run `./gradlew assembleRelease`.)
4. Send the APK (WhatsApp, Drive, Telegram).
5. Friends: tap the file → Android asks to allow **Install unknown apps** for that app (Chrome/Files/WhatsApp) → allow → Install.

## 5. Test checklist
- [ ] Mute / unmute works, muted icon shows for others
- [ ] Push-to-talk (Settings) — hold to talk, release = muted
- [ ] Switch Wi-Fi ↔ mobile data mid-call → shows "Reconnecting…" then reconnects
- [ ] Run a heavy game (BGMI, Free Fire, CoD) for 20+ minutes — no lag, call stays on
- [ ] Lock screen for 10 minutes — call stays on
- [ ] Bluetooth earbuds — select in the speaker menu; headset button toggles mute
- [ ] Incoming real phone call → voice chat mutes, unmutes after hanging up
- [ ] Battery saver ON
- [ ] 10 people in a room; the 11th sees "Room is full"
- [ ] A low-end phone (2–3 GB RAM)
- [ ] Wrong server URL / airplane mode show a clear message
- [ ] Notification Mute / Leave buttons work

If calls drop in the background, open the app's **Keep call alive** help (❓ icon) and follow the tips for your brand.

## 6. Free tier & self-hosting
- LiveKit Cloud's free **Build** plan includes a monthly quota of participant-minutes (around 5,000/month at the time of writing — check https://livekit.io/pricing). Usage counts **per person**: 10 people × 1 hour = 600 minutes. When you hit the limit, service stops until next month.
- **Self-host** to remove the limit: rent a small VPS (1–2 vCPU, e.g. Hetzner/DigitalOcean) and follow https://docs.livekit.io/home/self-hosting/deployment/ (Docker + generated config, needs a domain for TLS). Then just change `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` on Render — **no app change needed**.

## 7. Later: Google Play
Create a Google Play Console account ($25 one-time), build an **App Bundle** (.aab) instead of APK, and fill in the store listing. Play will ask you to declare the microphone foreground service usage ("voice call").

---

### Why it's light
- Audio only (no camera, no video code paths), mono Opus at ~20 kbps (~12 kbps in data saver)
- DTX: when you're silent almost nothing is sent
- RED redundancy for lossy mobile networks (off in data saver)
- Simulcast / dynacast / adaptive stream off
- Dark, flat UI with no animations or images

## Game + voice audio (v1.1)
- Voice now plays on the **media** stream (same volume as the game) and does not take audio focus, so games are not ducked or forced into call mode.
- Settings → **Noise cancellation**: Off / Standard / Strong. **Clear voice** raises Opus to 32 kbps.
- Use headphones: on the phone speaker the mic can pick up game sound.

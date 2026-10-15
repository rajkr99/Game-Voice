// GamerVoice token server: issues short-lived, audio-only LiveKit tokens.
// The LiveKit API secret lives ONLY here (env vars), never in the app.
import express from "express";
import cors from "cors";
import rateLimit from "express-rate-limit";
import { AccessToken, RoomServiceClient, TrackSource } from "livekit-server-sdk";

const { LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET } = process.env;
const PORT = Number(process.env.PORT || 3000);
const MAX = Number(process.env.MAX_PARTICIPANTS || 10);

if (!LIVEKIT_URL || !LIVEKIT_API_KEY || !LIVEKIT_API_SECRET) {
  console.error("Missing LIVEKIT_URL / LIVEKIT_API_KEY / LIVEKIT_API_SECRET");
  process.exit(1);
}

const httpUrl = LIVEKIT_URL.replace(/^ws/, "http");
const rooms = new RoomServiceClient(httpUrl, LIVEKIT_API_KEY, LIVEKIT_API_SECRET);

const app = express();
app.set("trust proxy", 1); // Render sits behind a proxy
app.use(cors());
app.use(express.json({ limit: "2kb" }));
app.use("/token", rateLimit({ windowMs: 60_000, limit: 20, standardHeaders: true, legacyHeaders: false }));

app.get("/health", (_req, res) => res.json({ ok: true }));

const sanitizeRoom = (s) => String(s ?? "").trim().toUpperCase().replace(/[^A-Z0-9_-]/g, "").slice(0, 32);
const sanitizeName = (s) => String(s ?? "").trim().replace(/[^\p{L}\p{N} _.-]/gu, "").slice(0, 32);

app.post("/token", async (req, res) => {
  const roomName = sanitizeRoom(req.body?.roomName);
  const participantName = sanitizeName(req.body?.participantName);
  if (roomName.length < 3) return res.status(400).json({ error: "Room code must be 3-32 letters or numbers.", code: "bad_room" });
  if (!participantName) return res.status(400).json({ error: "Please enter a name.", code: "bad_name" });

  try {
    // Enforce cap. A room that doesn't exist yet throws / returns empty -> 0 people.
    let count = 0;
    try {
      count = (await rooms.listParticipants(roomName)).length;
    } catch {
      count = 0;
    }
    if (count >= MAX) return res.status(409).json({ error: "Room is full", code: "room_full" });

    // Unique identity so two "Raj"s don't kick each other out.
    const identity = `${participantName}-${Math.random().toString(36).slice(2, 8)}`;
    const at = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET, {
      identity,
      name: participantName,
      ttl: "3h",
    });
    at.addGrant({
      room: roomName,
      roomJoin: true,
      canPublish: true,
      canPublishSources: [TrackSource.MICROPHONE], // audio only
      canSubscribe: true,
      canPublishData: false,
    });
    res.json({ token: await at.toJwt(), url: LIVEKIT_URL });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Server error", code: "server" });
  }
});

app.listen(PORT, () => console.log(`Token server on :${PORT}`));

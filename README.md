# ViKom - TV Caller App

An Android TV audio calling app with contact management, quick dial, and push notifications for incoming calls.

**Platform:** Android TV (SDK 21+) | **Language:** Kotlin | **Architecture:** MVVM

---

## Tech Stack

- **Backend:** Supabase (PostgreSQL + Auth + Realtime + Edge Functions)
- **Calling:** WebRTC (peer-to-peer, STUN/TURN)
- **Signaling:** Supabase Realtime
- **Push Notifications:** Firebase Cloud Messaging (FCM)
- **Security:** EncryptedSharedPreferences, Row Level Security (RLS)

---

## Features

- WebRTC audio calls between devices
- Incoming call push notifications when app is fully closed (FCM)
- Smart quick dial — top 4 contacts by call frequency
- Contact management with search and alphabetical list
- Real-time presence (online/offline/in-call status)
- Call can be attempted regardless of contact's online status
- User profile with avatar selection
- Settings screen (language, ringtone, vibration, auto-answer)
- Secure login with email verification
- TV D-pad navigation optimized

---

## Setup

1. Clone the repo
2. Create `local.properties` in the project root:
```properties
sdk.dir=/path/to/android/sdk
supabase.url=https://your-project.supabase.co
supabase.key=your-anon-key
```
3. Add `google-services.json` (from Firebase Console) into the `app/` folder
4. Set up your Supabase database (tables: `profiles`, `contacts`, `call_history`, `quick_dial`)
5. Add `fcm_token TEXT` column to the `profiles` table
6. Deploy the `send-call-notification` Edge Function and set `FCM_SERVER_KEY` in Supabase secrets
7. Build and run on a real Android TV device

> WebRTC calls only work on real devices — emulators fail at ICE negotiation.

---

## Project Structure

```
calling/        # WebRTC, signaling, FCM messaging service, foreground services
auth/           # Session management (encrypted storage)
datasource/     # Interfaces between ViewModels and repositories
repository/     # Data access layer (Supabase, FCM)
viewmodel/      # CallViewModel, AuthViewModel, ContactDetailViewModel, etc.
ui/             # Activities, fragments, adapters
model/          # Data classes
settings/       # SettingsManager
```

---

## Notes

- Both devices must point at the same Supabase project for calling to work
- FCM requires a Firebase project and the `FCM_SERVER_KEY` set in Supabase Edge Function secrets

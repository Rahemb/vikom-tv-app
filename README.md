# ViKom — TV / Tablet App

The patient-facing half of ViKom. Patients use it on a TV or tablet to take video
calls from their caregiver and to see their own appointments.

The other half is **[ViKom](../ViKom)** — the web portal used by healthcare
personnel, plus the ASP.NET Core backend that both applications share.

**Platform:** Android (minSdk 21) | **Language:** Kotlin | **Architecture:** MVVM

Developed against a Samsung Galaxy Tab A8, and also runs on Android TV devices.

---

## Tech Stack

- **Identity, realtime, calling data:** Supabase (PostgreSQL + Auth + Realtime + Edge Functions)
- **Clinical data:** the ViKom .NET backend (appointments)
- **Calling:** WebRTC (peer-to-peer, STUN/TURN)
- **Signaling:** Supabase Realtime
- **Push notifications:** Firebase Cloud Messaging (FCM)
- **Security:** EncryptedSharedPreferences, Row Level Security (RLS)

Patients sign in against Supabase. The backend accepts that same Supabase token on
its `/api/tv/*` endpoints, so there is no second login.

---

## Features

- WebRTC audio/video calls between devices
- Incoming call push notifications when the app is fully closed (FCM)
- Appointments tab showing the patient's own upcoming and past appointments
- Full-screen notification when a caregiver creates, changes or cancels an appointment
- Smart quick dial — top contacts by call frequency
- Contact management with search and alphabetical list
- Real-time presence (online/offline/in-call status)
- User profile with avatar selection
- Settings screen (language, ringtone, vibration, auto-answer)
- Secure login with email verification
- Norwegian by default, with English available
- D-pad navigation throughout, alongside touch

---

## Setup

1. Clone the repo.

2. Copy `local.properties.example` to `local.properties` and fill it in:

```properties
sdk.dir=/path/to/android/sdk

supabase.url=https://your-project.supabase.co
supabase.key=your-anon-key

backend.base.url=http://127.0.0.1:5084/
```

`backend.base.url` is the backend's address **as seen from the device**, with a
trailing slash. The simplest option for a USB-connected device is to forward the
port down the cable:

```bash
adb reverse tcp:5084 tcp:5084
```

With that in place `127.0.0.1:5084` on the device reaches the backend on your
machine, whatever Wi-Fi network the device is on, and no firewall rule is needed.
Re-run the command after replugging the device. Alternatives are `10.0.2.2:5084`
on an emulator, or your machine's LAN IP if the device is genuinely on the same
network — see `local.properties.example`.

The key may be left blank: the app still builds and runs, and the appointments tab
reports that it cannot reach the system.

3. Add `google-services.json` (from the Firebase Console) into the `app/` folder.

4. Set up Supabase: tables `profiles`, `contacts`, `call_history`, `quick_dial`,
   an `fcm_token TEXT` column on `profiles`, and the `send-call-notification`
   Edge Function with `FCM_SERVER_KEY` set in Supabase secrets.

5. Start the backend from the ViKom repo so the device can reach it:

```bash
dotnet run --project backend --launch-profile http-lan
```

6. Build and run on a real device.

> WebRTC calls only work on real devices — emulators fail at ICE negotiation.

Debug builds are allowed to use plain HTTP (see
`app/src/debug/res/xml/network_security_config.xml`) because the backend runs
without TLS during development. Release builds are not, so a production backend
must be served over HTTPS.

---

## Project Structure

```
calling/        # WebRTC, signaling, FCM messaging service, foreground services
auth/           # Session management (encrypted storage)
network/        # Supabase client and the .NET backend HTTP client
datasource/     # Interfaces between ViewModels and repositories
repository/     # Data access layer (Supabase, backend, FCM)
viewmodel/      # CallViewModel, AppointmentsViewModel, AuthViewModel, etc.
ui/             # Activities, fragments, adapters, shared UI helpers
model/          # Data classes
settings/       # SettingsManager
```

Design tokens live in `res/values/colors.xml`, `dimens.xml` and `styles.xml`, and
mirror the web portal's palette and typography so the two applications look like
one product. Sizes deliberately do **not** match the portal: they scale up per
screen-size bucket, because the primary users are elderly people reading a tablet
at a distance.

---

## Notes

- Both devices must point at the same Supabase project for calling to work
- FCM requires a Firebase project and `FCM_SERVER_KEY` set in Supabase Edge Function secrets
- Only patients whose backend record has a `SupabaseProfileId` receive appointment
  events or appear as callable — that link is currently set only by the backend seeder

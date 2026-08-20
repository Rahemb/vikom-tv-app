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

## Everyday Commands

Android Studio is convenient but not required. Everything below works from a
plain terminal, and is quicker once the app is installed.

### Put `adb` on your PATH first

Every command here starts with `adb`, which lives inside the Android SDK and is
not on the PATH by default. Add it once (Windows PowerShell):

```powershell
[Environment]::SetEnvironmentVariable("Path", $env:Path + ";$env:LOCALAPPDATA\Android\Sdk\platform-tools", "User")
```

On macOS or Linux add `~/Library/Android/sdk/platform-tools` or
`~/Android/Sdk/platform-tools` to your shell profile instead. Reopen the terminal
afterwards, then check it worked:

```bash
adb devices
```

Your device should be listed as `device`. `unauthorized` means you have not yet
accepted the USB-debugging prompt on the tablet; an empty list means USB
debugging is off, or the cable is charge-only.

### Build, install, launch

```bash
./gradlew installDebug                                                    # compile + install
adb shell am start -n com.example.tv_caller_app/.ui.activities.MainActivity   # launch it
```

On Windows use `.\gradlew.bat installDebug`. Note that `installDebug` installs but
does not start the app, so the two commands together are the edit-run loop.

### Stop, restart, reset

```bash
adb shell pidof com.example.tv_caller_app        # running? prints a PID, or nothing
adb shell am force-stop com.example.tv_caller_app   # kill it
adb shell pm clear com.example.tv_caller_app     # wipe all app data
```

`force-stop` kills the process but keeps the stored session, so the app comes
back signed in as the same patient. `pm clear` is the destructive one: it also
erases the saved Supabase login, which is what you want when you need to sign in
as a different test patient, and what you do **not** want otherwise.

### Watching the logs

```bash
adb logcat -c                                                # clear the backlog
adb logcat *:E                                               # errors only, live
adb logcat --pid=$(adb shell pidof com.example.tv_caller_app)   # only this app
```

Clearing the backlog before reproducing a problem is worth the habit — otherwise
you are scrolling through hours of unrelated system messages to find your own.

The `$(...)` in the last command is shell substitution; in Windows PowerShell
write it as `adb logcat --pid=(adb shell pidof com.example.tv_caller_app)`.

### Checking the app can reach the backend

Three separate things have to be true, and the app looks identical when any one
of them is false: an empty appointments tab and no calls arriving. Check them in
this order.

**1. Is the backend running at all?** From the ViKom repo, `dotnet run --project
backend`. To check from outside, `curl http://localhost:5084/swagger/index.html`
should answer `200`. On Windows PowerShell, `netstat -ano | findstr :5084` listing
nothing at all means it is not running.

**2. Is the USB tunnel up?** If `backend.base.url` is `127.0.0.1:5084`, the app
depends entirely on the port forward:

```bash
adb reverse --list                # expect: tcp:5084 tcp:5084
adb reverse tcp:5084 tcp:5084     # re-create it; harmless to run twice
```

The tunnel is dropped whenever the cable is unplugged or the adb server restarts,
and it does not come back on its own. This is the most common cause of "the
backend is broken" when the backend is in fact perfectly healthy.

**3. Is the app itself failing?** Now read `adb logcat`, filtered to the app as
shown above.

### When adb itself misbehaves

```bash
adb kill-server && adb start-server
```

Use this when the device shows as `offline` or stops responding. It drops every
port forward, so re-run `adb reverse tcp:5084 tcp:5084` afterwards.

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

# Picture in picture

PiP keeps the same Steam, game or desktop session visible over another Android
app. The session drawer offers Picture in picture and a global Automatically
enter PiP toggle, off by default. Background always bypasses automatic PiP.

Android owns window placement, sizing, expansion and dismissal. Normal guest
input is disabled while floating. DroidDeck overlays and Thor's second-screen
tools are hidden; expansion restores normal controls and the selected second-screen
mode. Session resolution stays fixed and the image is fitted without cropping.

The menu offers Suspend/Resume and Mute/Unmute. Suspend freezes the complete
Linux session, including downloads. Manual mute affects PulseAudio and DirectAudio
output only, consumes audio normally, and leaves microphone capture and Android
volume untouched. Audio is never automatically muted on entry. PiP exit restores
the output state captured before the first manual toggle.

Closing PiP follows the existing background/suspend preference and does not stop
the session. Screen-off and explicit Steam sleep retain their normal semantics.
Automatic entry requires a ready, unsuspended session with a presented frame;
manual entry can also show a suspended session. Internal pickers and share sheets
must not trigger automatic entry.

## Build

For a debug APK with the generated guest libraries and audio modules:

    DROIDDECK_BUILD_VARIANT=debug tools/build_local.sh

Gradle preBuild compiles the DirectAudio relay from tools/directaudio-relay.
The debug agent state response adds pip, pipMuted, pipMutePending and suspendPending.

## Device acceptance

- Enter manually from Steam, a game and desktop. Check the full image, existing
  guest PID/session ID/output resolution, and absence of overlays.
- Tap PiP for Android's menu; test both actions, resizing/stashing, expand and close.
- Toggle automatic entry; test Home and another app. Verify explicit Background,
  settings, file pickers, share sheets and a denied PiP permission.
- Repeat entry/exit while controls were held. Check for stuck buttons, mouse drags,
  guest touch, gyro input, clipboard sync or keyboard injection.
- On Thor, select a second-screen mode, enter PiP and expand. Check hiding and
  restoration. Repeat with the display disconnected or asleep.
- Test all suspend policies, explicit PiP suspend/resume, screen lock, Steam sleep,
  and stopping the session while floating. Closing PiP must not terminate the guest.
- Check classic and DirectAudio output, new streams and headphones/Bluetooth route
  changes. Muting must keep streams running and leave another app's volume alone.
- Verify launcher/notification Resume expands the existing session and no duplicate
  activity/compositor appears.

Platform references:
- https://developer.android.com/develop/ui/views/picture-in-picture
- https://developer.android.com/reference/android/app/PictureInPictureParams.Builder

## Validation recorded 2026-10-02

- Full debug unit suite: 113 tests, zero failures or errors. The eight focused
  PiP policy/audio tests also passed after the final transition restoration fix.
- Full runtime debug build and APK signature verification passed. Installed by
  update on AYN Thor, Android 13/API 33, with both displays connected. Final APK
  SHA-256: bec6553e2541a67074166e4b87a9282b1ac2b2e1e02f8d699d00cc9c9dc67882.
- Steam: manual entry and automatic Home entry retained session ID, guest PID
  and 1280x720 output. Floating content had no drawer, controls or HUD. Android
  resize and expand worked. Suspend/Resume changed the confirmed session phase
  and displayed the suspended label. Manual mute updated both output controls;
  expansion restored audible state (DirectAudio flag returned from 1 to 0).
- Closing PiP retained the Steam guest and followed the Manual background policy.
  Launcher Resume returned to the same session. Explicit Background bypassed
  automatic entry. A denied PiP app-op opened Android's permission page.
- Thor Keyboard+trackpad hid on entry and reappeared on expansion. The test
  restored second-screen mode to None and automatic entry to off.
- Desktop: final installed APK entered manually with the same guest PID/session
  and 1280x720 output. Stopping while floating completed normally.
- lintDebug could not complete: the existing Compose
  UnrememberedAnimatableDetector crashed on FocusGlide.kt with Kotlin metadata
  2.1.0 exceeding its supported 2.0.0. No unrelated dependency changes were made.

Remaining device coverage: a running game, acoustic output and Bluetooth/headphone
route changes, microphone capture, Android 8-12/14+, a single-screen or unsupported
device, held-input transitions, all sleep policies and disconnected-display
transitions. The acceptance checklist above remains the broader regression matrix.

# DirectAudio relay

Vendored from The412Banner/directaudio, branch feat/linux-relay-mic, commit
e4727e9b1762fadc5345833e8f76845ca79343ed. LGPL-2.1; see COPYING and the source headers.
The guest protocol is unchanged (version 1). Android 28 usage/input-preset
functions are resolved optionally so the helper also loads on Android 26–27.

DroidDeck adds --mute-file: an app-private regular file containing exactly one
byte, mapped read-only by the helper. Zero means audible, one means muted. The
host writes in place, never replaces or truncates a live mapping. Each output
callback consumes its normal audio ring and then silences the output if muted.
Capture is untouched. This also works while the helper is suspended and across
stream creation and route changes, without rebuilding a stream.

Gradle's preBuild compiles this helper with the selected Android NDK (API 26,
16 KB page alignment), so local and CI APKs use this source. To build directly:

    NDK=/path/to/ndk tools/directaudio-relay/build.sh app/src/main/jniLibs/arm64-v8a/libdirectaudiorelay.so

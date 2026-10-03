package com.droiddeck.launcher.audio;

import android.os.Process;
import android.util.Log;

import com.droiddeck.launcher.core.SessionPart;
import com.droiddeck.launcher.core.HostProcess;

import java.io.File;
import java.util.ArrayList;

/**
 * The DirectAudio relay helper, which owns Android's speaker and microphone on behalf of a Linux
 * session.
 *
 * <p>Nothing inside the session can open Android's audio itself. A game is a glibc process in a
 * rootfs that binds no {@code /system}, and a glibc process cannot call libaaudio at all; the
 * session is also never given a way to spawn a bionic helper of its own. So the helper runs out
 * here, under the app's own uid, and the driver inside Wine talks to it over a unix socket. The uid
 * matters for more than tidiness: Android checks the microphone permission against whoever opens
 * the stream, so a helper started any other way could play but never record.
 *
 * <p>It is executed from the app's native library directory because Android will not execute a
 * binary from anywhere else - the same reason the PulseAudio daemon ships as {@code
 * libpulseaudio.so} despite being a program rather than a library.
 *
 * <p>Started before the client, because the driver asks once whether a helper is there, when Wine
 * picks an audio driver, and reports itself unavailable if not. Ported from Bannerlator (GPL-3.0);
 * the relay source is pinned under tools/directaudio-relay and built with the app.
 */
public class DirectAudioRelayComponent extends SessionPart {
    private static final String TAG = "DirectAudioRelay";
    /** The helper, named as a library so Android will run it. */
    public static final String BINARY = "libdirectaudiorelay.so";

    private final File socketPath;
    /** Shared with PulseAudioComponent: one audio.log per session, appended by both. */
    private File logFile;
    /** Null when the microphone was not asked for; the helper then opens no input stream. */
    private final File micFifoPath;
    private volatile int pid = -1;
    private final File muteFile;

    public DirectAudioRelayComponent(File socketPath, File micFifoPath) {
        this.socketPath = socketPath;
        this.muteFile = new File(socketPath.getParentFile(), "output-mute");
        this.micFifoPath = micFifoPath;
    }

    /** Send the helper's output to this file as well as logcat. Set before {@link #start()}. */
    public void setLogFile(File file) {
        this.logFile = file;
    }

    @Override
    public void start() {
        stop();
        File binary = new File(app().getApplicationInfo().nativeLibraryDir, BINARY);
        if (!binary.isFile()) {
            Log.w(TAG, "helper missing at " + binary + "; DirectAudio will report itself unavailable");
            return;
        }
        File parent = socketPath.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        // A socket left by a session that did not shut down cleanly is not a listener, and the
        // helper would fail to bind onto it.
        //noinspection ResultOfMethodCallIgnored
        socketPath.delete();
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(muteFile)) {
            out.write(0);
        } catch (java.io.IOException e) {
            Log.w(TAG, "could not create output mute flag", e);
            return;
        }

        // PulseAudio's pipe module insists on creating the pipe itself and fails if one is already
        // there. The helper creates one too when the path is missing, and it starts a few
        // milliseconds after the daemon is exec'd - well before the daemon has read its config. So
        // left to itself the helper won its own race, the daemon's mkfifo failed with EEXIST, and
        // the microphone was lost while everything else came up. Wait for the daemon to make the
        // pipe before the helper runs; it appears within a second, and if it never does the helper
        // starts anyway and creates it, which is the right thing when the daemon is not involved.
        if (micFifoPath != null) {
            long deadline = System.currentTimeMillis() + 3000L;
            while (!micFifoPath.exists() && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            Log.i(TAG, micFifoPath.exists() ? "microphone pipe made by the daemon; starting helper"
                    : "daemon did not make the microphone pipe in 3 s; helper will make it");
        }

        StringBuilder command = new StringBuilder(binary.getAbsolutePath());
        command.append(" --socket ").append(socketPath.getAbsolutePath());
        // Diagnostics into the session's audio.log: every 1000 callbacks the device buffer,
        // Android's own xrun count and the ring's fill, plus every buffer change.
        command.append(" --log --mute-file ").append(muteFile.getAbsolutePath());
        if (micFifoPath != null) {
            // The helper creates the pipe if it is not there. PulseAudio is told the same path and
            // reads from the other end of it.
            command.append(" --mic-fifo ").append(micFifoPath.getAbsolutePath());
        }
        ArrayList<String> env = new ArrayList<>();
        env.add("HOME=" + app().getFilesDir());
        java.io.PrintWriter out = null;
        if (logFile != null) {
            try {
                out = new java.io.PrintWriter(new java.io.FileWriter(logFile, true));
                out.println("== DirectAudio relay helper starting"
                        + (micFifoPath != null ? " with the microphone" : " (no microphone)"));
                out.flush();
            } catch (Exception e) {
                Log.w(TAG, "could not open " + logFile, e);
            }
        }
        final java.io.PrintWriter log = out;
        pid = HostProcess.start(command.toString(), env.toArray(new String[0]),
                app().getFilesDir(), null,
                line -> {
                    Log.i(TAG, line);
                    if (log != null) synchronized (log) { log.println(line); log.flush(); }
                });
        Log.i(TAG, "started pid=" + pid + " socket=" + socketPath
                + (micFifoPath != null ? " mic=" + micFifoPath : " (no microphone)"));
    }

    @Override
    public void stop() {
        if (pid != -1) {
            // The helper exits on a signal and tears down its streams; there is nothing below it to
            // orphan, unlike the proot tree.
            Process.killProcess(pid);
            pid = -1;
        }
    }

    public Boolean getOutputMuted() {
        if (pid <= 1) return null;
        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(muteFile, "r")) {
            return file.readUnsignedByte() != 0;
        } catch (java.io.IOException e) { return null; }
    }

    /** Write in place: truncating or replacing this file would invalidate the relay's mapping. */
    public boolean setOutputMuted(boolean muted) {
        if (pid <= 1 || !muteFile.isFile()) return false;
        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(muteFile, "rw")) {
            if (file.length() != 1) return false;
            file.write(muted ? 1 : 0);
            return true;
        } catch (java.io.IOException e) {
            Log.w(TAG, "could not change output mute", e);
            return false;
        }
    }

    @Override
    public int suspendPid() {
        return pid;
    }
}

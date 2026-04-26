/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.client;

import netchat.shared.media.MediaMode;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.awt.AWTException;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientMediaController {
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(16_000.0f, 16, 1, true, false);
    private static final int AUDIO_PACKET_SIZE = 1024;
    private static final int VIDEO_MAX_PACKET = 60_000;
    private static final int VIDEO_FRAME_DELAY_MILLIS = 180;

    private final DatagramSocket audioSocket;
    private final DatagramSocket videoSocket;
    private final VideoWindow videoWindow;
    private final AtomicBoolean callActive = new AtomicBoolean(false);
    private final AtomicBoolean voiceEnabled = new AtomicBoolean(true);
    private final AtomicBoolean videoEnabled = new AtomicBoolean(true);

    private volatile InetAddress peerAddress;
    private volatile int peerAudioPort;
    private volatile int peerVideoPort;
    private volatile MediaMode mediaMode = MediaMode.AUDIO_ONLY;
    private volatile String peerUsername = "";

    public ClientMediaController() {
        try {
            this.audioSocket = new DatagramSocket();
            this.videoSocket = new DatagramSocket();
        } catch (SocketException exception) {
            throw new IllegalStateException("Could not open local media sockets.", exception);
        }
        this.videoWindow = new VideoWindow();
        startAudioReceiver();
        startVideoReceiver();
    }

    public int getLocalAudioPort() {
        return audioSocket.getLocalPort();
    }

    public int getLocalVideoPort() {
        return videoSocket.getLocalPort();
    }

    public void startCall(String peerHost, int audioPort, int videoPort, MediaMode mediaMode, String peerUsername) {
        try {
            this.peerAddress = InetAddress.getByName(peerHost);
            this.peerAudioPort = audioPort;
            this.peerVideoPort = videoPort;
            this.mediaMode = mediaMode;
            this.peerUsername = peerUsername;
            this.callActive.set(true);
            this.voiceEnabled.set(true);
            this.videoEnabled.set(mediaMode.includesVideo());
            startAudioSender();
            if (mediaMode.includesVideo()) {
                startVideoSender();
            } else {
                videoWindow.hide();
            }
            System.out.println("Realtime call active with " + peerUsername + ".");
        } catch (Exception exception) {
            System.out.println("Could not start media transport: " + exception.getMessage());
        }
    }

    public void stopCall() {
        callActive.set(false);
        videoWindow.hide();
        System.out.println("Realtime media stopped.");
    }

    public void setVoiceEnabled(boolean enabled) {
        voiceEnabled.set(enabled);
        System.out.println("Voice transmission " + (enabled ? "enabled." : "disabled."));
    }

    public void setVideoEnabled(boolean enabled) {
        if (!mediaMode.includesVideo() && enabled) {
            System.out.println("This call does not include video.");
            return;
        }
        videoEnabled.set(enabled);
        if (!enabled) {
            videoWindow.hide();
        }
        System.out.println("Video transmission " + (enabled ? "enabled." : "disabled."));
    }

    private void startAudioReceiver() {
        Thread thread = new Thread(() -> {
            try (SourceDataLine speaker = openSpeakerLine()) {
                byte[] buffer = new byte[AUDIO_PACKET_SIZE];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    audioSocket.receive(packet);
                    if (callActive.get()) {
                        speaker.write(packet.getData(), 0, packet.getLength());
                    }
                }
            } catch (Exception exception) {
                System.out.println("Audio receiver unavailable: " + exception.getMessage());
            }
        }, "netchat-audio-receiver");
        thread.setDaemon(true);
        thread.start();
    }

    private void startVideoReceiver() {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[VIDEO_MAX_PACKET];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    videoSocket.receive(packet);
                    if (!callActive.get() || !mediaMode.includesVideo()) {
                        continue;
                    }
                    ByteArrayInputStream input = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                    BufferedImage image = ImageIO.read(input);
                    if (image != null) {
                        videoWindow.showFrame(image);
                    }
                } catch (IOException exception) {
                    System.out.println("Video receiver unavailable: " + exception.getMessage());
                    return;
                }
            }
        }, "netchat-video-receiver");
        thread.setDaemon(true);
        thread.start();
    }

    private void startAudioSender() {
        Thread thread = new Thread(() -> {
            try (TargetDataLine microphone = openMicrophoneLine()) {
                byte[] buffer = new byte[AUDIO_PACKET_SIZE];
                while (callActive.get()) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0 && voiceEnabled.get() && peerAddress != null) {
                        DatagramPacket packet = new DatagramPacket(buffer, bytesRead, peerAddress, peerAudioPort);
                        audioSocket.send(packet);
                    }
                }
            } catch (Exception exception) {
                System.out.println("Audio sender unavailable: " + exception.getMessage());
            }
        }, "netchat-audio-sender");
        thread.setDaemon(true);
        thread.start();
    }

    private void startVideoSender() {
        Thread thread = new Thread(() -> {
            try {
                Robot robot = createRobot();
                Rectangle captureArea = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                while (callActive.get() && mediaMode.includesVideo()) {
                    if (videoEnabled.get() && peerAddress != null) {
                        BufferedImage screenshot = robot.createScreenCapture(captureArea);
                        BufferedImage scaled = scaleFrame(screenshot, 480, 270);
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        ImageIO.write(scaled, "jpg", output);
                        byte[] bytes = output.toByteArray();
                        if (bytes.length <= VIDEO_MAX_PACKET) {
                            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, peerAddress, peerVideoPort);
                            videoSocket.send(packet);
                        }
                    }
                    Thread.sleep(VIDEO_FRAME_DELAY_MILLIS);
                }
            } catch (Exception exception) {
                System.out.println("Video sender unavailable: " + exception.getMessage());
            }
        }, "netchat-video-sender");
        thread.setDaemon(true);
        thread.start();
    }

    private SourceDataLine openSpeakerLine() throws Exception {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(AUDIO_FORMAT);
        line.start();
        return line;
    }

    private TargetDataLine openMicrophoneLine() throws Exception {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(AUDIO_FORMAT);
        line.start();
        return line;
    }

    private Robot createRobot() throws AWTException {
        return new Robot();
    }

    private BufferedImage scaleFrame(BufferedImage input, int width, int height) {
        Image scaledImage = input.getScaledInstance(width, height, Image.SCALE_FAST);
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        graphics.drawImage(scaledImage, 0, 0, null);
        graphics.dispose();
        return output;
    }
}


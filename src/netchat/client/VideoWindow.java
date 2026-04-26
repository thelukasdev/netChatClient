/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.client;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.HeadlessException;
import java.awt.image.BufferedImage;

public class VideoWindow {
    private final JFrame frame;
    private final JLabel label;

    public VideoWindow() {
        JFrame createdFrame;
        JLabel createdLabel;
        try {
            createdFrame = new JFrame("NetChat Live Video");
            createdLabel = new JLabel("Waiting for live frames...");
            createdFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            createdFrame.add(createdLabel);
            createdFrame.setSize(640, 400);
        } catch (HeadlessException exception) {
            createdFrame = null;
            createdLabel = null;
        }
        frame = createdFrame;
        label = createdLabel;
    }

    public void showFrame(BufferedImage image) {
        if (frame == null || label == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            label.setIcon(new ImageIcon(image));
            label.setText(null);
            if (!frame.isVisible()) {
                frame.setVisible(true);
            }
        });
    }

    public void hide() {
        if (frame == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> frame.setVisible(false));
    }
}


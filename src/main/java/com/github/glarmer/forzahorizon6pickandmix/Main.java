package com.github.glarmer.forzahorizon6pickandmix;

import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            CheckBoxFrame frame = new CheckBoxFrame();
            frame.setVisible(true);
        });
    }

}

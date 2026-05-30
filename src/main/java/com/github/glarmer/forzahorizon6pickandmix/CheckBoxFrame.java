package com.github.glarmer.forzahorizon6pickandmix;

import javax.swing.JFrame;

public class CheckBoxFrame extends JFrame {

    public CheckBoxFrame() {
        setTitle("Forza Horizon 6 Pick And Mix Radio");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 720);
        setLocationRelativeTo(null);

        CheckBoxPanel panel = new CheckBoxPanel();
        add(panel);
    }
}

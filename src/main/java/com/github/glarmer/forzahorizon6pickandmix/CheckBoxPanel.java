package com.github.glarmer.forzahorizon6pickandmix;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CheckBoxPanel extends JPanel {
    private static final String RADIO_XML_HINT = "Radio XML files are located in `ForzaHorizon6/media/Audio/`";

    private final RadioInfoReader radioInfoReader;
    private final Map<JCheckBox, IRadioStation> stationCheckBoxes;
    private final JPanel sourceStationPanel;
    private final JLabel selectedFileLabel;
    private final JComboBox<IRadioStation> targetStationDropdown;
    private final JButton submitButton;

    private Path selectedRadioInfoPath;

    public CheckBoxPanel() {
        radioInfoReader = new RadioInfoReader();
        stationCheckBoxes = new LinkedHashMap<>();
        sourceStationPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        selectedFileLabel = new JLabel("No RadioInfo XML selected");
        targetStationDropdown = new JComboBox<>();
        submitButton = new JButton("Submit");

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        addComponents();
        configureTargetStationDropdown();
        addSubmitButtonListener();
        setStationControlsEnabled(false);
    }

    private void addComponents() {
        JPanel filePanel = new JPanel(new BorderLayout(8, 8));
        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(event -> browseForRadioInfoXml());

        JLabel hintLabel = new JLabel(RADIO_XML_HINT);
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.ITALIC));

        filePanel.setBorder(BorderFactory.createTitledBorder("Radio File Selection"));
        filePanel.add(selectedFileLabel, BorderLayout.CENTER);
        filePanel.add(browseButton, BorderLayout.EAST);
        filePanel.add(hintLabel, BorderLayout.SOUTH);

        sourceStationPanel.setBorder(BorderFactory.createTitledBorder("Include songs from"));

        JPanel targetStationPanel = new JPanel(new BorderLayout(8, 8));
        targetStationPanel.setBorder(BorderFactory.createTitledBorder("Insert songs into"));
        targetStationPanel.add(targetStationDropdown, BorderLayout.CENTER);
        targetStationPanel.add(submitButton, BorderLayout.EAST);

        add(filePanel, BorderLayout.NORTH);
        add(new JScrollPane(sourceStationPanel), BorderLayout.CENTER);
        add(targetStationPanel, BorderLayout.SOUTH);
    }

    private void configureTargetStationDropdown() {
        targetStationDropdown.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus
            ) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (value instanceof IRadioStation radioStation) {
                    setText(radioStation.name());
                }

                return this;
            }
        });
    }

    private void browseForRadioInfoXml() {
        Path defaultAudioFolder = findDefaultSteamAudioFolder();

        JFileChooser fileChooser = getJFileChooser(defaultAudioFolder);

        int result = fileChooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selectedPath = fileChooser.getSelectedFile().toPath();
        try {
            validateRadioInfoFile(selectedPath);
            List<IRadioStation> radioStations = radioInfoReader.readRadioStations(selectedPath);
            if (radioStations.isEmpty()) {
                throw new IOException("The selected file does not contain any radio stations.");
            }

            selectedRadioInfoPath = selectedPath;
            selectedFileLabel.setText(selectedPath.toString());
            populateRadioStations(radioStations);
        } catch (IOException | IllegalArgumentException exception) {
            selectedRadioInfoPath = null;
            clearRadioStations();
            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    "Invalid RadioInfo XML",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private static JFileChooser getJFileChooser(Path defaultAudioFolder) {
        JFileChooser fileChooser;

        if (defaultAudioFolder != null) {
            fileChooser = new JFileChooser(defaultAudioFolder.toFile());
        } else {
            fileChooser = new JFileChooser();
        }
        fileChooser.setDialogTitle("Choose RadioInfo XML");
        fileChooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(java.io.File file) {
                return file.isDirectory() || isAllowedRadioInfoFile(file.toPath());
            }

            @Override
            public String getDescription() {
                return "RadioInfo XML files (RadioInfo*.xml)";
            }
        });
        return fileChooser;
    }

    private static Path findDefaultSteamAudioFolder() {
        String os = System.getProperty("os.name").toLowerCase();

        List<Path> possiblePaths = new ArrayList<>();

        if (os.contains("win")) {
            String programFilesX86 = System.getenv("ProgramFiles(x86)");
            String programFiles = System.getenv("ProgramFiles");

            if (programFilesX86 != null) {
                possiblePaths.add(Path.of(
                        programFilesX86,
                        "Steam",
                        "steamapps",
                        "common",
                        "ForzaHorizon6",
                        "media",
                        "Audio"
                ));
            }

            if (programFiles != null) {
                possiblePaths.add(Path.of(
                        programFiles,
                        "Steam",
                        "steamapps",
                        "common",
                        "ForzaHorizon6",
                        "media",
                        "Audio"
                ));
            }
        } else {
            String home = System.getProperty("user.home");

            possiblePaths.add(Path.of(
                    home,
                    ".steam",
                    "steam",
                    "steamapps",
                    "common",
                    "ForzaHorizon6",
                    "media",
                    "Audio"
            ));

            possiblePaths.add(Path.of(
                    home,
                    ".local",
                    "share",
                    "Steam",
                    "steamapps",
                    "common",
                    "ForzaHorizon6",
                    "media",
                    "Audio"
            ));
        }

        for (Path path : possiblePaths) {
            if (Files.isDirectory(path)) {
                return path;
            }
        }

        return null;
    }

    private void populateRadioStations(List<IRadioStation> radioStations) {
        clearRadioStations();

        for (IRadioStation radioStation : radioStations) {
            JCheckBox checkBox = new JCheckBox(radioStation.name());
            stationCheckBoxes.put(checkBox, radioStation);
            sourceStationPanel.add(checkBox);
            targetStationDropdown.addItem(radioStation);
        }

        setStationControlsEnabled(true);
        sourceStationPanel.revalidate();
        sourceStationPanel.repaint();
    }

    private void clearRadioStations() {
        stationCheckBoxes.clear();
        sourceStationPanel.removeAll();
        targetStationDropdown.removeAllItems();
        setStationControlsEnabled(false);
        sourceStationPanel.revalidate();
        sourceStationPanel.repaint();
    }

    private void setStationControlsEnabled(boolean enabled) {
        targetStationDropdown.setEnabled(enabled);
        submitButton.setEnabled(enabled);
    }

    private void addSubmitButtonListener() {
        submitButton.addActionListener(event -> handleSubmit());
    }

    private void handleSubmit() {
        if (selectedRadioInfoPath == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Choose a RadioInfo XML file first.",
                    "No file selected",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        List<IRadioStation> selectedStations = getSelectedStations();

        if (selectedStations.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "No options selected.",
                    "Result",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } else {
            IRadioStation targetStation = getTargetStation();
            int includedSongCount = countIncludedSongs(selectedStations);
            String message = "Insert " + includedSongCount + " songs into "
                    + targetStation.name()
                    + " from:\n"
                    + String.join("\n", selectedStations.stream().map(IRadioStation::name).toList());

            int selectedOption = JOptionPane.showOptionDialog(
                    this,
                    message,
                    "Result",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    new Object[]{"Confirm", "Cancel"},
                    "Confirm"
            );

            if (selectedOption == 0) {
                saveChanges(targetStation, selectedStations);
            }
        }
    }

    private List<IRadioStation> getSelectedStations() {
        List<IRadioStation> selectedStations = new ArrayList<>();

        for (Map.Entry<JCheckBox, IRadioStation> entry : stationCheckBoxes.entrySet()) {
            if (entry.getKey().isSelected()) {
                selectedStations.add(entry.getValue());
            }
        }

        return selectedStations;
    }

    private IRadioStation getTargetStation() {
        return (IRadioStation) targetStationDropdown.getSelectedItem();
    }

    private int countIncludedSongs(List<IRadioStation> selectedStations) {
        int includedSongCount = 0;

        for (IRadioStation selectedStation : selectedStations) {
            includedSongCount += countSongs(selectedStation);
        }

        return includedSongCount;
    }

    private int countSongs(IRadioStation radioStation) {
        return radioStation.sampleLists()
                .stream()
                .filter(sampleList -> sampleList.type() == IRadioStation.SampleListType.TRACK)
                .mapToInt(sampleList -> sampleList.samples().size())
                .sum();
    }

    private void saveChanges(IRadioStation targetStation, List<IRadioStation> selectedStations) {
        try {
            Path backupPath = new RadioInfoEditor(selectedRadioInfoPath).applyPickAndMix(targetStation, selectedStations);
            JOptionPane.showMessageDialog(
                    this,
                    "Saved changes to " + selectedRadioInfoPath.getFileName() + ".\nBackup saved as "
                            + backupPath.getFileName() + ".\n" +
                            "You can now close this window and play the game.",
                    "Saved Radio",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    "Save failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private static void validateRadioInfoFile(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Choose an XML file, not a folder.");
        }

        if (!isAllowedRadioInfoFile(path)) {
            throw new IOException("Choose a file named RadioInfo*.xml.");
        }
    }

    private static boolean isAllowedRadioInfoFile(Path path) {
        Path fileNamePath = path.getFileName();
        if (fileNamePath == null) {
            return false;
        }

        String fileName = fileNamePath.toString();
        return fileName.startsWith("RadioInfo") && fileName.toLowerCase().endsWith(".xml");
    }
}

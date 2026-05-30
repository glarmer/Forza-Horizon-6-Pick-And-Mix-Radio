package com.github.glarmer.forzahorizon6pickandmix;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CheckBoxPanel extends JPanel {
    private static final String RADIO_XML_HINT = "Radio XML files are located in `ForzaHorizon6/media/Audio/`";

    private final RadioInfoReader radioInfoReader;
    private final List<AdvancedStationSelection> advancedStationSelections;
    private final JPanel advancedSourceStationPanel;
    private final JLabel selectedFileLabel;
    private final JComboBox<IRadioStation> targetStationDropdown;
    private final JButton submitButton;

    private Path selectedRadioInfoPath;

    public CheckBoxPanel() {
        radioInfoReader = new RadioInfoReader();
        advancedStationSelections = new ArrayList<>();
        advancedSourceStationPanel = new JPanel();
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

        advancedSourceStationPanel.setLayout(new BoxLayout(advancedSourceStationPanel, BoxLayout.Y_AXIS));
        advancedSourceStationPanel.setBorder(BorderFactory.createTitledBorder("Include individual songs from"));

        JPanel targetStationPanel = new JPanel(new BorderLayout(8, 8));
        targetStationPanel.setBorder(BorderFactory.createTitledBorder("Insert songs into"));
        targetStationPanel.add(targetStationDropdown, BorderLayout.CENTER);
        targetStationPanel.add(submitButton, BorderLayout.EAST);

        JScrollPane advancedSourceScrollPane = new JScrollPane(advancedSourceStationPanel);
        advancedSourceScrollPane.setBorder(BorderFactory.createEmptyBorder());
        advancedSourceScrollPane.setViewportBorder(null);
        configureScrollSpeed(advancedSourceScrollPane);

        add(filePanel, BorderLayout.NORTH);
        add(advancedSourceScrollPane, BorderLayout.CENTER);
        add(targetStationPanel, BorderLayout.SOUTH);
    }

    private static void configureScrollSpeed(JScrollPane scrollPane) {
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);
        scrollPane.getVerticalScrollBar().setBlockIncrement(160);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(12);
        scrollPane.getHorizontalScrollBar().setBlockIncrement(160);
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
            addAdvancedStationSelection(radioStation);
            targetStationDropdown.addItem(radioStation);
        }

        setStationControlsEnabled(true);
        refreshSourceStationPanels();
    }

    private void clearRadioStations() {
        advancedStationSelections.clear();
        advancedSourceStationPanel.removeAll();
        targetStationDropdown.removeAllItems();
        setStationControlsEnabled(false);
        refreshSourceStationPanels();
    }

    private void addAdvancedStationSelection(IRadioStation radioStation) {
        AdvancedStationSelection selection = new AdvancedStationSelection(radioStation);
        advancedStationSelections.add(selection);
        advancedSourceStationPanel.add(selection.panel());
        advancedSourceStationPanel.add(Box.createVerticalStrut(8));
    }

    private void refreshSourceStationPanels() {
        advancedSourceStationPanel.revalidate();
        advancedSourceStationPanel.repaint();
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
                    + String.join("\n", selectedStations.stream().map(this::stationSummary).toList());

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

        for (AdvancedStationSelection selection : advancedStationSelections) {
            IRadioStation selectedStation = selection.selectedStation();
            if (selectedStation != null) {
                selectedStations.add(selectedStation);
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

    private String stationSummary(IRadioStation radioStation) {
        return radioStation.name() + " (" + countSongs(radioStation) + " songs)";
    }

    private static List<IRadioStation.Sample> trackSamples(IRadioStation radioStation) {
        return radioStation.sampleLists()
                .stream()
                .filter(sampleList -> sampleList.type() == IRadioStation.SampleListType.TRACK)
                .flatMap(sampleList -> sampleList.samples().stream())
                .toList();
    }

    private static String songLabel(IRadioStation.Sample sample) {
        String displayName = sample.displayName();
        String artist = sample.artist();

        if (displayName != null && !displayName.isBlank()) {
            if (artist != null && !artist.isBlank()) {
                return artist + " - " + displayName;
            }

            return displayName;
        }

        return sample.soundName();
    }

    private static IRadioStation filterStation(
            IRadioStation radioStation,
            List<IRadioStation.Sample> selectedTrackSamples
    ) {
        Set<String> selectedTrackSoundNames = selectedTrackSoundNames(selectedTrackSamples);
        Set<String> selectedSongKeys = selectedSongKeys(selectedTrackSamples);
        List<IRadioStation.SampleList> sampleLists = radioStation.sampleLists()
                .stream()
                .map(sampleList -> filterSampleList(sampleList, selectedTrackSoundNames, selectedSongKeys))
                .toList();
        List<IRadioStation.PlayList> playLists = radioStation.playLists()
                .stream()
                .map(playList -> filterPlayList(playList, selectedTrackSoundNames))
                .toList();

        return new IRadioStation.RadioStation(
                radioStation.name(),
                radioStation.number(),
                radioStation.djCharId(),
                radioStation.mediaTrackRestrictions(),
                radioStation.banks(),
                sampleLists,
                playLists
        );
    }

    private static Set<String> selectedTrackSoundNames(List<IRadioStation.Sample> selectedTrackSamples) {
        Set<String> selectedTrackSoundNames = new HashSet<>();

        for (IRadioStation.Sample selectedTrackSample : selectedTrackSamples) {
            selectedTrackSoundNames.add(selectedTrackSample.soundName());
        }

        return selectedTrackSoundNames;
    }

    private static Set<String> selectedSongKeys(List<IRadioStation.Sample> selectedTrackSamples) {
        Set<String> selectedSongKeys = new HashSet<>();

        for (IRadioStation.Sample selectedTrackSample : selectedTrackSamples) {
            String songKey = metadataSongKey(selectedTrackSample);
            if (songKey != null) {
                selectedSongKeys.add(songKey);
            }
        }

        return selectedSongKeys;
    }

    private static IRadioStation.SampleList filterSampleList(
            IRadioStation.SampleList sampleList,
            Set<String> selectedTrackSoundNames,
            Set<String> selectedSongKeys
    ) {
        if (sampleList.type() != IRadioStation.SampleListType.TRACK
                && sampleList.type() != IRadioStation.SampleListType.TRACK_LFE) {
            return sampleList;
        }

        return new IRadioStation.SampleList(
                sampleList.type(),
                sampleList.event(),
                sampleList.samples()
                        .stream()
                        .filter(sample -> selectedTrackSoundNames.contains(sample.soundName())
                                || selectedSongKeys.contains(metadataSongKey(sample)))
                        .toList()
        );
    }

    private static IRadioStation.PlayList filterPlayList(
            IRadioStation.PlayList playList,
            Set<String> selectedTrackSoundNames
    ) {
        if (playList.type() != IRadioStation.PlayListType.FREE_ROAM
                && playList.type() != IRadioStation.PlayListType.EVENT) {
            return playList;
        }

        return new IRadioStation.PlayList(
                playList.type(),
                playList.entries()
                        .stream()
                        .filter(entry -> selectedTrackSoundNames.contains(entry.name()))
                        .toList()
        );
    }

    private static String metadataSongKey(IRadioStation.Sample sample) {
        String displayName = sample.displayName() == null ? "" : sample.displayName();
        String artist = sample.artist() == null ? "" : sample.artist();
        if (displayName.isBlank() && artist.isBlank()) {
            return null;
        }

        return artist + "\n" + displayName;
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

    private static final class AdvancedStationSelection {
        private final IRadioStation radioStation;
        private final JPanel panel;
        private final JCheckBox stationCheckBox;
        private final JPanel songPanel;
        private final JScrollPane songScrollPane;
        private final JButton toggleSongsButton;
        private final Map<JCheckBox, IRadioStation.Sample> songCheckBoxes;
        private boolean updating;

        private AdvancedStationSelection(IRadioStation radioStation) {
            this.radioStation = radioStation;
            panel = new JPanel(new BorderLayout(8, 8));
            stationCheckBox = new JCheckBox(radioStation.name());
            songPanel = new JPanel(new GridLayout(0, 1, 3, 3));
            songScrollPane = new JScrollPane(songPanel);
            toggleSongsButton = new JButton("Show songs");
            songCheckBoxes = new LinkedHashMap<>();

            buildPanel();
        }

        private JPanel panel() {
            return panel;
        }

        private void buildPanel() {
            panel.setBorder(BorderFactory.createEtchedBorder());
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));

            JPanel headerPanel = new JPanel(new BorderLayout(8, 8));
            headerPanel.add(stationCheckBox, BorderLayout.CENTER);
            headerPanel.add(toggleSongsButton, BorderLayout.EAST);

            List<IRadioStation.Sample> samples = trackSamples(radioStation);
            stationCheckBox.setText(radioStation.name() + " (" + samples.size() + " songs)");
            stationCheckBox.addActionListener(event -> setAllSongsSelected(stationCheckBox.isSelected()));

            for (IRadioStation.Sample sample : samples) {
                JCheckBox songCheckBox = new JCheckBox(songLabel(sample));
                songCheckBox.addActionListener(event -> updateStationSelectionState());
                songCheckBoxes.put(songCheckBox, sample);
                songPanel.add(songCheckBox);
            }

            songScrollPane.setPreferredSize(new Dimension(600, 160));
            songScrollPane.setVisible(false);
            configureScrollSpeed(songScrollPane);
            toggleSongsButton.addActionListener(event -> toggleSongs());

            panel.add(headerPanel, BorderLayout.NORTH);
            panel.add(songScrollPane, BorderLayout.CENTER);
        }

        private void setAllSongsSelected(boolean selected) {
            if (updating) {
                return;
            }

            updating = true;
            for (JCheckBox songCheckBox : songCheckBoxes.keySet()) {
                songCheckBox.setSelected(selected);
            }
            updating = false;
        }

        private void updateStationSelectionState() {
            if (updating) {
                return;
            }

            updating = true;
            stationCheckBox.setSelected(!songCheckBoxes.isEmpty()
                    && songCheckBoxes.keySet().stream().allMatch(JCheckBox::isSelected));
            updating = false;
        }

        private void toggleSongs() {
            boolean visible = !songScrollPane.isVisible();
            songScrollPane.setVisible(visible);
            toggleSongsButton.setText(visible ? "Hide songs" : "Show songs");
            panel.revalidate();
        }

        private IRadioStation selectedStation() {
            List<IRadioStation.Sample> selectedTrackSamples = new ArrayList<>();

            for (Map.Entry<JCheckBox, IRadioStation.Sample> entry : songCheckBoxes.entrySet()) {
                if (entry.getKey().isSelected()) {
                    selectedTrackSamples.add(entry.getValue());
                }
            }

            if (selectedTrackSamples.isEmpty()) {
                return null;
            }

            return filterStation(radioStation, selectedTrackSamples);
        }
    }
}

package com.github.glarmer.forzahorizon6pickandmix;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RadioInfoEditor {
    public static final Path DEFAULT_RADIO_INFO_PATH = Path.of(
            "src",
            "main",
            "resources",
            "media",
            "Audio",
            "RadioInfo_EN.xml"
    );

    private final Path radioInfoPath;

    public RadioInfoEditor() {
        this(DEFAULT_RADIO_INFO_PATH);
    }

    public RadioInfoEditor(Path radioInfoPath) {
        this.radioInfoPath = radioInfoPath;
    }

    public Path applyPickAndMix(
            IRadioStation targetStation,
            List<IRadioStation> sourceStations
    ) throws IOException {
        Path backupPath = backupPath(radioInfoPath);
        Files.copy(radioInfoPath, backupPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);

        Document document = readDocument(radioInfoPath);
        Element targetStationElement = findStationElement(document, targetStation);
        List<Element> sourceStationElements = sourceStationSnapshots(document, sourceStations);
        Map<Integer, IRadioStation> sourceStationsByNumber = sourceStationsByNumber(sourceStations);

        mergeBanks(document, targetStationElement, sourceStationElements);
        replaceSampleList(
                document,
                targetStationElement,
                sourceStationElements,
                sourceStationsByNumber,
                IRadioStation.SampleListType.TRACK
        );
        replaceSampleList(
                document,
                targetStationElement,
                sourceStationElements,
                sourceStationsByNumber,
                IRadioStation.SampleListType.TRACK_LFE
        );
        replacePlayList(
                document,
                targetStationElement,
                sourceStationElements,
                sourceStationsByNumber,
                IRadioStation.PlayListType.FREE_ROAM
        );
        replacePlayList(
                document,
                targetStationElement,
                sourceStationElements,
                sourceStationsByNumber,
                IRadioStation.PlayListType.EVENT
        );

        writeDocument(document, radioInfoPath);
        return backupPath;
    }

    private Document readDocument(Path radioInfoPath) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(radioInfoPath.toFile());
        } catch (ParserConfigurationException | SAXException exception) {
            throw new IOException("Could not parse " + radioInfoPath + ".", exception);
        }
    }

    private Element findStationElement(Document document, IRadioStation station) throws IOException {
        Element radioStationsElement = firstDirectChild(document.getDocumentElement(), "RadioStations");
        if (radioStationsElement == null) {
            throw new IOException("RadioInfo XML does not contain a RadioStations element.");
        }

        for (Element radioStationElement : directChildren(radioStationsElement, "RadioStation")) {
            if (radioStationElement.getAttribute("Number").equals(Integer.toString(station.number()))) {
                return radioStationElement;
            }
        }

        throw new IOException("Could not find radio station " + station.name() + " in RadioInfo XML.");
    }

    private List<Element> findStationElements(
            Document document,
            List<IRadioStation> sourceStations
    ) throws IOException {
        List<Element> stationElements = new ArrayList<>();

        for (IRadioStation sourceStation : sourceStations) {
            stationElements.add(findStationElement(document, sourceStation));
        }

        return stationElements;
    }

    private List<Element> sourceStationSnapshots(
            Document document,
            List<IRadioStation> sourceStations
    ) throws IOException {
        return findStationElements(document, sourceStations)
                .stream()
                .map(sourceStationElement -> (Element) sourceStationElement.cloneNode(true))
                .toList();
    }

    private void mergeBanks(
            Document document,
            Element targetStationElement,
            List<Element> sourceStationElements
    ) {
        Element targetBanksElement = firstDirectChild(targetStationElement, "Banks");
        if (targetBanksElement == null) {
            targetBanksElement = document.createElement("Banks");
            targetStationElement.insertBefore(targetBanksElement, firstDirectChild(targetStationElement, "SampleList"));
        }

        Set<String> bankNames = new LinkedHashSet<>();
        for (Element bankElement : directChildren(targetBanksElement, "Bank")) {
            bankNames.add(bankElement.getAttribute("Name"));
        }

        for (Element sourceStationElement : sourceStationElements) {
            Element sourceBanksElement = firstDirectChild(sourceStationElement, "Banks");
            if (sourceBanksElement == null) {
                continue;
            }

            for (Element sourceBankElement : directChildren(sourceBanksElement, "Bank")) {
                String bankName = sourceBankElement.getAttribute("Name");
                if (bankNames.add(bankName)) {
                    targetBanksElement.appendChild(document.createTextNode("\n        "));
                    targetBanksElement.appendChild(document.importNode(sourceBankElement, true));
                }
            }
        }

        targetBanksElement.appendChild(document.createTextNode("\n      "));
    }

    private void replaceSampleList(
            Document document,
            Element targetStationElement,
            List<Element> sourceStationElements,
            Map<Integer, IRadioStation> sourceStationsByNumber,
            IRadioStation.SampleListType sampleListType
    ) throws IOException {
        Element targetSampleListElement = findSampleListElement(targetStationElement, sampleListType);
        clearChildren(targetSampleListElement);

        Set<String> soundNames = new LinkedHashSet<>();
        for (Element sourceStationElement : sourceStationElements) {
            Element sourceSampleListElement = findSampleListElement(sourceStationElement, sampleListType);
            Set<String> selectedSoundNames = selectedSoundNames(
                    sourceStationElement,
                    sourceStationsByNumber,
                    sampleListType
            );

            for (Element sampleElement : directChildren(sourceSampleListElement, "Sample")) {
                String soundName = sampleElement.getAttribute("SoundName");
                if (selectedSoundNames.contains(soundName) && soundNames.add(soundName)) {
                    targetSampleListElement.appendChild(document.createTextNode("\n        "));
                    targetSampleListElement.appendChild(document.importNode(sampleElement, true));
                }
            }
        }

        if (!soundNames.isEmpty()) {
            targetSampleListElement.appendChild(document.createTextNode("\n      "));
        }
    }

    private void replacePlayList(
            Document document,
            Element targetStationElement,
            List<Element> sourceStationElements,
            Map<Integer, IRadioStation> sourceStationsByNumber,
            IRadioStation.PlayListType playListType
    ) throws IOException {
        Element targetPlayListElement = findPlayListElement(targetStationElement, playListType);
        clearChildren(targetPlayListElement);

        Set<String> entryNames = new LinkedHashSet<>();
        for (Element sourceStationElement : sourceStationElements) {
            Element sourcePlayListElement = findPlayListElement(sourceStationElement, playListType);
            Set<String> selectedEntryNames = selectedEntryNames(
                    sourceStationElement,
                    sourceStationsByNumber,
                    playListType
            );

            for (Element entryElement : directChildren(sourcePlayListElement, "Entry")) {
                String entryName = entryElement.getAttribute("Name");
                if (selectedEntryNames.contains(entryName) && entryNames.add(entryName)) {
                    targetPlayListElement.appendChild(document.createTextNode("\n        "));
                    targetPlayListElement.appendChild(document.importNode(entryElement, true));
                }
            }
        }

        if (!entryNames.isEmpty()) {
            targetPlayListElement.appendChild(document.createTextNode("\n      "));
        }
    }

    private Element findSampleListElement(
            Element radioStationElement,
            IRadioStation.SampleListType sampleListType
    ) throws IOException {
        for (Element sampleListElement : directChildren(radioStationElement, "SampleList")) {
            if (sampleListElement.getAttribute("Type").equals(sampleListType.xmlValue())) {
                return sampleListElement;
            }
        }

        throw new IOException(
                radioStationElement.getAttribute("Name")
                        + " does not contain a "
                        + sampleListType.xmlValue()
                        + " sample list."
        );
    }

    private Element findPlayListElement(
            Element radioStationElement,
            IRadioStation.PlayListType playListType
    ) throws IOException {
        for (Element playListElement : directChildren(radioStationElement, "PlayList")) {
            if (playListElement.getAttribute("Type").equals(playListType.xmlValue())) {
                return playListElement;
            }
        }

        throw new IOException(
                radioStationElement.getAttribute("Name")
                        + " does not contain a "
                        + playListType.xmlValue()
                        + " playlist."
        );
    }

    private static Map<Integer, IRadioStation> sourceStationsByNumber(List<IRadioStation> sourceStations) {
        Map<Integer, IRadioStation> sourceStationsByNumber = new HashMap<>();

        for (IRadioStation sourceStation : sourceStations) {
            sourceStationsByNumber.put(sourceStation.number(), sourceStation);
        }

        return sourceStationsByNumber;
    }

    private static Set<String> selectedSoundNames(
            Element sourceStationElement,
            Map<Integer, IRadioStation> sourceStationsByNumber,
            IRadioStation.SampleListType sampleListType
    ) {
        IRadioStation sourceStation = sourceStation(sourceStationElement, sourceStationsByNumber);
        Set<String> selectedSoundNames = new LinkedHashSet<>();

        for (IRadioStation.SampleList sampleList : sourceStation.sampleLists()) {
            if (sampleList.type() == sampleListType) {
                for (IRadioStation.Sample sample : sampleList.samples()) {
                    selectedSoundNames.add(sample.soundName());
                }
            }
        }

        return selectedSoundNames;
    }

    private static Set<String> selectedEntryNames(
            Element sourceStationElement,
            Map<Integer, IRadioStation> sourceStationsByNumber,
            IRadioStation.PlayListType playListType
    ) {
        IRadioStation sourceStation = sourceStation(sourceStationElement, sourceStationsByNumber);
        Set<String> selectedEntryNames = new LinkedHashSet<>();

        for (IRadioStation.PlayList playList : sourceStation.playLists()) {
            if (playList.type() == playListType) {
                for (IRadioStation.Entry entry : playList.entries()) {
                    selectedEntryNames.add(entry.name());
                }
            }
        }

        return selectedEntryNames;
    }

    private static IRadioStation sourceStation(
            Element sourceStationElement,
            Map<Integer, IRadioStation> sourceStationsByNumber
    ) {
        int stationNumber = Integer.parseInt(sourceStationElement.getAttribute("Number"));
        IRadioStation sourceStation = sourceStationsByNumber.get(stationNumber);
        if (sourceStation == null) {
            throw new IllegalStateException("No selected station data found for station number " + stationNumber + ".");
        }

        return sourceStation;
    }

    private void writeDocument(Document document, Path radioInfoPath) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(radioInfoPath)) {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            transformer.transform(new DOMSource(document), new StreamResult(outputStream));
        } catch (TransformerException exception) {
            throw new IOException("Could not write " + radioInfoPath + ".", exception);
        }
    }

    private static Path backupPath(Path radioInfoPath) {
        String fileName = radioInfoPath.getFileName().toString();
        int extensionStart = fileName.lastIndexOf('.');

        if (extensionStart == -1) {
            return radioInfoPath.resolveSibling(fileName + "_backup");
        }

        String name = fileName.substring(0, extensionStart);
        String extension = fileName.substring(extensionStart);
        return radioInfoPath.resolveSibling(name + "_backup" + extension);
    }

    private static void clearChildren(Element element) {
        while (element.hasChildNodes()) {
            element.removeChild(element.getFirstChild());
        }
    }

    private static Element firstDirectChild(Element parent, String tagName) {
        for (Element child : directChildren(parent, tagName)) {
            return child;
        }

        return null;
    }

    private static List<Element> directChildren(Element parent, String tagName) {
        List<Element> children = new ArrayList<>();
        NodeList childNodes = parent.getChildNodes();

        for (int index = 0; index < childNodes.getLength(); index++) {
            Node childNode = childNodes.item(index);
            if (childNode instanceof Element childElement && childElement.getTagName().equals(tagName)) {
                children.add(childElement);
            }
        }

        return children;
    }
}

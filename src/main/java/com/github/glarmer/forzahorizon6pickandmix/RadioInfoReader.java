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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RadioInfoReader {
    private static final String RADIO_INFO_RESOURCE = "media/Audio/RadioInfo_EN.xml";

    public List<IRadioStation> readDefaultRadioStations() throws IOException {
        if (Files.exists(RadioInfoEditor.DEFAULT_RADIO_INFO_PATH)) {
            return readRadioStations(RadioInfoEditor.DEFAULT_RADIO_INFO_PATH);
        }

        InputStream inputStream = RadioInfoReader.class
                .getClassLoader()
                .getResourceAsStream(RADIO_INFO_RESOURCE);

        if (inputStream == null) {
            throw new IOException("Could not find " + RADIO_INFO_RESOURCE + " on the classpath.");
        }

        try (inputStream) {
            return readRadioStations(inputStream);
        }
    }

    public List<IRadioStation> readRadioStations(Path radioInfoPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(radioInfoPath)) {
            return readRadioStations(inputStream);
        }
    }

    public List<IRadioStation> readRadioStations(InputStream inputStream) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            Element radioStationsElement = firstDirectChild(document.getDocumentElement(), "RadioStations");

            if (radioStationsElement == null) {
                throw new IOException("RadioInfo XML does not contain a RadioStations element.");
            }

            List<IRadioStation> radioStations = new ArrayList<>();
            for (Element radioStationElement : directChildren(radioStationsElement, "RadioStation")) {
                radioStations.add(readRadioStation(radioStationElement));
            }

            return List.copyOf(radioStations);
        } catch (ParserConfigurationException | SAXException exception) {
            throw new IOException("Could not parse RadioInfo XML.", exception);
        }
    }

    private IRadioStation readRadioStation(Element element) {
        return new IRadioStation.RadioStation(
                element.getAttribute("Name"),
                intAttribute(element, "Number"),
                intAttribute(element, "DJCharID"),
                new IRadioStation.MediaTrackRestrictions(),
                readBanks(firstDirectChild(element, "Banks")),
                readSampleLists(element),
                readPlayLists(element)
        );
    }

    private List<IRadioStation.Bank> readBanks(Element banksElement) {
        if (banksElement == null) {
            return List.of();
        }

        List<IRadioStation.Bank> banks = new ArrayList<>();
        for (Element bankElement : directChildren(banksElement, "Bank")) {
            banks.add(new IRadioStation.Bank(bankElement.getAttribute("Name")));
        }

        return banks;
    }

    private List<IRadioStation.SampleList> readSampleLists(Element radioStationElement) {
        List<IRadioStation.SampleList> sampleLists = new ArrayList<>();
        for (Element sampleListElement : directChildren(radioStationElement, "SampleList")) {
            sampleLists.add(new IRadioStation.SampleList(
                    IRadioStation.SampleListType.fromXmlValue(sampleListElement.getAttribute("Type")),
                    sampleListElement.getAttribute("Event"),
                    readSamples(sampleListElement)
            ));
        }

        return sampleLists;
    }

    private List<IRadioStation.Sample> readSamples(Element sampleListElement) {
        List<IRadioStation.Sample> samples = new ArrayList<>();
        for (Element sampleElement : directChildren(sampleListElement, "Sample")) {
            samples.add(new IRadioStation.Sample(
                    sampleElement.getAttribute("SoundName"),
                    longAttribute(sampleElement, "SampleLength"),
                    intAttribute(sampleElement, "SampleRate"),
                    nullableAttribute(sampleElement, "DisplayName"),
                    nullableAttribute(sampleElement, "Artist"),
                    nullableBooleanAttribute(sampleElement, "IsXCloudModeSafe"),
                    nullableAttribute(sampleElement, "GameEvent"),
                    readMarkers(sampleElement),
                    readLoops(sampleElement),
                    readBpms(sampleElement)
            ));
        }

        return samples;
    }

    private List<IRadioStation.Marker> readMarkers(Element sampleElement) {
        List<IRadioStation.Marker> markers = new ArrayList<>();
        for (Element markerElement : directChildren(sampleElement, "Marker")) {
            markers.add(new IRadioStation.Marker(
                    markerElement.getAttribute("Name"),
                    longAttribute(markerElement, "Position")
            ));
        }

        return markers;
    }

    private List<IRadioStation.Loop> readLoops(Element sampleElement) {
        List<IRadioStation.Loop> loops = new ArrayList<>();
        for (Element loopElement : directChildren(sampleElement, "Loop")) {
            loops.add(new IRadioStation.Loop(
                    loopElement.getAttribute("Name"),
                    loopElement.getAttribute("StartMarker"),
                    loopElement.getAttribute("EndMarker")
            ));
        }

        return loops;
    }

    private List<IRadioStation.Bpm> readBpms(Element sampleElement) {
        List<IRadioStation.Bpm> bpms = new ArrayList<>();
        for (Element bpmElement : directChildren(sampleElement, "BPM")) {
            bpms.add(new IRadioStation.Bpm(
                    doubleAttribute(bpmElement, "Value"),
                    longAttribute(bpmElement, "Start")
            ));
        }

        return bpms;
    }

    private List<IRadioStation.PlayList> readPlayLists(Element radioStationElement) {
        List<IRadioStation.PlayList> playLists = new ArrayList<>();
        for (Element playListElement : directChildren(radioStationElement, "PlayList")) {
            playLists.add(new IRadioStation.PlayList(
                    IRadioStation.PlayListType.fromXmlValue(playListElement.getAttribute("Type")),
                    readEntries(playListElement)
            ));
        }

        return playLists;
    }

    private List<IRadioStation.Entry> readEntries(Element playListElement) {
        List<IRadioStation.Entry> entries = new ArrayList<>();
        for (Element entryElement : directChildren(playListElement, "Entry")) {
            entries.add(new IRadioStation.Entry(entryElement.getAttribute("Name")));
        }

        return entries;
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

    private static String nullableAttribute(Element element, String attributeName) {
        if (element.hasAttribute(attributeName)) {
            return element.getAttribute(attributeName);
        }

        return null;
    }

    private static Boolean nullableBooleanAttribute(Element element, String attributeName) {
        String value = nullableAttribute(element, attributeName);
        if (value == null) {
            return null;
        }

        return Boolean.parseBoolean(value);
    }

    private static int intAttribute(Element element, String attributeName) {
        return Integer.parseInt(element.getAttribute(attributeName));
    }

    private static long longAttribute(Element element, String attributeName) {
        return Long.parseLong(element.getAttribute(attributeName));
    }

    private static double doubleAttribute(Element element, String attributeName) {
        return Double.parseDouble(element.getAttribute(attributeName));
    }
}

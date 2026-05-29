package com.github.glarmer.forzahorizon6pickandmix;

import java.util.List;

public interface IRadioStation {
    String name();

    int number();

    int djCharId();

    MediaTrackRestrictions mediaTrackRestrictions();

    List<Bank> banks();

    List<SampleList> sampleLists();

    List<PlayList> playLists();

    record RadioStation(
            String name,
            int number,
            int djCharId,
            MediaTrackRestrictions mediaTrackRestrictions,
            List<Bank> banks,
            List<SampleList> sampleLists,
            List<PlayList> playLists
    ) implements IRadioStation {
        public RadioStation {
            banks = List.copyOf(banks);
            sampleLists = List.copyOf(sampleLists);
            playLists = List.copyOf(playLists);
        }
    }

    record MediaTrackRestrictions() {
    }

    record Bank(String name) {
    }

    record SampleList(
            SampleListType type,
            String event,
            List<Sample> samples
    ) {
        public SampleList {
            samples = List.copyOf(samples);
        }
    }

    record Sample(
            String soundName,
            long sampleLength,
            int sampleRate,
            String displayName,
            String artist,
            Boolean xCloudModeSafe,
            String gameEvent,
            List<Marker> markers,
            List<Loop> loops,
            List<Bpm> bpms
    ) {
        public Sample {
            markers = List.copyOf(markers);
            loops = List.copyOf(loops);
            bpms = List.copyOf(bpms);
        }
    }

    record Marker(String name, long position) {
    }

    record Loop(String name, String startMarker, String endMarker) {
    }

    record Bpm(double value, long start) {
    }

    record PlayList(
            PlayListType type,
            List<Entry> entries
    ) {
        public PlayList {
            entries = List.copyOf(entries);
        }
    }

    record Entry(String name) {
    }

    enum SampleListType {
        TRACK("Track"),
        TRACK_LFE("TrackLFE"),
        DJ("DJ"),
        STINGER("Stinger"),
        STINGER_LFE("StingerLFE");

        private final String xmlValue;

        SampleListType(String xmlValue) {
            this.xmlValue = xmlValue;
        }

        public String xmlValue() {
            return xmlValue;
        }

        public static SampleListType fromXmlValue(String xmlValue) {
            for (SampleListType type : values()) {
                if (type.xmlValue.equals(xmlValue)) {
                    return type;
                }
            }

            throw new IllegalArgumentException("Unknown sample list type: " + xmlValue);
        }
    }

    enum PlayListType {
        FREE_ROAM("FreeRoam"),
        EVENT("Event"),
        SHORT_STINGER("ShortStinger");

        private final String xmlValue;

        PlayListType(String xmlValue) {
            this.xmlValue = xmlValue;
        }

        public String xmlValue() {
            return xmlValue;
        }

        public static PlayListType fromXmlValue(String xmlValue) {
            for (PlayListType type : values()) {
                if (type.xmlValue.equals(xmlValue)) {
                    return type;
                }
            }

            throw new IllegalArgumentException("Unknown playlist type: " + xmlValue);
        }
    }
}

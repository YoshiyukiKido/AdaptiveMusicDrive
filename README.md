# AdaptiveMusicDrive

AdaptiveMusicDrive is an Android application developed for research on adaptive music presentation during driving.

The application acquires the vehicle's current position and speed using smartphone GPS, obtains road speed-limit information from OpenStreetMap-based data, and controls music playback according to the experimental condition.

The application is primarily intended as a research prototype for evaluating the technical feasibility and potential effects of adaptive music presentation during driving.

## Features

- Vehicle speed acquisition using smartphone GPS
- Current-location display using MapLibre
- Map display using OpenFreeMap
- Road speed-limit acquisition using OpenStreetMap data
- Three experimental conditions:
  - No Music
  - Fixed-Tempo Music
  - Adaptive Music
- Automatic music playback using AndroidX Media3 / ExoPlayer
- CSV logging of driving and playback data
- Recording of music start, stop, and switching events
- GPS accuracy logging
- START / STOP control for driving experiments

## Experimental Conditions

The application supports three experimental conditions.

### 1. No Music

No music is played during the experiment.

### 2. Fixed-Tempo Music

Music with a fixed tempo is played throughout the experiment.

The current implementation uses:

```text
80 BPM
```

for the fixed-tempo condition.

### 3. Adaptive Music

The music tempo is automatically selected according to the difference between the road speed limit and the current vehicle speed.

Let

```text
Δv = speed limit - current vehicle speed
```

The current tempo-selection rules are:

| Driving condition | Tempo |
|---|---:|
| Δv >= 10 km/h | 120 BPM |
| 5 km/h <= Δv < 10 km/h | 80 BPM |
| Δv < 5 km/h | 60 BPM |

The 60, 80, and 120 BPM values are used as representative slow, medium, and fast tempo categories for experimental evaluation.

These values are not intended to represent universally optimal music tempos for driving.

## System Overview

The basic processing flow is:

```text
Smartphone GPS
      |
      v
Current Position / Vehicle Speed
      |
      +-----------------------+
      |                       |
      v                       v
OpenStreetMap            Vehicle Speed
Speed-Limit Data              |
      |                       |
      +-----------+-----------+
                  |
                  v
          Driving-State
          Classification
                  |
                  v
          Tempo Selection
                  |
                  v
           Music Playback
                  |
                  v
             CSV Logging
```

## Technologies

The application is developed using:

- Kotlin
- Jetpack Compose
- Google Play Services Location
- MapLibre Android SDK
- OpenFreeMap
- OpenStreetMap
- AndroidX Media3 / ExoPlayer

## Map and Road Information

Map rendering is performed using MapLibre.

The current map style is obtained from OpenFreeMap:

```text
https://tiles.openfreemap.org/styles/liberty
```

Road information is based on OpenStreetMap data.

The application attempts to obtain the `maxspeed` attribute of the road near the current GPS position.

When explicit speed-limit information cannot be obtained, a predefined fallback value may be used.

The current fallback value is:

```text
45 km/h
```

Road matching and speed-limit acquisition are experimental components and may be improved in future versions.

## Music Files

Music files are not included in this repository.

For experimental use, MP3 files can be placed under the following directories:

```text
app/src/main/assets/
├── bpm60/
│   ├── song01.mp3
│   ├── song02.mp3
│   └── ...
├── bpm80/
│   ├── song01.mp3
│   ├── song02.mp3
│   └── ...
└── bpm120/
    ├── song01.mp3
    ├── song02.mp3
    └── ...
```

Each directory contains music assigned to the corresponding tempo category.

For controlled experiments, the set of music files should be fixed across participants in order to reduce differences caused by musical content.

Users are responsible for ensuring that they have appropriate rights or permissions to use the audio files placed in these directories.

## CSV Logging

A new CSV log file is created when an experiment is started.

The log records information such as:

```text
datetime
condition
latitude
longitude
gps_accuracy_m
speed_kmh
speed_limit_kmh
delta_v
bpm
music_file
music_event
road_name
speed_limit_source
```

An example record is:

```csv
datetime,condition,latitude,longitude,gps_accuracy_m,speed_kmh,speed_limit_kmh,delta_v,bpm,music_file,music_event,road_name,speed_limit_source
2026-11-03 19:20:01.100,ADAPTIVE,34.780000,135.500000,3.2,30.1,50.0,19.9,120,bpm120/song01.mp3,START,"Example Road",OSM
2026-11-03 19:20:02.100,ADAPTIVE,34.780100,135.500100,3.1,34.2,50.0,15.8,120,bpm120/song01.mp3,NONE,"Example Road",OSM
2026-11-03 19:20:03.100,ADAPTIVE,34.780200,135.500200,3.0,42.0,50.0,8.0,80,bpm80/song01.mp3,SWITCH,"Example Road",OSM
```

### Music Events

The `music_event` field indicates changes in music playback.

| Event | Description |
|---|---|
| `START` | Music playback starts |
| `SWITCH` | Music or tempo changes |
| `STOP` | Music playback stops |
| `NONE` | No playback change |

This information can be used to analyze switching frequency and vehicle behavior around music-switching points.

## CSV File Location

CSV files are currently stored in the application's external Documents directory.

A typical location is:

```text
/storage/emulated/0/Android/data/
jp.viscloud.adaptivemusicdrive/
files/Documents/
```

Example filename:

```text
driving_log_20261103_192001.csv
```

## Required Android Permissions

The application requires the following permissions:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

Location permissions are requested at runtime.

## Experimental Use

This application is intended for research involving driving behavior and adaptive music presentation.

For real-road experiments:

- Participants should possess a valid driver's license.
- Appropriately insured vehicles should be used.
- Experiments should not be conducted when a participant has consumed alcohol or is unwell, fatigued, or sleep-deprived.
- Experiments should be suspended when weather, road, or traffic conditions make safe driving difficult.
- Drivers should not operate smartphones or experimental equipment while driving.
- Researchers should not instruct participants to exceed speed limits or perform unusual acceleration or deceleration.
- Experiments should be terminated immediately in the event of an accident or other safety-related incident.
- Appropriate institutional ethics-review and informed-consent procedures should be completed before experiments involving human participants.

Safe driving and compliance with applicable traffic regulations must always take priority over the experiment.

## Privacy

The application may record:

- GPS coordinates
- Date and time
- Vehicle speed
- GPS accuracy
- Road speed limits
- Road information
- Music tempo
- Music playback events

A combination of location and time information may potentially identify an individual or their travel history.

Research data should therefore be stored and handled appropriately. Raw GPS trajectories and other potentially identifying information should not be publicly released without appropriate processing.

## Research Evaluation

The current system is intended to support controlled experiments using the following three conditions:

```text
No Music
Fixed-Tempo Music
Adaptive Music
```

Potential evaluation metrics include:

- Mean vehicle speed
- Speed variance
- Standard deviation of vehicle speed
- Mean deviation from the speed limit
- Speed-limit exceedance duration
- Percentage of time exceeding the speed limit
- Music switching frequency
- GPS accuracy
- Music-switching latency

The current implementation itself does not establish that adaptive music improves driving safety or speed compliance. Controlled evaluation with multiple participants is required to investigate behavioral effects.

## Build Environment

The project is developed using Android Studio.

A recent Android SDK and Java 17-compatible build environment are recommended.

Main dependencies include:

```text
Kotlin
Jetpack Compose
Google Play Services Location
MapLibre Android SDK
AndroidX Media3
```

## Repository Notes

Audio files, experimental data, signing keys, and local configuration files should normally not be committed to the repository.

Example `.gitignore` entries:

```gitignore
.gradle/
.idea/
build/
*/build/
local.properties

*.jks
*.keystore

app/src/main/assets/bpm60/*.mp3
app/src/main/assets/bpm80/*.mp3
app/src/main/assets/bpm120/*.mp3

*.csv
data/
logs/
```

If empty BPM directories need to be retained in Git, a `.gitkeep` file can be placed in each directory.

## Disclaimer

AdaptiveMusicDrive is a research prototype.

It is not a vehicle safety system and is not intended to replace traffic regulations, navigation systems, vehicle safety systems, or driver judgment.

The driver is responsible for complying with applicable traffic laws and for operating the vehicle safely.

## Author

Yoshiyuki Kido  

## License
This software is released under the MIT License.
See the `LICENSE` file for details.
Copyright (c) 2026 Yoshiyuki Kido

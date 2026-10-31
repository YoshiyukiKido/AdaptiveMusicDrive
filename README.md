# AdaptiveMusicDrive

AdaptiveMusicDrive is an Android application for research on adaptive music presentation during driving.

The application acquires the vehicle's current position and speed using smartphone GPS, obtains road speed-limit information from OpenStreetMap-based data, and changes the tempo of the played music according to the relationship between the current vehicle speed and the road speed limit.

The application is intended primarily for experimental and research use.

## Features

- Vehicle speed acquisition using smartphone GPS
- Current-location display using MapLibre
- Map display using OpenFreeMap
- Road speed-limit acquisition using OpenStreetMap data
- Adaptive music playback based on vehicle speed and road speed limit
- Three experimental conditions:
  - No Music
  - Fixed Tempo Music
  - Adaptive Music
- CSV logging of driving and music-playback data
- Recording of music switching events
- GPS accuracy logging
- START/STOP control for driving experiments

## Experimental Conditions

The application supports the following three experimental conditions.

### No Music

No music is played during the experiment.

### Fixed Tempo Music

Music with a fixed tempo is played throughout the experiment.

The current implementation uses:

```text
80 BPM
```

as the fixed-tempo condition.

### Adaptive Music

The music tempo is automatically selected according to the difference between the road speed limit and the current vehicle speed.

Let

```text
Δv = speed limit - current vehicle speed
```

The current tempo-selection rules are:

| Condition | Selected Tempo |
|---|---:|
| Δv >= 10 km/h | 120 BPM |
| 5 km/h <= Δv < 10 km/h | 80 BPM |
| Δv < 5 km/h | 60 BPM |

These tempo values are currently used as representative slow, medium, and fast tempo conditions for experimental evaluation.

## System Overview

The basic processing flow is:

```text
Smartphone GPS
      |
      v
Current Location / Vehicle Speed
      |
      +--------------------+
      |                    |
      v                    v
OpenStreetMap         Vehicle Speed
Speed Limit               |
      |                    |
      +---------+----------+
                |
                v
        Driving State
        Classification
                |
                v
        Tempo Selection
                |
                v
          Music Playback
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

The application attempts to obtain the `maxspeed` attribute of roads near the current GPS position.

If speed-limit information cannot be obtained, a predefined fallback speed limit may be used.

The current fallback value is:

```text
45 km/h
```

The road-matching and speed-limit acquisition methods are under development and may be improved in future versions.

## CSV Logging

A new CSV log file is created when an experiment is started.

The log contains information such as:

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
music_event
road_name
speed_limit_source
```

Example:

```csv
datetime,condition,latitude,longitude,gps_accuracy_m,speed_kmh,speed_limit_kmh,delta_v,bpm,music_event,road_name,speed_limit_source
2026-10-31 18:20:01.100,ADAPTIVE,34.780000,135.500000,3.2,30.1,50.0,19.9,120,START,"Example Road",OSM
2026-10-31 18:20:02.100,ADAPTIVE,34.780100,135.500100,3.1,34.2,50.0,15.8,120,NONE,"Example Road",OSM
2026-10-31 18:20:03.100,ADAPTIVE,34.780200,135.500200,3.0,42.0,50.0,8.0,80,SWITCH,"Example Road",OSM
```

### Music Events

The `music_event` field records changes in music playback.

| Event | Description |
|---|---|
| `START` | Music playback starts |
| `SWITCH` | Music tempo changes |
| `STOP` | Music playback stops |
| `NONE` | No change in music playback |

This information can be used to calculate music-switching frequency and analyze vehicle speed at each switching point.

## CSV File Location

CSV files are currently stored in the application-specific Documents directory.

Typical location:

```text
/storage/emulated/0/Android/data/
jp.viscloud.adaptivemusicdrive/
files/Documents/
```

Example filename:

```text
driving_log_20261031_182001.csv
```

## Music Files

Music files are not included in this repository.

Place the required audio files in:

```text
app/src/main/res/raw/
```

The current implementation expects files corresponding to:

```text
bpm60.mp3
bpm80.mp3
bpm120.mp3
```

Music files should only be used when appropriate copyright and licensing permissions have been obtained.

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
- Experiments should use appropriately insured vehicles.
- Experiments should not be conducted when a participant is under the influence of alcohol, is unwell, fatigued, or sleep-deprived.
- Experiments should be suspended under unsafe weather or traffic conditions.
- Drivers should not operate smartphones or experimental equipment while driving.
- Researchers should not instruct participants to exceed speed limits or perform unsafe driving maneuvers.
- Experiments should be terminated immediately in the event of an accident or other safety-related incident.
- Applicable institutional ethics-review procedures should be followed before conducting experiments involving human participants.

## Privacy

The application may record GPS coordinates, timestamps, vehicle speed, road speed limits, and music-playback information.

Because combinations of location and time information may potentially identify participants, experimental data should be handled appropriately.

Researchers should avoid publishing raw GPS trajectories or other information that could identify individual participants.

## Current Research Status

The current version is designed primarily to evaluate the technical feasibility of adaptive music playback during real-world driving.

The implementation itself does not establish that adaptive music improves driving safety, speed compliance, or driver behavior.

Controlled experiments using multiple participants and comparison conditions are required to evaluate behavioral effects.

Planned experimental conditions include:

```text
No Music
Fixed Tempo Music
Adaptive Music
```

Potential evaluation metrics include:

- Speed variance
- Standard deviation of vehicle speed
- Mean deviation from the speed limit
- Duration of speed-limit exceedance
- Percentage of time exceeding the speed limit
- Music switching frequency
- GPS accuracy
- System response and switching latency

## Build Environment

The project is developed using Android Studio.

Main development components include:

```text
Kotlin
Jetpack Compose
Google Play Services Location
MapLibre Android SDK
AndroidX Media3
```

A recent Android SDK and Java 17-compatible build environment are recommended.

## Repository Notes

The following files should not normally be committed to the repository:

```text
local.properties
*.jks
*.keystore
*.mp3
experimental CSV data
participant data
```

Example `.gitignore` entries:

```gitignore
.gradle/
.idea/
build/
*/build/
local.properties

*.jks
*.keystore

app/src/main/res/raw/*.mp3

*.csv
data/
logs/
```

## Disclaimer

This software is a research prototype.

It is not intended to replace traffic regulations, vehicle safety systems, navigation systems, or driver judgment.

Drivers must always comply with applicable traffic laws and prioritize safe driving.

## Author

Yoshiyuki Kido  
Faculty of Business Data Science  
Kansai University

## License

License information will be added in a future release.

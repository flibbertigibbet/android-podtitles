# Android Podtitles

Android podcast player for on-device transcription and translation.


## About

Currently, this app supports transcribing podcast audio episodes on device (without uploading any audio to servers) then using the generated machine transcription to display subtitles while listening to the episode.

A planned feature is to also support translating the transcript on the device, using [ML Kit](https://developers.google.com/ml-kit/language/translation/android).


## Demo

![podtitles_demo](https://user-images.githubusercontent.com/960264/181162815-33920833-a691-427b-b93a-d6062c22ac86.gif)



## Supported languages

The app supports transcription using any of the languages with `small` models listed [here](https://alphacephei.com/vosk/models).



## Libraries in use

 - [Vosk](https://alphacephei.com/vosk/) for on-device transcription
 - `ffmpeg` for converting audio files for transcription
 - `ExoPlayer` for media playback and subtitle display

Also, this app uses [Gpodder](https://gpodder.github.io/) for a podcast search API.

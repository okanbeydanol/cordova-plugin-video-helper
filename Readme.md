# [Cordova Video Helper](https://github.com/okanbeydanol/cordova-plugin-video-helper) [![Release](https://img.shields.io/npm/v/cordova-plugin-video-helper.svg?style=flat)](https://github.com/okanbeydanol/cordova-plugin-video-helper/releases)

This plugin provides a simple way to perform video editing tasks in Cordova apps:

* Transcoding
* Trimming
* Creating thumbnails
* Getting video info (width, height, orientation, duration, size, bitrate)

Supports Android and iOS.

## Plugin setup

Using this plugin requires [Cordova iOS](https://github.com/apache/cordova-ios) and [Cordova Android](https://github.com/apache/cordova-android).

1. `cordova plugin add cordova-plugin-video-helper`--save

## Usage

### JavaScript (Global Cordova)
After the device is ready, you can use the plugin via the global `cordova.plugins.VideoHelper` object:

```javascript
var VideoHelper = cordova.plugins.VideoHelper;

// Transcode a video
VideoHelper.transcodeVideo({
    fileUri: 'file-uri-here',
    outputFileName: 'output-name',
    width: 340,
    height: 640,
    videoBitrate: 5000000,
    duration: 60,
    type: 'ffmpeg'
}, success, error);

// Trim a video
VideoHelper.trim({
    fileUri: 'file-uri-here',
    trimStart: 0,
    outputFileName: 'output-name'
}, trimSuccess, trimFail);

// Create a thumbnail
VideoHelper.createThumbnail({
    fileUri: 'file-uri-here',
    outputFileName: 'output-name',
    atTime: 2,
    width: 320,
    height: 480,
    quality: 100
}, success, error);

// Get video info
VideoHelper.getVideoInfo('file-uri-here', getVideoInfoSuccess, getVideoInfoError);
```

### TypeScript / ES Module / Ionic
You can also use ES module imports (with TypeScript support):

```typescript
import { VideoHelper } from 'cordova-plugin-video-helper';

// Transcode a video
VideoHelper.transcodeVideo({
    fileUri: 'file-uri-here',
    outputFileName: 'output-name',
    width: 340,
    height: 640,
    videoBitrate: 5000000,
    duration: 60,
    type: 'ffmpeg'
}, (info) => {
    console.log('Transcode info:', info);
}, (error) => {
    console.error('Transcode error:', error);
});

// Trim a video
VideoHelper.trim({
    fileUri: 'file-uri-here',
    trimStart: 0,
    outputFileName: 'output-name'
}, (result) => {
    console.log('Trimmed video path:', result);
}, (error) => {
    console.error('Trim error:', error);
});

//Create a JPEG thumbnail from a video
VideoHelper.createThumbnail({
    fileUri: 'file-uri-here',
    outputFileName: 'output-name',
    atTime: 2,
    width: 320,
    height: 480,
    quality: 100
}, (path) => {
    console.log('Thumbnail path:', path);
}, (error) => {
    console.error('Thumbnail error:', error);
});
#### A note on width and height used by createThumbnail
The aspect ratio of the thumbnail created will match that of the video input. This means you may not get exactly the
width and height dimensions you give to `createThumbnail` for the jpeg. This for your convenience but let us know if it
is a problem.

//Get info on a video (width, height, orientation, duration, size, & bitrate)
VideoHelper.getVideoInfo('file-uri-here', (info) => {
    console.log('Video info:', info);
}, (error) => {
    console.error('Video info error:', error);
});
```

TypeScript Types
Type definitions are included. You get full autocompletion and type safety in TypeScript/Ionic projects.

* Check the [Typescript definitions](https://github.com/okanbeydanol/cordova-plugin-video-helper/tree/master/www/VideoHelper.d.ts) for additional configuration.


## On iOS

[iOS Developer AVFoundation Documentation](https://developer.apple.com/library/ios/documentation/AudioVideo/Conceptual/AVFoundationPG/Articles/01_UsingAssets.html#//apple_ref/doc/uid/TP40010188-CH7-SW8)

[Video compression in AVFoundation](http://www.iphonedevsdk.com/forum/iphone-sdk-development/110246-video-compression-avassetwriter-in-avfoundation.html)

[AVFoundation slides - tips/tricks](https://speakerdeck.com/bobmccune/composing-and-editing-media-with-av-foundation)

[AVFoundation slides #2](http://www.slideshare.net/bobmccune/learning-avfoundation)

[Bob McCune's AVFoundation Editor - ios app example](https://github.com/tapharmonic/AVFoundationEditor)

[Saving videos after recording videos](http://stackoverflow.com/questions/20902234/save-video-to-library-after-capturing-video-using-phonegap-capturevideo)

## On Android

[Android Documentation](http://developer.android.com/guide/appendix/media-formats.html#recommendations)

[Android Media Stores](http://developer.android.com/reference/android/provider/MediaStore.html#EXTRA_VIDEO_QUALITY)

[How to Port ffmpeg (the Program) to Android–Ideas and Thoughts](http://www.roman10.net/how-to-port-ffmpeg-the-program-to-androidideas-and-thoughts/)

[How to Build Android Applications Based on FFmpeg by An Example](http://www.roman10.net/how-to-build-android-applications-based-on-ffmpeg-by-an-example/)


## Communication

- If you **need help**, use [Stack Overflow](http://stackoverflow.com/questions/tagged/cordova). (Tag `cordova`)
- If you **found a bug** or **have a feature request**, open an issue.
- If you **want to contribute**, submit a pull request.

## Contributing

Patches welcome! Please submit all pull requests against the master branch. If your pull request contains JavaScript patches or features, include relevant unit tests. Thanks!

## Copyright and license

    The MIT License (MIT)

    Copyright (c) 2024 Okan Beydanol

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.

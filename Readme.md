[![npm version](https://badge.fury.io/js/cordova-plugin-video-editor.svg)](https://badge.fury.io/js/cordova-plugin-video-editor)

This is a cordova plugin to assist in several video editing tasks such as:

* Transcoding
* Trimming
* Creating thumbnails from a video file with specific time in the video
* Getting info on a video - width, height, orientation, duration, size, & bitrate.

This plugin will address those concerns, hopefully.

## Installation

```
cordova plugin add cordova-plugin-video-helper
```

`VideoHelper` will be available in the window after deviceready.

## Usage

### Transcode a video

```javascript
// parameters passed to transcodeVideo
window.VideoHelper.transcodeVideo(
    success, // success cb
    error, // error cb
    {
        fileUri: 'file-uri-here', // the path to the video on the device
        outputFileName: 'output-name', // the file name for the transcoded video
        width: 340, // optional, see note below on width and height
        height: 640, // optional, see notes below on width and height
        videoBitrate: 5000000, // optional, bitrate in bits, defaults to 5 megabit (5000000),
        duration: 60, // optinal for triming while compressing
        type: 'ffmpeg' // optinal default for ffmpeg. You can use MediaCoder for compress(Only android)
    }
);
```

#### transcodeVideo example -

```javascript
// this example uses the cordova media capture plugin
window.device.capture.captureVideo(
    videoCaptureSuccess,
    videoCaptureError,
    {
        limit: 1,
        duration: 20
    }
);

function videoCaptureSuccess(mediaFiles) {
    var outputFileName = (
        new Date().getTime()
    ).toString() + (
        outputFileName ? outputFileName : this.getFileNameFromURL(fileUrl)
    );
    const mediaSizeResult: {
        error: boolean,
        data: { bitrate: string, duration: string, height: string, orientation: string, size: number, width: string }
    } = this.getVideoInformation(fileUrl);

    const originalSize = Math.floor(+mediaSizeResult.data.size / 1024) / 1000 + 'MB';
    const originalBitrate = +mediaSizeResult.data.bitrate;
    const originalPath = fileUrl;
    let width: number = 360;
    let height: number = 640;
    if (maintainAspectRatio) {
        const aspectRatio: {
            width: number,
            height: number
        } = this.getAspectRatio(Math.floor(+mediaSizeResult.data.width), +mediaSizeResult.data.height);
        width = aspectRatio.width;
        height = aspectRatio.height;
    }

    window.VideoHelper.transcodeVideo(
        {
            fileUri,
            outputFileName,
            width,
            height,
            videoBitrate,
            duration,
            type
        },
        async (info: {
            progress: number,
            completed: boolean,
            error: boolean,
            data: string,
            message: string
        }): Promise<any> => {
            if (info.error) {
                return resolve({error: true, data: null, message: info.message});
            }
            if (info.completed) {
                const mediaSizeResult: {
                    error: boolean,
                    data: {
                        bitrate: string,
                        duration: string,
                        height: string,
                        orientation: string,
                        size: number,
                        width: string
                    }
                } = await this.getVideoInformation(info.data);
                return resolve({error: false, data: mediaSizeResult.data, message: null});
            } else {
                console.log('transCodeVideo - Progresss:', info);
            }
        },
        (error) => {
            console.error('Error transCodeVideo:', error);
            return resolve({error: true, data: null, message: error.message});
        }
    );
}

function getVideoInformation(videoPath: string) {
    return new Promise(async (resolve: <T>(value: {
        error: boolean,
        data: { bitrate: string, duration: string, height: string, orientation: string, size: number, width: string }
    }) => void): Promise<void> => {
        window.VideoHelper.getVideoInfo(
            videoPath,
            (info): any => {
                return resolve({error: false, data: info});
            },
            (error) => {
                console.error('Error fetching video info:', error);
                return resolve({error: true, data: null});
            }
        );
    });
}

function getAspectRatio(width: number, height: number, scaledWidth: number = 640, scaledHeight: number = 640) {
    const videoWidth = width > height ? height : width;
    const videoHeight = width > height ? width : height;
    const aspectRatio = videoWidth / videoHeight;

    const newWidth = scaledWidth && scaledHeight ? scaledHeight * aspectRatio : videoWidth;
    const newHeight = scaledWidth && scaledHeight ? newWidth / aspectRatio : videoHeight;

    return {width: newWidth, height: newHeight};
}

function getFileNameFromURL(url) {
    if (!url) {
        return (
            new Date().getTime()
        ).toString();
    }
    let fileName = url.substr(url.lastIndexOf('/') + 1);
    if (fileName.indexOf('?') != -1) {
        fileName = fileName.substr(0, fileName.indexOf('?'));
    }
    const split = fileName.split('.');
    if (split.length > 1) {
        fileName = split.slice(0, split.length - 1).join('_');
    } else if (split.length > 0) {
        fileName = split[0];
    }
    return fileName;
}
```

### Trim a Video

```javascript
VideoHelper.trim(
    {
        fileUri: 'file-uri-here', // path to input video
        duration: 15, // time to end trimming in seconds
        outputFileName: 'output-name', // output file name
    },
    trimSuccess,
    trimFail,
);

function trimSuccess(result) {
    // result is the path to the trimmed video on the device
    console.log('trimSuccess, result: ' + result);
}

function trimFail(err) {
    console.log('trimFail, err: ' + err);
}
```

### Create a JPEG thumbnail from a video

```javascript
VideoHelper.createThumbnail(
    {
        fileUri: 'file-uri-here', // the path to the video on the device
        outputFileName: 'output-name', // the file name for the JPEG image
        atTime: 2, // optional, location in the video to create the thumbnail (in seconds)
        width: 320, // optional, width of the thumbnail
        height: 480, // optional, height of the thumbnail
    },
    success, // success cb
    error, // error cb
);
// atTime will default to 0 if not provided
// width and height will be the same as the video input if they are not provided
// quality will default to 100 if not provided
```

#### A note on width and height used by createThumbnail

The aspect ratio of the thumbnail created will match that of the video input. This means you may not get exactly the
width and height dimensions you give to `createThumbnail` for the jpeg. This for your convenience but let us know if it
is a problem.

### Get info on a video (width, height, orientation, duration, size, & bitrate)

```javascript
VideoHelper.getVideoInfo(
    success, // success cb
    error, // error cb
    {
        fileUri: 'file-uri-here', // the path to the video on the device
    }
);
```

```javascript
VideoHelper.getVideoInfo(
    getVideoInfoSuccess,
    getVideoInfoError,
    {
        fileUri: file.fullPath
    }
);

function getVideoInfoSuccess(info) {
    console.log('getVideoInfoSuccess, info: ' + JSON.stringify(info, null, 2));
    {
        /*
        width: 1920,
        height:1080,
        orientation:'landscape', // will be portrait or landscape
        duration:3.541, // duration in seconds
        size:6830126, // size of the video in bytes
        bitrate:15429777 // bitrate of the video in bits per second,
        videoMediaType: 'video/3gpp' // Media type of the video, android example: 'video/3gpp', ios example: 'avc1',
        audioMediaType: 'audio/mp4a-latm' // Media type of the audio track in video, android example: 'audio/mp4a-latm', ios example: 'aac',
         */
    }
}
```

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

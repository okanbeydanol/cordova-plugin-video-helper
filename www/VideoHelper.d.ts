declare namespace CordovaPlugins {

    interface VideoHelperTranscodeProperties {
        fileUri: string;
        outputFileName: string;
        width?: number;
        height?: number;
        videoBitrate?: number;
        duration: number;
        type: string;
    }

    interface VideoHelperTrimProperties {
        fileUri: string;
        trimStart: number;
        outputFileName: string;
    }

    interface VideoHelperThumbnailProperties {
        fileUri: string;
        outputFileName: string;
        atTime?: number;
        width?: number;
        height?: number;
        quality?: number;
    }

    interface VideoHelperVideoInfoDetails {
        width: number;
        height: number;
        orientation: 'portrait' | 'landscape';
        duration: number;
        size: number;
        bitrate: number;
        videoMediaType: string;
        audioMediaType: string;
    }
    interface VideoHelper {
        transcodeVideo(
            options: VideoHelperTranscodeProperties,
            onSuccess: (info: {
                progress: number;
                completed: boolean;
                error: boolean;
                data: string;
                message: string;
            }) => Promise<void>,
            onError: (error: Error) => void
        ): void;

        trim(
            trimOptions: VideoHelperTrimProperties,
            onSuccess: (path: string) => void,
            onError: (error: Error) => void
        ): void;

        createThumbnail(
            options: VideoHelperThumbnailProperties,
            onSuccess: (path: string) => void,
            onError: (error: Error) => void
        ): void;

        getVideoInfo(
            path: string,
            onSuccess: (info: VideoHelperVideoInfoDetails) => void,
            onError: (error: Error) => void
        ): void;
    }
}
export interface VideoHelperTranscodeProperties extends CordovaPlugins.VideoHelperTranscodeProperties { }
export interface VideoHelperTrimProperties extends CordovaPlugins.VideoHelperTrimProperties { }
export interface VideoHelperThumbnailProperties extends CordovaPlugins.VideoHelperThumbnailProperties { }
export interface VideoHelperVideoInfoDetails extends CordovaPlugins.VideoHelperVideoInfoDetails { }

interface CordovaPlugins {
    VideoHelper: CordovaPlugins.VideoHelper;
}

interface Cordova {
    plugins: CordovaPlugins;
}

declare let cordova: Cordova;

export const VideoHelper: CordovaPlugins.VideoHelper;
export as namespace VideoHelper;
declare const _default: CordovaPlugins.VideoHelper;
export default _default;

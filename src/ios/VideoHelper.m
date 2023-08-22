
#import <Cordova/CDV.h>
#import "VideoHelper.h"
#import "SDAVAssetExportSession.h"

@interface VideoHelper ()

@end

@implementation VideoHelper

- (void) transcodeVideo:(CDVInvokedUrlCommand*)command
{
    NSDictionary* options = [command.arguments objectAtIndex:0];

    if ([options isKindOfClass:[NSNull class]]) {
        options = [NSDictionary dictionary];
    }

    NSString *inputFilePath = [options objectForKey:@"fileUri"];
    NSURL *inputFileURL = [self getURLFromFilePath:inputFilePath];
    NSString *videoFileName = [options objectForKey:@"outputFileName"];
    float videoDuration = [[options objectForKey:@"duration"] floatValue];
    float width = [[options objectForKey:@"width"] floatValue];
    float height = [[options objectForKey:@"height"] floatValue];
    int videoBitrate = ([options objectForKey:@"videoBitrate"]) ? [[options objectForKey:@"videoBitrate"] intValue] : 1000000; // default to 1 megabit
    int audioChannels = ([options objectForKey:@"audioChannels"]) ? [[options objectForKey:@"audioChannels"] intValue] : 2;
    int audioSampleRate = ([options objectForKey:@"audioSampleRate"]) ? [[options objectForKey:@"audioSampleRate"] intValue] : 44100;
    int audioBitrate = ([options objectForKey:@"audioBitrate"]) ? [[options objectForKey:@"audioBitrate"] intValue] : 128000; // default to 128 kilobits

    NSString *stringOutputFileType = AVFileTypeMPEG4;
    NSString *outputExtension = @".mp4";

    AVURLAsset *avAsset = [AVURLAsset URLAssetWithURL:inputFileURL options:nil];

    NSString *cacheDir = [NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES) objectAtIndex:0];
    NSString *outputPath = [NSString stringWithFormat:@"%@/%@%@", cacheDir, videoFileName, outputExtension];
    NSURL *outputURL = [NSURL fileURLWithPath:outputPath];

    SDAVAssetExportSession *encoder = [SDAVAssetExportSession.alloc initWithAsset:avAsset];
    encoder.outputFileType = stringOutputFileType;
    encoder.outputURL = outputURL;
    encoder.shouldOptimizeForNetworkUse = false;
    encoder.videoSettings = @
    {
        AVVideoCodecKey: AVVideoCodecTypeH264,
        AVVideoWidthKey: [NSNumber numberWithInt: width],
        AVVideoHeightKey: [NSNumber numberWithInt: height],
        AVVideoCompressionPropertiesKey: @
        {
            AVVideoAverageBitRateKey: [NSNumber numberWithInt: videoBitrate],
            AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel
        }
    };
    encoder.audioSettings = @
    {
        AVFormatIDKey: @(kAudioFormatMPEG4AAC),
        AVNumberOfChannelsKey: [NSNumber numberWithInt: audioChannels],
        AVSampleRateKey: [NSNumber numberWithInt: audioSampleRate],
        AVEncoderBitRateKey: [NSNumber numberWithInt: audioBitrate]
    };
    
     NSArray *videoTracks = [avAsset tracksWithMediaType:AVMediaTypeVideo];
     AVAssetTrack *videoTrack = [videoTracks objectAtIndex:0];

     if (videoDuration && videoDuration < (videoTrack.timeRange.duration.value / 600.0)) {
         int32_t preferredTimeScale = 600;
         CMTime startTime = CMTimeMakeWithSeconds(0, preferredTimeScale);
         CMTime stopTime = CMTimeMakeWithSeconds(videoDuration, preferredTimeScale);
         CMTimeRange exportTimeRange = CMTimeRangeFromTimeToTime(startTime, stopTime);
         encoder.timeRange = exportTimeRange;
     }
  
    dispatch_semaphore_t sessionWaitSemaphore = dispatch_semaphore_create(0);

    void (^completionHandler)(void) = ^(void)
    {
        dispatch_semaphore_signal(sessionWaitSemaphore);
    };

    [self.commandDelegate runInBackground:^{
        [encoder exportAsynchronouslyWithCompletionHandler:completionHandler];

        do {
            dispatch_time_t dispatchTime = DISPATCH_TIME_FOREVER;  // if we dont want progress, we will wait until it finishes.
            dispatchTime = getDispatchTimeFromSeconds((float)1.0);
            double progress = [encoder progress] * 100;

            NSMutableDictionary *dictionary = [[NSMutableDictionary alloc] init];
            [dictionary setValue: [NSNumber numberWithDouble: progress] forKey: @"progress"];
            [dictionary setValue: [NSNumber numberWithBool: NO] forKey: @"error"];
            [dictionary setValue: [NSNumber numberWithBool: NO] forKey: @"completed"];
            [dictionary setObject:@"" forKey:@"message"];
            
            CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary: dictionary];

            [result setKeepCallbackAsBool:YES];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
            dispatch_semaphore_wait(sessionWaitSemaphore, dispatchTime);
        } while( [encoder status] < AVAssetExportSessionStatusCompleted );

        if ([encoder status] == AVAssetExportSessionStatusCompleted) {
            NSMutableDictionary *dictionary = [[NSMutableDictionary alloc] init];
            double progress = 100.00;
            [dictionary setValue: [NSNumber numberWithDouble: progress] forKey: @"progress"];
            [dictionary setValue: [NSNumber numberWithBool: NO] forKey: @"error"];
            [dictionary setValue: [NSNumber numberWithBool: NO] forKey: @"completed"];
            [dictionary setObject:@"" forKey:@"message"];
            
            CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary: dictionary];

            [result setKeepCallbackAsBool:YES];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        }

        if (encoder.status == AVAssetExportSessionStatusCompleted)
        {
            NSMutableDictionary *dictionary = [[NSMutableDictionary alloc] init];
            double progress = 100.00;
            [dictionary setValue: [NSNumber numberWithDouble: progress] forKey: @"progress"];
            [dictionary setValue: [NSNumber numberWithBool: NO] forKey: @"error"];
            [dictionary setValue: [NSNumber numberWithBool: YES] forKey: @"completed"];
            [dictionary setObject:@"Completed" forKey:@"message"];
            [dictionary setObject:outputPath forKey:@"data"];
            CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary: dictionary];
            [result setKeepCallbackAsBool:NO];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        }
        else if (encoder.status == AVAssetExportSessionStatusCancelled)
        {
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Video export cancelled"] callbackId:command.callbackId];
        }
        else
        {
            NSString *error = [NSString stringWithFormat:@"Video export failed with error: %@ (%ld)", encoder.error.localizedDescription, (long)encoder.error.code];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:error] callbackId:command.callbackId];
        }
    }];
}

- (void) createThumbnail:(CDVInvokedUrlCommand*)command
{
    NSDictionary* options = [command.arguments objectAtIndex:0];

    if ([options isKindOfClass:[NSNull class]]) {
        options = [NSDictionary dictionary];
    }

    NSString* srcVideoPath = [options objectForKey:@"fileUri"];
    NSString* outputFileName = [options objectForKey:@"outputFileName"];
    float atTime = ([options objectForKey:@"atTime"]) ? [[options objectForKey:@"atTime"] floatValue] : 0;
    float width = [[options objectForKey:@"width"] floatValue];
    float height = [[options objectForKey:@"height"] floatValue];
    float quality = ([options objectForKey:@"quality"]) ? [[options objectForKey:@"quality"] floatValue] : 100;
    float thumbQuality = quality * 1.0 / 100;

    int32_t preferredTimeScale = 600;
    CMTime time = CMTimeMakeWithSeconds(atTime, preferredTimeScale);

    UIImage* thumbnail = [self generateThumbnailImage:srcVideoPath atTime:time];

    if (width && height) {
        CGSize newSize = CGSizeMake(width, height);
        thumbnail = [self scaleImage:thumbnail toSize:newSize];
    }

    NSString *cacheDir = [NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES) objectAtIndex:0];
    NSString *outputFilePath = [cacheDir stringByAppendingPathComponent:[NSString stringWithFormat:@"%@.%@", outputFileName, @"jpg"]];

    if ([UIImageJPEGRepresentation(thumbnail, thumbQuality) writeToFile:outputFilePath atomically:YES])
    {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:outputFilePath] callbackId:command.callbackId];
    }
    else
    {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"failed to create thumbnail file"] callbackId:command.callbackId];
    }
}

- (void) getVideoInfo:(CDVInvokedUrlCommand*)command
{
    NSString *filePath = [command.arguments objectAtIndex:0];
    NSURL *fileURL = [self getURLFromFilePath:filePath];

    unsigned long long size = [[NSFileManager defaultManager] attributesOfItemAtPath:[fileURL path] error:nil].fileSize;

    AVURLAsset *avAsset = [AVURLAsset URLAssetWithURL:fileURL options:nil];

    NSArray *videoTracks = [avAsset tracksWithMediaType:AVMediaTypeVideo];
    NSArray *audioTracks = [avAsset tracksWithMediaType:AVMediaTypeAudio];
    AVAssetTrack *videoTrack = [videoTracks objectAtIndex:0];
    AVAssetTrack *audioTrack = nil;
    if (audioTracks.count > 0) {
        audioTrack = [audioTracks objectAtIndex:0];
    }

    NSString *videoMediaType = nil;
    NSString *audioMediaType = nil;
    if (videoTrack.formatDescriptions.count > 0) {
        videoMediaType = getMediaTypeFromDescription(videoTrack.formatDescriptions[0]);
    }
    if (audioTrack != nil && audioTrack.formatDescriptions.count > 0) {
        audioMediaType = getMediaTypeFromDescription(audioTrack.formatDescriptions[0]);
    }

    CGSize mediaSize = videoTrack.naturalSize;
    float videoWidth = mediaSize.width;
    float videoHeight = mediaSize.height;
    float aspectRatio = videoWidth / videoHeight;

    NSString *videoOrientation = [self getOrientationForTrack:avAsset];
    if ([videoOrientation isEqual: @"portrait"]) {
        if (videoWidth > videoHeight) {
            videoWidth = mediaSize.height;
            videoHeight = mediaSize.width;
            aspectRatio = videoWidth / videoHeight;
        }
    }

    NSMutableDictionary *dict = [[NSMutableDictionary alloc] init];
    [dict setObject:[NSNumber numberWithFloat:videoWidth] forKey:@"width"];
    [dict setObject:[NSNumber numberWithFloat:videoHeight] forKey:@"height"];
    [dict setValue:videoOrientation forKey:@"orientation"];
    [dict setValue:[NSNumber numberWithFloat:videoTrack.timeRange.duration.value / 600.0] forKey:@"duration"];
    [dict setObject:[NSNumber numberWithLongLong:size] forKey:@"size"];
    [dict setObject:[NSNumber numberWithFloat:videoTrack.estimatedDataRate] forKey:@"bitrate"];
    [dict setValue:videoMediaType forKey:@"videoMediaType"];
    [dict setValue:audioMediaType forKey:@"audioMediaType"];

    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:dict] callbackId:command.callbackId];
}

- (void) trim:(CDVInvokedUrlCommand*)command {

    NSDictionary* options = [command.arguments objectAtIndex:0];
    if ([options isKindOfClass:[NSNull class]]) {
        options = [NSDictionary dictionary];
    }
    NSString *inputFilePath = [options objectForKey:@"fileUri"];
    NSURL *inputFileURL = [self getURLFromFilePath:inputFilePath];
    float trimStart = [[options objectForKey:@"trimStart"] floatValue];
    float trimEnd = [[options objectForKey:@"trimEnd"] floatValue];
    NSString *outputName = [options objectForKey:@"outputFileName"];

    NSFileManager *fileMgr = [NSFileManager defaultManager];
    NSString *cacheDir = [NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES) objectAtIndex:0];

    NSString *videoDir = [cacheDir stringByAppendingPathComponent:@"mp4"];
    if ([fileMgr createDirectoryAtPath:videoDir withIntermediateDirectories:YES attributes:nil error: NULL] == NO){
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"failed to create video dir"] callbackId:command.callbackId];
        return;
    }
    NSString *videoOutput = [videoDir stringByAppendingPathComponent:[NSString stringWithFormat:@"%@.%@", outputName, @"mp4"]];

    [self.commandDelegate runInBackground:^{

        AVURLAsset *avAsset = [AVURLAsset URLAssetWithURL:inputFileURL options:nil];

        AVAssetExportSession *exportSession = [[AVAssetExportSession alloc]initWithAsset:avAsset presetName: AVAssetExportPresetHighestQuality];
        exportSession.outputURL = [NSURL fileURLWithPath:videoOutput];
        exportSession.outputFileType = AVFileTypeQuickTimeMovie;
        exportSession.shouldOptimizeForNetworkUse = YES;

        int32_t preferredTimeScale = 600;
        CMTime startTime = CMTimeMakeWithSeconds(trimStart, preferredTimeScale);
        CMTime stopTime = CMTimeMakeWithSeconds(trimEnd, preferredTimeScale);
        CMTimeRange exportTimeRange = CMTimeRangeFromTimeToTime(startTime, stopTime);
        exportSession.timeRange = exportTimeRange;

        // debug timings
        NSString *trimStart = (NSString *) CFBridgingRelease(CMTimeCopyDescription(NULL, startTime));
        NSString *trimEnd = (NSString *) CFBridgingRelease(CMTimeCopyDescription(NULL, stopTime));

        //  Set up a semaphore for the completion handler and progress timer
        dispatch_semaphore_t sessionWaitSemaphore = dispatch_semaphore_create(0);

        void (^completionHandler)(void) = ^(void)
        {
            dispatch_semaphore_signal(sessionWaitSemaphore);
        };

        // do it
        [exportSession exportAsynchronouslyWithCompletionHandler:completionHandler];

        do {
            dispatch_time_t dispatchTime = DISPATCH_TIME_FOREVER;  // if we dont want progress, we will wait until it finishes.
            dispatchTime = getDispatchTimeFromSeconds((float)1.0);
            double progress = [exportSession progress] * 100;

            NSMutableDictionary *dictionary = [[NSMutableDictionary alloc] init];
            [dictionary setValue: [NSNumber numberWithDouble: progress] forKey: @"progress"];
            [dictionary setValue: [NSNumber numberWithBool: NO] forKey: @"error"];
            [dictionary setValue: [NSNumber numberWithBool: NO] forKey: @"completed"];
            [dictionary setObject:@"" forKey:@"message"];

            CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary: dictionary];

            [result setKeepCallbackAsBool:YES];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
            dispatch_semaphore_wait(sessionWaitSemaphore, dispatchTime);
        } while( [exportSession status] < AVAssetExportSessionStatusCompleted );

        if ([exportSession status] == AVAssetExportSessionStatusCompleted) {
            NSMutableDictionary *dictionary = [[NSMutableDictionary alloc] init];
            double progress = 100.00;
            [dictionary setValue: [NSNumber numberWithDouble: progress] forKey: @"progress"];
            [dictionary setValue: [NSNumber numberWithBool: NO] forKey: @"error"];
            [dictionary setValue: [NSNumber numberWithBool: NO] forKey: @"completed"];
            [dictionary setObject:@"" forKey:@"message"];
            
            CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary: dictionary];

            [result setKeepCallbackAsBool:YES];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        }

        switch ([exportSession status]) {
            case AVAssetExportSessionStatusCompleted: {
                NSMutableDictionary *dictionary = [[NSMutableDictionary alloc] init];
                double progress = 100.00;
                [dictionary setValue: [NSNumber numberWithDouble: progress] forKey: @"progress"];
                [dictionary setValue: [NSNumber numberWithBool: NO] forKey: @"error"];
                [dictionary setValue: [NSNumber numberWithBool: YES] forKey: @"completed"];
                [dictionary setObject:@"Completed" forKey:@"message"];
                CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary: dictionary];
                [result setKeepCallbackAsBool:NO];
                [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
                break;
            }
            case AVAssetExportSessionStatusFailed:
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[[exportSession error] localizedDescription]] callbackId:command.callbackId];
                break;
            case AVAssetExportSessionStatusCancelled:
                break;
            default:
                break;
        }

    }];
}

- (UIImage *)generateThumbnailImage: (NSString *)srcVideoPath atTime:(CMTime)time
{
    NSURL *url = [NSURL fileURLWithPath:srcVideoPath];

    if ([srcVideoPath rangeOfString:@"://"].location == NSNotFound)
    {
        NSString *filePath = [@"file://localhost" stringByAppendingString:srcVideoPath];
        NSString *encodedFilePath = [filePath stringByAddingPercentEncodingWithAllowedCharacters:[NSCharacterSet URLFragmentAllowedCharacterSet]];
        url = [NSURL URLWithString:encodedFilePath];
    }
    else
    {
        NSString *encodedSrcVideoPath = [srcVideoPath stringByAddingPercentEncodingWithAllowedCharacters:[NSCharacterSet URLFragmentAllowedCharacterSet]];
        url = [NSURL URLWithString:encodedSrcVideoPath];
    }

    AVAsset *asset = [AVAsset assetWithURL:url];
    AVAssetImageGenerator *imageGenerator = [[AVAssetImageGenerator alloc] initWithAsset:asset];
    imageGenerator.requestedTimeToleranceAfter = kCMTimeZero;
    imageGenerator.requestedTimeToleranceBefore = kCMTimeZero;
    imageGenerator.appliesPreferredTrackTransform = YES;
    CGImageRef imageRef = [imageGenerator copyCGImageAtTime:time actualTime:NULL error:NULL];
    UIImage *thumbnail = [UIImage imageWithCGImage:imageRef];
    CGImageRelease(imageRef);

    return thumbnail;
}

- (UIImage*)scaleImage:(UIImage*)image
                toSize:(CGSize)newSize;
{
    float oldWidth = image.size.width;
    float scaleFactor = newSize.width / oldWidth;

    float newHeight = image.size.height * scaleFactor;
    float newWidth = oldWidth * scaleFactor;

    UIGraphicsBeginImageContext(CGSizeMake(newWidth, newHeight));
    [image drawInRect:CGRectMake(0, 0, newWidth, newHeight)];
    UIImage *newImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    return newImage;
}

- (NSString*)getOrientationForTrack:(AVAsset *)asset
{
    AVAssetTrack *videoTrack = [[asset tracksWithMediaType:AVMediaTypeVideo] objectAtIndex:0];
    CGSize size = [videoTrack naturalSize];
    CGAffineTransform txf = [videoTrack preferredTransform];

    if (size.width == txf.tx && size.height == txf.ty)
        return @"landscape";
    else if (txf.tx == 0 && txf.ty == 0)
        return @"landscape";
    else if (txf.tx == 0 && txf.ty == size.width)
        return @"portrait";
    else
        return @"portrait";
}

- (NSURL*)getURLFromFilePath:(NSString*)filePath
{
    if ([filePath containsString:@"assets-library://"]) {
        NSString *encodedFilePath = [filePath stringByAddingPercentEncodingWithAllowedCharacters:[NSCharacterSet URLFragmentAllowedCharacterSet]];
        return [NSURL URLWithString:encodedFilePath];
    } else if ([filePath containsString:@"file://"]) {
        NSString *encodedFilePath = [filePath stringByAddingPercentEncodingWithAllowedCharacters:[NSCharacterSet URLFragmentAllowedCharacterSet]];
        return [NSURL URLWithString:encodedFilePath];
    }

    NSString *encodedFilePath = [filePath stringByAddingPercentEncodingWithAllowedCharacters:[NSCharacterSet URLFragmentAllowedCharacterSet]];
    return [NSURL fileURLWithPath:encodedFilePath];
}


static NSString* getMediaTypeFromDescription(id description) {
    CMFormatDescriptionRef desc = (__bridge CMFormatDescriptionRef)description;
    FourCharCode code = CMFormatDescriptionGetMediaSubType(desc);

    NSString *result = [NSString stringWithFormat:@"%c%c%c%c",
                        (code >> 24) & 0xff,
                        (code >> 16) & 0xff,
                        (code >> 8) & 0xff,
                        code & 0xff];
    NSCharacterSet *characterSet = [NSCharacterSet whitespaceCharacterSet];
    return [result stringByTrimmingCharactersInSet:characterSet];
}

static dispatch_time_t getDispatchTimeFromSeconds(float seconds) {
    long long milliseconds = seconds * 1000.0;
    dispatch_time_t waitTime = dispatch_time( DISPATCH_TIME_NOW, 1000000LL * milliseconds );
    return waitTime;
}

@end

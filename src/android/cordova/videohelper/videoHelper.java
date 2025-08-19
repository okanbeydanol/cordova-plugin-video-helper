package com.okanbeydanol.videoHelper;

import org.apache.cordova.*;
import org.json.*;

import android.graphics.Bitmap;
import android.media.*;
import android.os.Environment;

import androidx.annotation.NonNull;

import java.io.*;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import net.ypresto.androidtranscoder.MediaTranscoder;
import net.ypresto.androidtranscoder.format.MediaFormatStrategy;
import net.ypresto.androidtranscoder.utils.MediaExtractorUtils;

public class VideoHelper extends CordovaPlugin {
    private static final long MICROS_PER_SECOND = 1_000_000L;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd/HH-mm-ss", Locale.ENGLISH);

    private interface ActionExecutor {
        void execute(JSONArray args, CallbackContext callbackContext) throws JSONException, IOException;
    }

    private final Map<String, ActionExecutor> actionMap;

    public VideoHelper() {
        actionMap = new HashMap<>();
        actionMap.put("transcodeVideo", this::transcodeVideo);
        actionMap.put("trimVideo", this::trimVideo);
        actionMap.put("createThumbnail", this::createThumbnail);
        actionMap.put("getVideoInfo", this::executeGetVideoInfo);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        ActionExecutor executor = actionMap.get(action);
        if (executor != null) {
            try {
                executor.execute(args, callbackContext);
            } catch (Exception e) {
                callbackContext.error("Failed to execute action " + action + ": " + e.getMessage());
            }
            return true;
        }
        return false;
    }

    private void executeGetVideoInfo(JSONArray args, CallbackContext callbackContext) throws JSONException {
        String videoPath = args.getString(0);
        getVideoInfo(videoPath, callbackContext);
    }

    private VideoOptions extractVideoOptions(JSONArray args) throws JSONException, UnsupportedEncodingException {
        JSONObject options = args.optJSONObject(0);
        String videoPath = preprocessVideoPath(options.getString("fileUri"));
        String outputFileName = options.optString("outputFileName", DATE_FORMAT.format(new Date()));
        int width = options.optInt("width", 0);
        int height = options.optInt("height", 0);
        int videoBitrate = options.optInt("videoBitrate");
        int audioBitrate = options.optInt("audioBitrate", CustomAndroidFormatStrategy.AUDIO_BITRATE_AS_IS);
        int audioChannels = options.optInt("audioChannels", CustomAndroidFormatStrategy.AUDIO_CHANNELS_AS_IS);
        int videoDuration = (int) (options.optLong("duration", 0));
        long atTimeOpt = options.optLong("atTime", 0);
        return new VideoOptions(videoPath, outputFileName, width, height, videoBitrate, audioBitrate, audioChannels, videoDuration, atTimeOpt, options);
    }

    private void transcodeVideo(JSONArray args, CallbackContext callbackContext) {
        try {
            VideoOptions options = extractVideoOptions(args);
            String outputExtension = ".mp4";
            File mediaStorageDir = getOrCreateMediaStorageDir();
            String outputPathForTrim = new File(mediaStorageDir, "trim-" + options.outputFileName + outputExtension).getAbsolutePath();
            String outputPathForCompress = new File(mediaStorageDir, options.outputFileName + outputExtension).getAbsolutePath();
            MediaFormatStrategy outFormatStrategy =
                new CustomAndroidFormatStrategy(options.videoBitrate, 30, options.width, options.height, options.audioBitrate, options.audioChannels);
            JSONObject videoInfo = getVideoInfoSynchronously(options.videoPath);

            long sourceDurationSec = Math.round(videoInfo.optDouble("duration", 0));
            long requested = options.videoDuration == 0 ? sourceDurationSec : options.videoDuration;
            boolean needTrim = requested > 0 && requested < sourceDurationSec;

            cordova.getThreadPool().execute(() -> {
                try {
                    MediaTranscoder.Listener listener = new MediaTranscoder.Listener() {
                        @Override public void onTranscodeProgress(double progress) {
                            sendJsonResponse(new TranscodeProgressJsonResponse(progress * 100), true, callbackContext);
                        }
                        @Override public void onTranscodeCompleted() {
                            File trimmedFile = new File(outputPathForTrim);
                            if (trimmedFile.exists()) trimmedFile.delete();
                            File outFile = new File(outputPathForCompress);
                            if (!outFile.exists()) {
                                sendJsonResponse(new TranscodeFailedJsonResponse(new Exception("Output missing after transcode")), false, callbackContext);
                                return;
                            }
                            sendJsonResponse(new TranscodeCompletedJsonResponse(outputPathForCompress), false, callbackContext);
                        }
                        @Override public void onTranscodeCanceled() {
                            sendJsonResponse(new TranscodeCanceledJsonResponse(), false, callbackContext);
                        }
                        @Override public void onTranscodeFailed(Exception exception) {
                            sendJsonResponse(new TranscodeFailedJsonResponse(exception), false, callbackContext);
                        }
                    };

                    String sourceForTranscode = options.videoPath;
                    if (needTrim) {
                        long endUs = requested * MICROS_PER_SECOND;
                        if (trimVideoSegment(options.videoPath, outputPathForTrim, 0, endUs, videoInfo.getInt("rotationDegrees"))) {
                            sourceForTranscode = outputPathForTrim;
                        }
                    }

                    MediaTranscoder.getInstance().transcodeVideo(
                        sourceForTranscode,
                        outputPathForCompress,
                        outFormatStrategy,
                        listener
                    );
                } catch (Throwable e) {
                    sendJsonResponse(new TranscodeFailedJsonResponse(new Exception(e.toString())), false, callbackContext);
                }
            });
        } catch (Exception e) {
            handleException(e, callbackContext);
        }
    }

    private boolean trimVideoSegment(String inputPath, String outputPath, long startUs, long endUs, int rotationDegrees) throws IOException {
        if (endUs <= startUs) return false;
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            extractor.setDataSource(inputPath);
            final int trackCount = extractor.getTrackCount();
            int[] indexMap = new int[trackCount];
            Arrays.fill(indexMap, -1);

            muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            boolean hasTrack = false;
            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && (mime.startsWith("video/") || mime.startsWith("audio/"))) {
                    extractor.selectTrack(i);
                    indexMap[i] = muxer.addTrack(format);
                    hasTrack = true;
                }
            }
            if (!hasTrack) return false;

            if (rotationDegrees != 0) {
                try { muxer.setOrientationHint(rotationDegrees); } catch (Throwable ignored) {}
            }
            muxer.start();

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int maxBufferSize = 256 * 1024;
            for (int i = 0; i < trackCount; i++) {
                if (indexMap[i] != -1) {
                    MediaFormat f = extractor.getTrackFormat(i);
                    if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxBufferSize = Math.max(maxBufferSize, f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                    }
                }
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

            while (true) {
                int trackIndex = extractor.getSampleTrackIndex();
                if (trackIndex < 0) break;
                if (indexMap[trackIndex] == -1) {
                    extractor.advance();
                    continue;
                }
                long sampleTimeUs = extractor.getSampleTime();
                if (sampleTimeUs < 0 || sampleTimeUs > endUs) break;

                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize <= 0) break;

                bufferInfo.offset = 0;
                bufferInfo.size = sampleSize;
                bufferInfo.presentationTimeUs = sampleTimeUs - startUs;
                int sampleFlags = extractor.getSampleFlags();
                bufferInfo.flags = ((sampleFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0)
                    ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                muxer.writeSampleData(indexMap[trackIndex], buffer, bufferInfo);
                extractor.advance();
            }
            return new File(outputPath).exists();
        } finally {
            try { extractor.release(); } catch (Throwable ignored) {}
            if (muxer != null) {
                try { muxer.stop(); } catch (Throwable ignored) {}
                try { muxer.release(); } catch (Throwable ignored) {}
            }
        }
    }

    private void getVideoInfo(String videoPath, CallbackContext callback) {
        cordova.getThreadPool().execute(() -> {
            MediaExtractor extractor = null;
            try {
                String newVideoPath = preprocessVideoPath(videoPath);
                try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {

                    retriever.setDataSource(newVideoPath);
                    float width = Float.parseFloat(Objects.requireNonNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)));
                    float height = Float.parseFloat(Objects.requireNonNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)));
                    double duration = Double.parseDouble(Objects.requireNonNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))) / 1000.0;
                    long bitrate = Long.parseLong(Objects.requireNonNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)));
                    long rotationDegrees = Long.parseLong(Objects.requireNonNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)));
                    String orientation = getOrientation(width, height, rotationDegrees);

                    extractor = new MediaExtractor();
                    extractor.setDataSource(newVideoPath);
                    MediaExtractorUtils.TrackResult trackResult = MediaExtractorUtils.getFirstVideoAndAudioTrack(extractor);

                    File videoFile = new File(newVideoPath);
                    JSONObject videoInfo = new JSONObject();
                    videoInfo.put("width", width);
                    videoInfo.put("height", height);
                    videoInfo.put("rotationDegrees", rotationDegrees);
                    videoInfo.put("duration", duration);
                    videoInfo.put("bitrate", bitrate);
                    videoInfo.put("orientation", orientation);
                    videoInfo.put("size", videoFile.length());
                    videoInfo.put("videoMediaType", trackResult.mVideoTrackMime);
                    videoInfo.put("audioMediaType", trackResult.mAudioTrackMime);

                    callback.success(videoInfo);
                }
            } catch (Exception e) {
                callback.error("Failed to retrieve video metadata: " + e.getMessage());
            } finally {
                if (extractor != null) try { extractor.release(); } catch (Throwable ignored) {}
            }
        });
    }

    private void trimVideo(JSONArray args, CallbackContext callbackContext) {
        try {
            VideoOptions options = extractVideoOptions(args);
            String outputExtension = ".mp4";
            File mediaStorageDir = getOrCreateMediaStorageDir();
            JSONObject videoInfo = getVideoInfoSynchronously(options.videoPath);
            String outputPath = new File(mediaStorageDir.getPath(), "trimmed-" + options.outputFileName + outputExtension).getAbsolutePath();
            long duration = options.videoDuration == 0 ? videoInfo.getLong("duration") : options.videoDuration;
            cordova.getThreadPool().execute(() -> {
                try {
                    boolean trimmed;
                    long startUs = 0L;
                    long endUs = duration * MICROS_PER_SECOND;
                    trimmed = trimVideoSegment(options.videoPath, outputPath, startUs, endUs, videoInfo.getInt("rotationDegrees"));
                    if (trimmed) {
                        callbackContext.success(outputPath);
                    } else {
                        callbackContext.error("Failed to trim video.");
                    }
                } catch (Exception e) {
                    handleException(e, callbackContext);
                }
            });
        } catch (Exception e) {
            handleException(e, callbackContext);
        }
    }

    private void createThumbnail(JSONArray args, CallbackContext callbackContext) {
        try {
            VideoOptions options = extractVideoOptions(args);
            String outputExtension = ".jpg";
            File mediaStorageDir = getOrCreateMediaStorageDir();
            String outputPath = new File(mediaStorageDir.getPath(), options.outputFileName + outputExtension).getAbsolutePath();
            final File outputFile = new File(outputPath);
            cordova.getThreadPool().execute(() -> {
                try (MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                     OutputStream outStream = new FileOutputStream(outputFile)) {

                    mmr.setDataSource(options.videoPath);
                    Bitmap bitmap = mmr.getFrameAtTime((options.atTimeOpt == 0) ? 0 : options.atTimeOpt * MICROS_PER_SECOND);
                    if (bitmap == null) {
                        callbackContext.error("Failed to capture frame.");
                        return;
                    }
                    if (options.width > 0 || options.height > 0) {
                        int videoWidth = bitmap.getWidth();
                        int videoHeight = bitmap.getHeight();
                        double aspectRatio = (double) videoWidth / (double) videoHeight;
                        int targetWidth = options.width > 0 ? options.width : (options.height > 0 ? (int)(options.height * aspectRatio) : videoWidth);
                        int targetHeight = options.height > 0 ? options.height : (int)(targetWidth / aspectRatio);
                        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false);
                        if (scaled != bitmap) bitmap.recycle();
                        bitmap = scaled;
                    }
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outStream);
                    bitmap.recycle();
                    callbackContext.success(outputPath);
                } catch (Exception e) {
                    callbackContext.error("Exception during creating thumbnail: " + e.getMessage());
                }
            });
        } catch (JSONException | IOException e) {
            handleException(e, callbackContext);
        }
    }

    private JSONObject getVideoInfoSynchronously(String videoPath) throws InterruptedException, IOException, JSONException {
        final CountDownLatch latch = new CountDownLatch(1);
        final JSONObject[] resultHolder = new JSONObject[1];
        final String[] errorHolder = new String[1];
        getVideoInfo(videoPath, new CallbackContext("videoInfo", webView) {
            @Override
            public void success(JSONObject result) {
                resultHolder[0] = result;
                latch.countDown();
            }
            @Override
            public void error(String errorMessage) {
                errorHolder[0] = errorMessage;
                latch.countDown();
            }
        });
        latch.await();
        if (errorHolder[0] != null) {
            throw new IOException("Error in getVideoInfo: " + errorHolder[0]);
        }
        return resultHolder[0];
    }

    @NonNull
    private static String getOrientation(float width, float height, long rotationDegrees) {
        boolean isPortrait = (width < height);
        boolean rotated = (rotationDegrees == 90L || rotationDegrees == 270L);
        return (isPortrait ^ rotated) ? "portrait" : "landscape";
    }

    private void handleException(Exception e, CallbackContext callbackContext) {
        callbackContext.error("Failed to " + "compress video" + ": " + e.getMessage());
    }

    private File getOrCreateMediaStorageDir() throws IOException {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
            "/Android/data/" + cordova.getActivity().getPackageName() + "/files/videos");
        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
            throw new IOException("Can't access or make videos directory");
        }
        return mediaStorageDir;
    }

    private String preprocessVideoPath(String videoPath) throws UnsupportedEncodingException {
        return removeFileProtocolPrefix(URLDecoder.decode(videoPath, "UTF-8"));
    }

    private String removeFileProtocolPrefix(String videoPath) {
        return videoPath.startsWith("file://") ? videoPath.substring(7) : videoPath;
    }

    private void sendJsonResponse(JsonResponseCreator creator, boolean keepCallback, CallbackContext callbackContext) {
        try {
            PluginResult result = new PluginResult(PluginResult.Status.OK, creator.createResponse());
            result.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(result);
        } catch (JSONException e) {
            callbackContext.error("Error creating JSON response.");
        }
    }

    public static class VideoOptions {
        String videoPath;
        String outputFileName;
        JSONObject extra;
        int width;
        int height;
        int videoBitrate;
        int audioBitrate;
        int audioChannels;
        int videoDuration;
        long atTimeOpt;

        VideoOptions(String videoPath, String outputFileName, int width, int height,
                     int videoBitrate, int audioBitrate, int audioChannels,
                     int videoDuration, long atTimeOpt, JSONObject extra) {
            this.videoPath = videoPath;
            this.outputFileName = outputFileName;
            this.extra = extra;
            this.width = width;
            this.height = height;
            this.videoBitrate = videoBitrate;
            this.audioBitrate = audioBitrate;
            this.audioChannels = audioChannels;
            this.videoDuration = videoDuration;
            this.atTimeOpt = atTimeOpt;
        }
    }
}

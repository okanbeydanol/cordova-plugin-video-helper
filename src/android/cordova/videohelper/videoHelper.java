package android.cordova.videohelper;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.media.MediaMetadataRetriever;
import android.os.Environment;

import com.arthenica.mobileffmpeg.FFmpeg;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import net.ypresto.androidtranscoder.MediaTranscoder;
import net.ypresto.androidtranscoder.format.MediaFormatStrategy;
import net.ypresto.androidtranscoder.utils.MediaExtractorUtils;

public class VideoHelper extends CordovaPlugin {
  private static final String TAG = "VideoHelper: ";

  private interface ActionExecutor {
    void execute(JSONArray args, CallbackContext callbackContext) throws JSONException, IOException;
  }

  private final Map < String, ActionExecutor > actionMap;

  public VideoHelper() {
    actionMap = new HashMap < > ();
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
    String outputFileName = options.optString("outputFileName", new SimpleDateFormat("yyyy-MM-dd/HH-mm-ss", Locale.ENGLISH).format(new Date()));
    int width = options.optInt("width");
    int height = options.optInt("height");
    int videoBitrate = options.optInt("videoBitrate");
    int audioBitrate = options.optInt("audioBitrate", CustomAndroidFormatStrategy.AUDIO_BITRATE_AS_IS);
    int audioChannels = options.optInt("audioChannels", CustomAndroidFormatStrategy.AUDIO_CHANNELS_AS_IS);
    int videoDuration = (int)(options.optLong("duration", 0));
    long atTimeOpt = options.optLong("atTime", 0);
    return new VideoOptions(videoPath, outputFileName, width, height, videoBitrate, audioBitrate, audioChannels, videoDuration, atTimeOpt, options);
  }
  
  private void transcodeVideo(JSONArray args, CallbackContext callbackContext) {
    JSONObject options = args.optJSONObject(0);
    String type = options.optString("type", "ffmpeg");
    if (type.equals("ffmpeg")){
      compressVideoWithFFMPEG(args, callbackContext);
    }else{
      compressVideoWithMediaCoder(args, callbackContext);
    }
  }

  private void compressVideoWithFFMPEG(JSONArray args, CallbackContext callbackContext) {
    try {
      VideoOptions options = extractVideoOptions(args);
      File mediaStorageDir = getOrCreateMediaStorageDir();
      JSONObject videoInfo = getVideoInfoSynchronously(options.videoPath);
      Dimension outputDim = calculateOutputDimensions(videoInfo, options.width, options.height);
      String outputExtension = ".mp4";
      String outputPath = new File(mediaStorageDir.getPath(), options.outputFileName + outputExtension).getAbsolutePath();
      int defaultDuration = videoInfo.getInt("duration");
      String[] cmd = FFMpegUtils.getCommandForExtension(options.videoPath, outputPath, options.videoDuration, defaultDuration, options.videoBitrate, outputDim.width, outputDim.height);
      FFmpegTranscoder.Listener listener = new FFmpegTranscoder.Listener() {
        @Override
        public void onTranscodeProgress(double progress) {
          sendJsonResponse(new TranscodeProgressJsonResponse(progress), true, callbackContext);
        }

        @Override
        public void onTranscodeCompleted() {
          sendJsonResponse(new TranscodeCompletedJsonResponse(outputPath), false, callbackContext);
        }

        @Override
        public void onTranscodeCanceled() {
          sendJsonResponse(new TranscodeCanceledJsonResponse(), false, callbackContext);
        }

        @Override
        public void onTranscodeFailed(Exception exception) {
          sendJsonResponse(new TranscodeFailedJsonResponse(exception), false, callbackContext);
        }
      };

      String cmdString = Arrays.stream(cmd)
        .map(arg -> {
          if (arg.contains(" ")) {
            return "\"" + arg + "\"";
          }
          return arg;
        })
        .collect(Collectors.joining(" "));

      FFmpegTranscoder.transcode(cmdString, defaultDuration, listener);
    } catch (JSONException | IOException | InterruptedException e) {
      sendJsonResponse(new TranscodeFailedJsonResponse(e), false, callbackContext);
    }
  }

  private void compressVideoWithMediaCoder(JSONArray args, CallbackContext callbackContext) {
    try {
      VideoOptions options = extractVideoOptions(args);
      String outputExtension = ".mp4";
      File mediaStorageDir = getOrCreateMediaStorageDir();
      String outputPathForTrim = new File(mediaStorageDir.getPath(), "trim-" + options.outputFileName + outputExtension).getAbsolutePath();
      String outputPathForCompress = new File(mediaStorageDir.getPath(), options.outputFileName + outputExtension).getAbsolutePath();
      MediaFormatStrategy outFormatStrategy = new CustomAndroidFormatStrategy(options.videoBitrate, 30, options.width, options.height, options.audioBitrate, options.audioChannels);
      JSONObject videoInfo = getVideoInfoSynchronously(options.videoPath);
      cordova.getThreadPool().execute(() -> {
        try {
          MediaTranscoder.Listener listener = new MediaTranscoder.Listener() {
            @Override
            public void onTranscodeProgress(double progress) {
              sendJsonResponse(new TranscodeProgressJsonResponse(progress * 100), true , callbackContext);
            }

            @Override
            public void onTranscodeCompleted() {
              File trimmedFile = new File(outputPathForTrim);
              if (trimmedFile.exists()) {
                boolean delete = trimmedFile.delete();
              }
              File outFile = new File(outputPathForCompress);
              if (!outFile.exists()) {
                sendJsonResponse(new TranscodeFailedJsonResponse(new Exception("an error occurred during transcoding")), false, callbackContext);
                return;
              }
              sendJsonResponse(new TranscodeCompletedJsonResponse(outputPathForCompress), false, callbackContext);
            }

            @Override
            public void onTranscodeCanceled() {
              sendJsonResponse(new TranscodeCanceledJsonResponse(), false, callbackContext);
            }

            @Override
            public void onTranscodeFailed(Exception exception) {
              sendJsonResponse(new TranscodeFailedJsonResponse(exception), false, callbackContext);
            }
          };
          int duration = options.videoDuration == 0 ? videoInfo.getInt("duration") : options.videoDuration;
          String[] cmd = FFMpegUtils.getTrimmingCommand(options.videoPath, 0, duration, outputPathForTrim);
          int result = FFmpeg.execute(cmd);
          MediaTranscoder.getInstance().transcodeVideo(
            result == 0 ? outputPathForTrim : options.videoPath,
            outputPathForCompress,
            outFormatStrategy,
            listener
          );
        } catch (Throwable e) {
          sendJsonResponse(new TranscodeFailedJsonResponse(new Exception(e.toString())), false, callbackContext);
        }
      });
    } catch (JSONException | IOException | InterruptedException e) {
      sendJsonResponse(new TranscodeFailedJsonResponse(e), false, callbackContext);
    }
  }

  private void getVideoInfo(String videoPath, CallbackContext callback) {
    cordova.getThreadPool().execute(() -> {
      MediaMetadataRetriever retriever = null;
      try {
        String newVideoPath = preprocessVideoPath(videoPath);
        retriever = new MediaMetadataRetriever();
        retriever.setDataSource(newVideoPath);
        float width = Float.parseFloat(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        float height = Float.parseFloat(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        double duration = Double.parseDouble(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) / 1000.0;
        Long bitrate = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)); // Returns in bps
        String orientation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION); // Returns 0, 90, 180, or 270
        if (width < height) {
          if (orientation.equals("0") || orientation.equals("180")) {
            orientation = "portrait";
          } else {
            orientation = "landscape";
          }
        } else {
          if (orientation.equals("0") || orientation.equals("180")) {
            orientation = "landscape";
          } else {
            orientation = "portrait";
          }
        }
        String videoMediaType;
        String audioMediaType;
        final MediaExtractor mExtractor = new MediaExtractor();
        mExtractor.setDataSource(newVideoPath);
        MediaExtractorUtils.TrackResult trackResult = MediaExtractorUtils.getFirstVideoAndAudioTrack(mExtractor);

        // get types
        videoMediaType = trackResult.mVideoTrackMime;
        audioMediaType = trackResult.mAudioTrackMime;

        // release resources
        mExtractor.release();

        File videoFile = new File(newVideoPath);
        JSONObject videoInfo = new JSONObject();
        videoInfo.put("width", width);
        videoInfo.put("height", height);
        videoInfo.put("duration", duration);
        videoInfo.put("bitrate", bitrate);
        videoInfo.put("orientation", orientation);
        videoInfo.put("size", videoFile.length());
        videoInfo.put("videoMediaType", videoMediaType);
        videoInfo.put("audioMediaType", audioMediaType);
        retriever.release();
        callback.success(videoInfo);
      } catch (Exception e) {
        callback.error("Failed to retrieve video metadata: " + e.getMessage());
      }
    });
  }

  private void createThumbnail(JSONArray args, CallbackContext callbackContext) {
    try {
      VideoOptions options = extractVideoOptions(args);
      String outputExtension = ".jpg";
      File mediaStorageDir = getOrCreateMediaStorageDir();
      String outputPath = new File(mediaStorageDir.getPath(), options.outputFileName + outputExtension).getAbsolutePath();
      final File outputFile = new File(outputPath);
      cordova.getThreadPool().execute(() -> {
        OutputStream outStream = null;
        try {
          MediaMetadataRetriever mmr = new MediaMetadataRetriever();
          mmr.setDataSource(options.videoPath);
          Bitmap bitmap = mmr.getFrameAtTime((options.atTimeOpt == 0) ? 0 : options.atTimeOpt * 1000000);
          if (options.width > 0 || options.height > 0) {
            int videoWidth = bitmap.getWidth();
            int videoHeight = bitmap.getHeight();
            double aspectRatio = (double) videoWidth / (double) videoHeight;

            int scaleWidth = Double.valueOf(options.height * aspectRatio).intValue();
            int scaleHeight = Double.valueOf(scaleWidth / aspectRatio).intValue();

            final Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, scaleWidth, scaleHeight, false);
            bitmap.recycle();
            bitmap = resizedBitmap;
          }
          outStream = new FileOutputStream(outputFile);
          bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outStream);
          callbackContext.success(outputPath);
        } catch (Exception e) {
          if (outStream != null) {
            try {
              outStream.close();
            } catch (IOException e1) {
              callbackContext.error("Exception during creating thumbnail: " + e1.toString());
            }
          }
          callbackContext.error("Exception during creating thumbnail: " + e.toString());
        }
      });
    } catch (JSONException | IOException e) {
      handleException(e, callbackContext);
    }
  }

  private void trimVideo(JSONArray args, CallbackContext callbackContext) {
    try {
      VideoOptions options = extractVideoOptions(args);
      String outputExtension = ".mp4";
      File mediaStorageDir = getOrCreateMediaStorageDir();
      JSONObject videoInfo = getVideoInfoSynchronously(options.videoPath);
      String outputPath = new File(mediaStorageDir.getPath(), "trimmed-" + options.outputFileName + outputExtension).getAbsolutePath();
      int duration = options.videoDuration == 0 ? videoInfo.getInt("duration") : options.videoDuration;
      String[] cmd = FFMpegUtils.getTrimmingCommand(options.videoPath, 0, duration, outputPath);
      cordova.getThreadPool().execute(() -> {
        try {
          int result = FFmpeg.execute(cmd);
          if (result == 0) {
            callbackContext.success(outputPath);
          } else {
            callbackContext.error("Failed to trim video. Error code: " + result);
          }
        } catch (Exception e) {
          callbackContext.error("Exception during FFmpeg execution for trim: " + e.getMessage());
        }
      });
    } catch (JSONException | IOException | InterruptedException e) {
      handleException(e, callbackContext);
    }
  }

  private JSONObject getVideoInfoSynchronously(String videoPath) throws InterruptedException, JSONException, IOException {
    final CountDownLatch latch = new CountDownLatch(1);
    final JSONObject[] resultHolder = new JSONObject[1];
    final String[] errorHolder = new String[1];
    getVideoInfo(videoPath, new CallbackContext("", webView) {
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
      throw new JSONException("Error in getVideoInfo: " + errorHolder[0]);
    }
    return resultHolder[0];
  }

  private Dimension calculateOutputDimensions(JSONObject videoInfo, int desiredWidth, int desiredHeight) throws JSONException {
    int inWidth = videoInfo.getInt("width");
    int inHeight = videoInfo.getInt("height");

    double aspectRatio;
    int outWidth, outHeight, outLonger, inLonger, inShorter;

    outLonger = Math.max(desiredWidth, desiredHeight);

    if (inWidth >= inHeight) {
      inLonger = inWidth;
      inShorter = inHeight;
    } else {
      inLonger = inHeight;
      inShorter = inWidth;
    }

    if (inLonger > outLonger && outLonger > 0) {
      aspectRatio = (double) inLonger / (double) inShorter;
      if (inWidth >= inHeight) {
        outWidth = outLonger;
        outHeight = (int)(outWidth / aspectRatio);
      } else {
        outHeight = outLonger;
        outWidth = (int)(outHeight * aspectRatio);
      }
    } else {
      outWidth = inWidth;
      outHeight = inHeight;
    }

    return new Dimension(outWidth, outHeight);
  }

  private File getOrCreateMediaStorageDir() throws IOException {
    File mediaStorageDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/" + cordova.getActivity().getPackageName() + "/files/videos");
    if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
      throw new IOException("Can't access or make videos directory");
    }
    return mediaStorageDir;
  }

  private String preprocessVideoPath(String videoPath) throws UnsupportedEncodingException {
    return removeFileProtocolPrefix(decodeURL(videoPath));
  }

  private String decodeURL(String videoPath) throws UnsupportedEncodingException {
    return URLDecoder.decode(videoPath, "UTF-8");
  }

  private String removeFileProtocolPrefix(String videoPath) {
    return videoPath.startsWith("file://") ? videoPath.substring(7) : videoPath;
  }

  private void handleException(Exception e, CallbackContext callbackContext) {
    callbackContext.error("Failed to " + "compress video with FFMPEG" + ": " + e.getMessage());
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

    VideoOptions(String videoPath, String outputFileName, int width, int height, int videoBitrate, int audioBitrate, int audioChannels, int videoDuration, long atTimeOpt, JSONObject extra) {
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

  public static class Dimension {
    int width;
    int height;

    Dimension(int width, int height) {
      this.width = width;
      this.height = height;
    }
  }
}

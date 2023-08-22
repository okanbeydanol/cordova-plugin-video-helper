package android.cordova.videohelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public class FFMpegUtils {

  // Declare video and audio codec maps
  private static final Map < String, String > videoCodecMap = new HashMap < > ();
  private static final Map < String, String > audioCodecMap = new HashMap < > ();

  // Function to get video codec based on file extension
  public static String getVideoCodecForExtension(String extension) {
    return videoCodecMap.getOrDefault(extension, "mpeg4");
  }

  // Function to get audio codec based on file extension
  public static String getAudioCodecForExtension(String extension) {
    return audioCodecMap.getOrDefault(extension, "libmp3lame");
  }

  // Function to retrieve all video codecs
  public static Map < String, String > getAllVideoCodecs() {
    return videoCodecMap;
  }

  // Function to retrieve all audio codecs
  public static Map < String, String > getAllAudioCodecs() {
    return audioCodecMap;
  }

  // Function to retrieve video information
  public static String[] getVideoInfoCommand(String videoPath) {
    return new String[] {
      "-i",
      videoPath
    };
  }

  // Function for video trimming without re-encoding
  public static String[] getTrimmingCommand(String videoPath, int startTime, int duration, String outputPathForTrim) {
    return new String[] {
      "-ss",
      String.valueOf(startTime),
      "-t",
      String.valueOf(duration),
      "-i",
      videoPath,
      "-c:a",
      "copy",
      "-c:v",
      "copy",
      outputPathForTrim
    };
  }

  // Function to get command based on output file extension
  public static String[] getCommandForExtension(String videoPath, String outputPath, int videoDuration, int defaultDuration, int videoBitrate, int outHeight, int outWidth) {
    String extension = outputPath.substring(outputPath.lastIndexOf(".") + 1);
    String videoCodec = getVideoCodecForExtension(extension);
    String audioCodec = getAudioCodecForExtension(extension);

    List < String > cmd = getBaseCommand(videoPath);
    setVideoDuration(cmd, videoDuration, defaultDuration);
    setAudioCodec(cmd, audioCodec);
    setVideoCodec(cmd, videoCodec);
    setLosslessEncoding(cmd, 28);
    setEncodingPreset(cmd);
    setVideoBitrate(cmd, videoBitrate);
    setVideoResolution(cmd, outHeight, outWidth);
    setFrameRate(cmd, 24);

    return getFinalCommand(cmd, outputPath);
  }

  // Function to get the basic ffmpeg command
  private static List < String > getBaseCommand(String videoPath) {
    List < String > cmd = new ArrayList < > ();
    cmd.add("-i");
    cmd.add(videoPath);
    return cmd;
  }

  // Function to set video duration or default
  public static void setVideoDuration(List < String > cmd, int videoDuration, int defaultDuration) {
    cmd.add("-t");
    cmd.add(String.valueOf(videoDuration == 0 ? defaultDuration : videoDuration));
  }

  // Function to set audio codec
  public static void setAudioCodec(List < String > cmd, String audioCodecType) {
    cmd.add("-c:a");
    cmd.add(audioCodecType);
  }

  // Function to set video codec
  public static void setVideoCodec(List < String > cmd, String codecType) {
    cmd.add("-c:v");
    cmd.add(codecType.toLowerCase(Locale.ENGLISH));
  }

  // Function to set lossless encoding
  public static void setLosslessEncoding(List < String > cmd, int LossRate) {
    cmd.add("-crf");
    cmd.add(String.valueOf(LossRate));
  }

  // Function to set encoding preset
  public static void setEncodingPreset(List < String > cmd) {
    cmd.add("-preset");
    cmd.add("superfast");
  }

  // Function to set video bitrate
  public static void setVideoBitrate(List < String > cmd, int videoBitrate) {
    cmd.add("-b:v");
    cmd.add(videoBitrate + "k");
  }

  // Function to set video resolution
  public static void setVideoResolution(List < String > cmd, int outHeight, int outWidth) {
    cmd.add("-s");
    cmd.add(outHeight + "x" + outWidth);
  }

  // Function to set frame rate
  public static void setFrameRate(List < String > cmd, int frameRate) {
    cmd.add("-r");
    cmd.add(String.valueOf(frameRate));
  }

  // Function to get the final ffmpeg command
  public static String[] getFinalCommand(List < String > cmd, String outputPath) {
    cmd.add(outputPath);
    return cmd.toArray(new String[0]);
  }

  public static void execCmd(String cmd, int totalDurationInSeconds, String outputPathForCompress, final OnEditorListener onEditorListener) {

    FFmpegTranscoder.Listener listener = new FFmpegTranscoder.Listener() {
      @Override
      public void onTranscodeProgress(double progress) {
        // Handle progress
        JSONObject jsonObj = new JSONObject();
        try {
          jsonObj.put("progress", progress);
          onEditorListener.onProgress(jsonObj);
        } catch (JSONException e) {
          e.printStackTrace();
        }
      }

      @Override
      public void onTranscodeCompleted() {
        File outFile = new File(outputPathForCompress);
        if (!outFile.exists()) {
          onEditorListener.onFailure("an error occurred during transcoding");
          return;
        }
        onEditorListener.onSuccess(outputPathForCompress);
      }

      @Override
      public void onTranscodeCanceled() {
        onEditorListener.onCancel();
      }

      @Override
      public void onTranscodeFailed(Exception exception) {
        onEditorListener.onFailure(exception.toString());
      }
    };

    FFmpegTranscoder.transcode(cmd, totalDurationInSeconds, listener);
  }
}

class TranscodeCompletedJsonResponse implements JsonResponseCreator {
  private final String outputPath;

  public TranscodeCompletedJsonResponse(String outputPath) {
    this.outputPath = outputPath;
  }

  @Override
  public JSONObject createResponse() throws JSONException {
    JSONObject completedJson = new JSONObject();
    completedJson.put("progress", 100);
    completedJson.put("completed", true);
    completedJson.put("error", false);
    completedJson.put("message", "Completed!");
    completedJson.put("data", outputPath);
    return completedJson;
  }
}

class TranscodeCanceledJsonResponse implements JsonResponseCreator {
  @Override
  public JSONObject createResponse() throws JSONException {
    JSONObject canceledJson = new JSONObject();
    canceledJson.put("progress", 0);
    canceledJson.put("completed", false);
    canceledJson.put("error", true);
    canceledJson.put("message", "Transcode canceled!");
    return canceledJson;
  }
}

class TranscodeFailedJsonResponse implements JsonResponseCreator {
  private final Exception exception;

  public TranscodeFailedJsonResponse(Exception exception) {
    this.exception = exception;
  }

  @Override
  public JSONObject createResponse() throws JSONException {
    JSONObject failedJson = new JSONObject();
    failedJson.put("progress", 0);
    failedJson.put("completed", false);
    failedJson.put("error", true);
    failedJson.put("message", exception.getMessage());
    return failedJson;
  }
}
class TranscodeProgressJsonResponse implements JsonResponseCreator {
  private final double progress;

  public TranscodeProgressJsonResponse(double progress) {
    this.progress = progress;
  }

  @Override
  public JSONObject createResponse() throws JSONException {
    JSONObject progressJson = new JSONObject();
    progressJson.put("progress", progress);
    progressJson.put("completed", false);
    progressJson.put("error", false);
    return progressJson;
  }
}

interface JsonResponseCreator {
  JSONObject createResponse() throws JSONException;
}

interface OnEditorListener {
  void onSuccess(String outputPath);
  void onFailure(String error);
  void onProgress(JSONObject progress);
  void onCancel();
}

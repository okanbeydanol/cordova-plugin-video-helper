package com.okanbeydanol.videoHelper;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.ExecuteCallback;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.arthenica.mobileffmpeg.LogCallback;
import com.arthenica.mobileffmpeg.LogMessage;
import org.json.JSONException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FFmpegTranscoder {

  public interface Listener {
    void onTranscodeProgress(double progress);
    void onTranscodeCompleted();
    void onTranscodeCanceled();
    void onTranscodeFailed(Exception exception) throws JSONException;
  }

  public static void transcode(String cmd, int totalDurationInSeconds, final Listener listener) {
    Config.enableLogCallback(new LogCallback() {
      @Override
      public void apply(final LogMessage message) {
        double progress = parseProgressFromLog(message.getText(), totalDurationInSeconds);
        if (progress >= 0) {
          listener.onTranscodeProgress(progress);
        }
      }
    });

    FFmpeg.executeAsync(cmd, new ExecuteCallback() {
      @Override
      public void apply(final long executionId, final int returnCode) {
        if (returnCode == 0) {
          listener.onTranscodeCompleted();
        } else if (returnCode == 255) {
          listener.onTranscodeCanceled();
        } else {
          try {
            listener.onTranscodeFailed(new Exception("FFmpeg returned with error code: " + returnCode));
          } catch (JSONException e) {
            e.printStackTrace();
          }
        }
      }
    });
  }

  private static double parseProgressFromLog(String logMessage, int totalDurationInSeconds) {
    Pattern pattern = Pattern.compile("time=([\\d\\w:]+)");
    Matcher matcher = pattern.matcher(logMessage);

    if (matcher.find()) {
      String[] parts = matcher.group(1).split(":");
      double hours = Double.parseDouble(parts[0]);
      double minutes = Double.parseDouble(parts[1]);
      double seconds = Double.parseDouble(parts[2]);
      double currentTimeInSeconds = hours * 3600 + minutes * 60 + seconds;

      return (currentTimeInSeconds / totalDurationInSeconds) * 100;
    }

    return -1;
  }
}
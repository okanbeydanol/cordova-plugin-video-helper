package com.okanbeydanol.videoHelper;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import net.ypresto.androidtranscoder.format.MediaFormatExtraConstants;
import net.ypresto.androidtranscoder.format.MediaFormatStrategy;
import java.util.Objects;

public class CustomAndroidFormatStrategy implements MediaFormatStrategy {
  public static final int AUDIO_BITRATE_AS_IS = -1;
  public static final int AUDIO_CHANNELS_AS_IS = -1;
  public static final int DEFAULT_VIDEO_BITRATE = 9000000;
  public static final int DEFAULT_FRAMERATE = 30;
  public static final int DEFAULT_WIDTH = 0;
  public static final int DEFAULT_HEIGHT = 0;
  public static final int DEFAULT_AUDIO_BITRATE = 128000;
  private static final String TAG = "CustomFormatStrategy";
  private final int mVideoBitrate;
  private final int mFrameRate;
  private final int width;
  private final int height;
  private final int mAudioBitrate;
  private final int mAudioChannels;

  public CustomAndroidFormatStrategy() {
    this.mVideoBitrate = DEFAULT_VIDEO_BITRATE;
    this.mFrameRate = DEFAULT_FRAMERATE;
    this.width = DEFAULT_WIDTH;
    this.height = DEFAULT_HEIGHT;
    this.mAudioBitrate = AUDIO_BITRATE_AS_IS;
    this.mAudioChannels = AUDIO_CHANNELS_AS_IS;
  }

  public CustomAndroidFormatStrategy
    (
      final int videoBitrate,
      final int frameRate,
      final int width,
      final int height,
      final int audioBitrate,
      final int audioChannels
    ) {
      this.mVideoBitrate = videoBitrate;
      this.mFrameRate = frameRate;
      this.width = width;
      this.height = height;
      this.mAudioBitrate = audioBitrate;
      this.mAudioChannels = audioChannels;
    }

  public MediaFormat createVideoOutputFormat(MediaFormat inputFormat) {
    int inWidth = inputFormat.getInteger(MediaFormat.KEY_WIDTH);
    int inHeight = inputFormat.getInteger(MediaFormat.KEY_HEIGHT);
    int inLonger, inShorter, outWidth, outHeight, outLonger;
    double aspectRatio;

    outLonger = Math.max(this.width, this.height);
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
        outHeight = Double.valueOf(outWidth / aspectRatio).intValue();

      } else {
        outHeight = outLonger;
        outWidth = Double.valueOf(outHeight / aspectRatio).intValue();
      }
    } else {
      outWidth = inWidth;
      outHeight = inHeight;
    }

    MediaFormat format = MediaFormat.createVideoFormat("video/avc", outWidth, outHeight);
    format.setInteger(MediaFormat.KEY_BIT_RATE, mVideoBitrate);
    format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3);
    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

    return format;
  }

  public MediaFormat createAudioOutputFormat(MediaFormat inputFormat) {
    boolean isAACAudioFormat = Objects.equals(inputFormat.getString(MediaFormat.KEY_MIME), MediaFormatExtraConstants.MIMETYPE_AUDIO_AAC);
    if (mAudioBitrate == AUDIO_BITRATE_AS_IS && mAudioChannels == AUDIO_CHANNELS_AS_IS && isAACAudioFormat) return null;

    int audioBitrate = mAudioBitrate;
    if (audioBitrate == AUDIO_BITRATE_AS_IS) {
      audioBitrate = DEFAULT_AUDIO_BITRATE;
    }

    int audioChannels = mAudioChannels;
    if (audioChannels == AUDIO_CHANNELS_AS_IS) {
      audioChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
    }

    final MediaFormat format = MediaFormat.createAudioFormat(MediaFormatExtraConstants.MIMETYPE_AUDIO_AAC,
      inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), audioChannels);
    format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
    format.setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate);
    return format;
  }
}
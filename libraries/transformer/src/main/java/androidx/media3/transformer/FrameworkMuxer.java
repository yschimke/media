/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.SDK_INT;
import static androidx.media3.common.util.Util.castNonNull;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.ParcelFileDescriptor;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.MediaFormatUtil;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/** Muxer implementation that uses a {@link MediaMuxer}. */
/* package */ final class FrameworkMuxer implements Muxer {

  // MediaMuxer supported sample formats are documented in MediaMuxer.addTrack(MediaFormat).
  private static final ImmutableMap<String, ImmutableList<String>>
      SUPPORTED_CONTAINER_TO_VIDEO_SAMPLE_MIME_TYPES =
          ImmutableMap.of(
              MimeTypes.VIDEO_MP4,
              Util.SDK_INT >= 24
                  ? ImmutableList.of(
                      MimeTypes.VIDEO_H263,
                      MimeTypes.VIDEO_H264,
                      MimeTypes.VIDEO_MP4V,
                      MimeTypes.VIDEO_H265)
                  : ImmutableList.of(
                      MimeTypes.VIDEO_H263, MimeTypes.VIDEO_H264, MimeTypes.VIDEO_MP4V),
              MimeTypes.VIDEO_WEBM,
              Util.SDK_INT >= 24
                  ? ImmutableList.of(MimeTypes.VIDEO_VP8, MimeTypes.VIDEO_VP9)
                  : ImmutableList.of(MimeTypes.VIDEO_VP8));

  private static final ImmutableMap<String, ImmutableList<String>>
      SUPPORTED_CONTAINER_TO_AUDIO_SAMPLE_MIME_TYPES =
          ImmutableMap.of(
              MimeTypes.VIDEO_MP4,
              ImmutableList.of(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_AMR_NB, MimeTypes.AUDIO_AMR_WB),
              MimeTypes.VIDEO_WEBM,
              ImmutableList.of(MimeTypes.AUDIO_VORBIS));

  public static final class Factory implements Muxer.Factory {
    @Override
    public FrameworkMuxer create(String path, String outputMimeType) throws IOException {
      MediaMuxer mediaMuxer = new MediaMuxer(path, mimeTypeToMuxerOutputFormat(outputMimeType));
      return new FrameworkMuxer(mediaMuxer);
    }

    @RequiresApi(26)
    @Override
    public FrameworkMuxer create(ParcelFileDescriptor parcelFileDescriptor, String outputMimeType)
        throws IOException {
      MediaMuxer mediaMuxer =
          new MediaMuxer(
              parcelFileDescriptor.getFileDescriptor(),
              mimeTypeToMuxerOutputFormat(outputMimeType));
      return new FrameworkMuxer(mediaMuxer);
    }

    @Override
    public boolean supportsOutputMimeType(String mimeType) {
      try {
        mimeTypeToMuxerOutputFormat(mimeType);
      } catch (IllegalArgumentException e) {
        return false;
      }
      return true;
    }

    @Override
    public boolean supportsSampleMimeType(
        @Nullable String sampleMimeType, String containerMimeType) {
      return getSupportedSampleMimeTypes(MimeTypes.getTrackType(sampleMimeType), containerMimeType)
          .contains(sampleMimeType);
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(
        @C.TrackType int trackType, String containerMimeType) {
      // MediaMuxer supported sample formats are documented in MediaMuxer.addTrack(MediaFormat).
      if (trackType == C.TRACK_TYPE_VIDEO) {
        return SUPPORTED_CONTAINER_TO_VIDEO_SAMPLE_MIME_TYPES.getOrDefault(
            containerMimeType, ImmutableList.of());
      } else if (trackType == C.TRACK_TYPE_AUDIO) {
        return SUPPORTED_CONTAINER_TO_AUDIO_SAMPLE_MIME_TYPES.getOrDefault(
            containerMimeType, ImmutableList.of());
      }
      return ImmutableList.of();
    }
  }

  private final MediaMuxer mediaMuxer;
  private final MediaCodec.BufferInfo bufferInfo;

  private boolean isStarted;

  private FrameworkMuxer(MediaMuxer mediaMuxer) {
    this.mediaMuxer = mediaMuxer;
    bufferInfo = new MediaCodec.BufferInfo();
  }

  @Override
  public int addTrack(Format format) {
    String sampleMimeType = checkNotNull(format.sampleMimeType);
    MediaFormat mediaFormat;
    if (MimeTypes.isAudio(sampleMimeType)) {
      mediaFormat =
          MediaFormat.createAudioFormat(
              castNonNull(sampleMimeType), format.sampleRate, format.channelCount);
    } else {
      mediaFormat =
          MediaFormat.createVideoFormat(castNonNull(sampleMimeType), format.width, format.height);
      mediaMuxer.setOrientationHint(format.rotationDegrees);
    }
    MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);
    return mediaMuxer.addTrack(mediaFormat);
  }

  @SuppressLint("WrongConstant") // C.BUFFER_FLAG_KEY_FRAME equals MediaCodec.BUFFER_FLAG_KEY_FRAME.
  @Override
  public void writeSampleData(
      int trackIndex, ByteBuffer data, boolean isKeyFrame, long presentationTimeUs) {
    if (!isStarted) {
      isStarted = true;
      mediaMuxer.start();
    }
    int offset = data.position();
    int size = data.limit() - offset;
    int flags = isKeyFrame ? C.BUFFER_FLAG_KEY_FRAME : 0;
    bufferInfo.set(offset, size, presentationTimeUs, flags);
    mediaMuxer.writeSampleData(trackIndex, data, bufferInfo);
  }

  @Override
  public void release(boolean forCancellation) {
    if (!isStarted) {
      mediaMuxer.release();
      return;
    }

    isStarted = false;
    try {
      mediaMuxer.stop();
    } catch (IllegalStateException e) {
      if (SDK_INT < 30) {
        // Set the muxer state to stopped even if mediaMuxer.stop() failed so that
        // mediaMuxer.release() doesn't attempt to stop the muxer and therefore doesn't throw the
        // same exception without releasing its resources. This is already implemented in MediaMuxer
        // from API level 30.
        try {
          Field muxerStoppedStateField = MediaMuxer.class.getDeclaredField("MUXER_STATE_STOPPED");
          muxerStoppedStateField.setAccessible(true);
          int muxerStoppedState = castNonNull((Integer) muxerStoppedStateField.get(mediaMuxer));
          Field muxerStateField = MediaMuxer.class.getDeclaredField("mState");
          muxerStateField.setAccessible(true);
          muxerStateField.set(mediaMuxer, muxerStoppedState);
        } catch (Exception reflectionException) {
          // Do nothing.
        }
      }
      // It doesn't matter that stopping the muxer throws if the transformation is being cancelled.
      if (!forCancellation) {
        throw e;
      }
    } finally {
      mediaMuxer.release();
    }
  }

  /**
   * Converts a {@link MimeTypes MIME type} into a {@link MediaMuxer.OutputFormat MediaMuxer output
   * format}.
   *
   * @param mimeType The {@link MimeTypes MIME type} to convert.
   * @return The corresponding {@link MediaMuxer.OutputFormat MediaMuxer output format}.
   * @throws IllegalArgumentException If the {@link MimeTypes MIME type} is not supported as output
   *     format.
   */
  private static int mimeTypeToMuxerOutputFormat(String mimeType) {
    if (mimeType.equals(MimeTypes.VIDEO_MP4)) {
      return MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
    } else if (SDK_INT >= 21 && mimeType.equals(MimeTypes.VIDEO_WEBM)) {
      return MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM;
    } else {
      throw new IllegalArgumentException("Unsupported output MIME type: " + mimeType);
    }
  }
}

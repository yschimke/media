/*
 * Copyright 2020 The Android Open Source Project
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
import static androidx.media3.exoplayer.source.SampleStream.FLAG_REQUIRE_FORMAT;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.source.SampleStream.ReadDataResult;
import androidx.media3.extractor.metadata.mp4.SlowMotionData;

/* package */ final class TransformerAudioRenderer extends TransformerBaseRenderer {

  private static final String TAG = "TAudioRenderer";

  private final Codec.EncoderFactory encoderFactory;
  private final Codec.DecoderFactory decoderFactory;
  private final DecoderInputBuffer decoderInputBuffer;

  public TransformerAudioRenderer(
      MuxerWrapper muxerWrapper,
      TransformerMediaClock mediaClock,
      TransformationRequest transformationRequest,
      Codec.EncoderFactory encoderFactory,
      Codec.DecoderFactory decoderFactory) {
    super(C.TRACK_TYPE_AUDIO, muxerWrapper, mediaClock, transformationRequest);
    this.encoderFactory = encoderFactory;
    this.decoderFactory = decoderFactory;
    decoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
  }

  @Override
  public String getName() {
    return TAG;
  }

  /** Attempts to read the input format and to initialize the {@link SamplePipeline}. */
  @Override
  protected boolean ensureConfigured() throws TransformationException {
    if (samplePipeline != null) {
      return true;
    }
    FormatHolder formatHolder = getFormatHolder();
    @ReadDataResult
    int result = readSource(formatHolder, decoderInputBuffer, /* readFlags= */ FLAG_REQUIRE_FORMAT);
    if (result != C.RESULT_FORMAT_READ) {
      return false;
    }
    Format inputFormat = checkNotNull(formatHolder.format);
    String sampleMimeType = checkNotNull(inputFormat.sampleMimeType);
    if (transformationRequest.audioMimeType == null
        && !muxerWrapper.supportsSampleMimeType(sampleMimeType)) {
      throw TransformationException.createForMuxer(
          new IllegalArgumentException(
              "The output sample MIME inferred from the input format is not supported by the muxer."
                  + " Sample MIME type: "
                  + sampleMimeType),
          TransformationException.ERROR_CODE_MUXER_SAMPLE_MIME_TYPE_UNSUPPORTED);
    }
    if (shouldPassthrough(inputFormat)) {
      samplePipeline = new PassthroughSamplePipeline(inputFormat);
    } else {
      samplePipeline =
          new AudioSamplePipeline(
              inputFormat, transformationRequest, encoderFactory, decoderFactory);
    }
    return true;
  }

  private boolean shouldPassthrough(Format inputFormat) {
    if (transformationRequest.audioMimeType != null
        && !transformationRequest.audioMimeType.equals(inputFormat.sampleMimeType)) {
      return false;
    }
    if (transformationRequest.flattenForSlowMotion && isSlowMotion(inputFormat)) {
      return false;
    }
    return true;
  }

  private static boolean isSlowMotion(Format format) {
    @Nullable Metadata metadata = format.metadata;
    if (metadata == null) {
      return false;
    }
    for (int i = 0; i < metadata.length(); i++) {
      if (metadata.get(i) instanceof SlowMotionData) {
        return true;
      }
    }
    return false;
  }
}

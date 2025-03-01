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

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.decoder.DecoderInputBuffer;

/** Pipeline that passes through the samples without any re-encoding or transformation. */
/* package */ final class PassthroughSamplePipeline implements SamplePipeline {

  private final DecoderInputBuffer buffer;
  private final Format format;

  private boolean hasPendingBuffer;

  public PassthroughSamplePipeline(Format format) {
    this.format = format;
    buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
    hasPendingBuffer = false;
  }

  @Override
  @Nullable
  public DecoderInputBuffer dequeueInputBuffer() {
    return hasPendingBuffer ? null : buffer;
  }

  @Override
  public void queueInputBuffer() {
    hasPendingBuffer = true;
  }

  @Override
  public boolean processData() {
    return false;
  }

  @Override
  public Format getOutputFormat() {
    return format;
  }

  @Override
  @Nullable
  public DecoderInputBuffer getOutputBuffer() {
    return hasPendingBuffer ? buffer : null;
  }

  @Override
  public void releaseOutputBuffer() {
    buffer.clear();
    hasPendingBuffer = false;
  }

  @Override
  public boolean isEnded() {
    return buffer.isEndOfStream();
  }

  @Override
  public void release() {}
}

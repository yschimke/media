/*
 * Copyright (C) 2017 The Android Open Source Project
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
package androidx.media3.test.utils;

import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.chunk.MediaChunk;
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import java.util.List;

/**
 * A fake {@link ExoTrackSelection} that only returns 1 fixed track, and allows querying the number
 * of calls to its methods.
 */
@UnstableApi
public final class FakeTrackSelection implements ExoTrackSelection {

  private final TrackGroup rendererTrackGroup;

  public int enableCount;
  public int releaseCount;
  public boolean isEnabled;

  public FakeTrackSelection(TrackGroup rendererTrackGroup) {
    this.rendererTrackGroup = rendererTrackGroup;
  }

  // TrackSelection implementation.

  @Override
  public int getType() {
    return TYPE_UNSET;
  }

  @Override
  public TrackGroup getTrackGroup() {
    return rendererTrackGroup;
  }

  @Override
  public int length() {
    return rendererTrackGroup.length;
  }

  @Override
  public Format getFormat(int index) {
    return rendererTrackGroup.getFormat(0);
  }

  @Override
  public int getIndexInTrackGroup(int index) {
    return 0;
  }

  @Override
  public int indexOf(Format format) {
    assertThat(isEnabled).isTrue();
    return 0;
  }

  @Override
  public int indexOf(int indexInTrackGroup) {
    return 0;
  }

  // ExoTrackSelection specific methods.

  @Override
  public void enable() {
    // assert that track selection is in disabled state before this call.
    assertThat(isEnabled).isFalse();
    enableCount++;
    isEnabled = true;
  }

  @Override
  public void disable() {
    // assert that track selection is in enabled state before this call.
    assertThat(isEnabled).isTrue();
    releaseCount++;
    isEnabled = false;
  }

  @Override
  public Format getSelectedFormat() {
    return rendererTrackGroup.getFormat(0);
  }

  @Override
  public int getSelectedIndexInTrackGroup() {
    return 0;
  }

  @Override
  public int getSelectedIndex() {
    return 0;
  }

  @Override
  public int getSelectionReason() {
    return C.SELECTION_REASON_UNKNOWN;
  }

  @Override
  @Nullable
  public Object getSelectionData() {
    return null;
  }

  @Override
  public void onPlaybackSpeed(float playbackSpeed) {
    // Do nothing.
  }

  @Override
  public void updateSelectedTrack(
      long playbackPositionUs,
      long bufferedDurationUs,
      long availableDurationUs,
      List<? extends MediaChunk> queue,
      MediaChunkIterator[] mediaChunkIterators) {
    assertThat(isEnabled).isTrue();
  }

  @Override
  public int evaluateQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
    assertThat(isEnabled).isTrue();
    return 0;
  }

  @Override
  public boolean blacklist(int index, long exclusionDurationMs) {
    assertThat(isEnabled).isTrue();
    return false;
  }

  @Override
  public boolean isBlacklisted(int index, long nowMs) {
    assertThat(isEnabled).isTrue();
    return false;
  }
}

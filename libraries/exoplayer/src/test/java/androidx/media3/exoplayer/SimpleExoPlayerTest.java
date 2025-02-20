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
package androidx.media3.exoplayer;

import static androidx.media3.test.utils.robolectric.TestPlayerRunHelper.runUntilPlaybackState;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.test.utils.ExoPlayerTestRunner;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.FakeMediaSource;
import androidx.media3.test.utils.FakeTimeline;
import androidx.media3.test.utils.FakeVideoRenderer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** Unit test for {@link SimpleExoPlayer}. */
@SuppressWarnings("deprecation") // Testing deprecated type.
@RunWith(AndroidJUnit4.class)
public class SimpleExoPlayerTest {

  @Test
  @Config(sdk = Config.ALL_SDKS)
  public void builder_inBackgroundThread_doesNotThrow() throws Exception {
    Thread builderThread =
        new Thread(
            () -> new SimpleExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build());
    AtomicReference<Throwable> builderThrow = new AtomicReference<>();
    builderThread.setUncaughtExceptionHandler((thread, throwable) -> builderThrow.set(throwable));

    builderThread.start();
    builderThread.join();

    assertThat(builderThrow.get()).isNull();
  }

  @Test
  public void onPlaylistMetadataChanged_calledWhenPlaylistMetadataSet() {
    SimpleExoPlayer player =
        new SimpleExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build();
    Player.Listener playerListener = mock(Player.Listener.class);
    player.addListener(playerListener);
    AnalyticsListener analyticsListener = mock(AnalyticsListener.class);
    player.addAnalyticsListener(analyticsListener);

    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setTitle("test").build();
    player.setPlaylistMetadata(mediaMetadata);

    verify(playerListener).onPlaylistMetadataChanged(mediaMetadata);
    verify(analyticsListener).onPlaylistMetadataChanged(any(), eq(mediaMetadata));
  }

  @Test
  public void release_triggersAllPendingEventsInAnalyticsListeners() throws Exception {
    SimpleExoPlayer player =
        new SimpleExoPlayer.Builder(
                ApplicationProvider.getApplicationContext(),
                (handler, videoListener, audioListener, textOutput, metadataOutput) ->
                    new Renderer[] {new FakeVideoRenderer(handler, videoListener)})
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    AnalyticsListener listener = mock(AnalyticsListener.class);
    player.addAnalyticsListener(listener);
    // Do something that requires clean-up callbacks like decoder disabling.
    player.setMediaSource(
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT));
    player.prepare();
    player.play();
    runUntilPlaybackState(player, Player.STATE_READY);

    player.release();
    ShadowLooper.runMainLooperToNextTask();

    verify(listener).onVideoDisabled(any(), any());
    verify(listener).onPlayerReleased(any());
  }

  @Test
  public void releaseAfterRendererEvents_triggersPendingVideoEventsInListener() throws Exception {
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 0));
    SimpleExoPlayer player =
        new SimpleExoPlayer.Builder(
                ApplicationProvider.getApplicationContext(),
                (handler, videoListener, audioListener, textOutput, metadataOutput) ->
                    new Renderer[] {new FakeVideoRenderer(handler, videoListener)})
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    player.setMediaSource(
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT));
    player.setVideoSurface(surface);
    player.prepare();
    player.play();
    runUntilPlaybackState(player, Player.STATE_READY);

    player.release();
    surface.release();
    ShadowLooper.runMainLooperToNextTask();

    verify(listener, atLeastOnce()).onEvents(any(), any()); // EventListener
    verify(listener).onRenderedFirstFrame(); // VideoListener
  }

  @Test
  public void releaseAfterVolumeChanges_triggerPendingVolumeEventInListener() throws Exception {
    SimpleExoPlayer player =
        new SimpleExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);

    player.setVolume(0F);
    player.release();
    ShadowLooper.runMainLooperToNextTask();

    verify(listener).onVolumeChanged(anyFloat());
  }

  @Test
  public void releaseAfterVolumeChanges_triggerPendingDeviceVolumeEventsInListener() {
    SimpleExoPlayer player =
        new SimpleExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);

    int deviceVolume = player.getDeviceVolume();
    try {
      player.setDeviceVolume(deviceVolume + 1); // No-op if at max volume.
      player.setDeviceVolume(deviceVolume - 1); // No-op if at min volume.
    } finally {
      player.setDeviceVolume(deviceVolume); // Restore original volume.
    }

    player.release();
    ShadowLooper.runMainLooperToNextTask();

    verify(listener, atLeast(2)).onDeviceVolumeChanged(anyInt(), anyBoolean());
  }
}

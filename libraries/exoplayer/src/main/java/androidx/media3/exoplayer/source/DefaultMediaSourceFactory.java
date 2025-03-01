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
package androidx.media3.exoplayer.source;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.castNonNull;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.AdViewProvider;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.ads.AdsLoader;
import androidx.media3.exoplayer.source.ads.AdsMediaSource;
import androidx.media3.exoplayer.text.SubtitleDecoderFactory;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.text.SubtitleExtractor;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * The default {@link MediaSource.Factory} implementation.
 *
 * <p>This implementation delegates calls to {@link #createMediaSource(MediaItem)} to the following
 * factories:
 *
 * <ul>
 *   <li>{@code DashMediaSource.Factory} if the item's {@link MediaItem.LocalConfiguration#uri uri}
 *       ends in '.mpd' or if its {@link MediaItem.LocalConfiguration#mimeType mimeType field} is
 *       explicitly set to {@link MimeTypes#APPLICATION_MPD} (Requires the <a
 *       href="https://exoplayer.dev/hello-world.html#add-exoplayer-modules">exoplayer-dash module
 *       to be added</a> to the app).
 *   <li>{@code HlsMediaSource.Factory} if the item's {@link MediaItem.LocalConfiguration#uri uri}
 *       ends in '.m3u8' or if its {@link MediaItem.LocalConfiguration#mimeType mimeType field} is
 *       explicitly set to {@link MimeTypes#APPLICATION_M3U8} (Requires the <a
 *       href="https://exoplayer.dev/hello-world.html#add-exoplayer-modules">exoplayer-hls module to
 *       be added</a> to the app).
 *   <li>{@code SsMediaSource.Factory} if the item's {@link MediaItem.LocalConfiguration#uri uri}
 *       ends in '.ism', '.ism/Manifest' or if its {@link MediaItem.LocalConfiguration#mimeType
 *       mimeType field} is explicitly set to {@link MimeTypes#APPLICATION_SS} (Requires the <a
 *       href="https://exoplayer.dev/hello-world.html#add-exoplayer-modules">
 *       exoplayer-smoothstreaming module to be added</a> to the app).
 *   <li>{@link ProgressiveMediaSource.Factory} serves as a fallback if the item's {@link
 *       MediaItem.LocalConfiguration#uri uri} doesn't match one of the above. It tries to infer the
 *       required extractor by using the {@link DefaultExtractorsFactory} or the {@link
 *       ExtractorsFactory} provided in the constructor. An {@link UnrecognizedInputFormatException}
 *       is thrown if none of the available extractors can read the stream.
 * </ul>
 *
 * <h2>Ad support for media items with ad tag URIs</h2>
 *
 * <p>To support media items with {@link MediaItem.LocalConfiguration#adsConfiguration ads
 * configuration}, {@link #setAdsLoaderProvider} and {@link #setAdViewProvider} need to be called to
 * configure the factory with the required providers.
 */
@SuppressWarnings("deprecation") // Implement deprecated type for backwards compatibility.
public final class DefaultMediaSourceFactory implements MediaSourceFactory {

  /** @deprecated Use {@link AdsLoader.Provider} instead. */
  @UnstableApi
  @Deprecated
  public interface AdsLoaderProvider extends AdsLoader.Provider {}

  private static final String TAG = "DMediaSourceFactory";

  private final DataSource.Factory dataSourceFactory;
  private final DelegateFactoryLoader delegateFactoryLoader;

  @Nullable private final MediaSource.Factory serverSideDaiMediaSourceFactory;
  @Nullable private AdsLoader.Provider adsLoaderProvider;
  @Nullable private AdViewProvider adViewProvider;
  @Nullable private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private long liveTargetOffsetMs;
  private long liveMinOffsetMs;
  private long liveMaxOffsetMs;
  private float liveMinSpeed;
  private float liveMaxSpeed;
  private boolean useProgressiveMediaSourceForSubtitles;

  /**
   * Creates a new instance.
   *
   * @param context Any context.
   */
  public DefaultMediaSourceFactory(Context context) {
    this(new DefaultDataSource.Factory(context));
  }

  /**
   * Creates a new instance.
   *
   * @param context Any context.
   * @param extractorsFactory An {@link ExtractorsFactory} used to extract progressive media from
   *     its container.
   */
  @UnstableApi
  public DefaultMediaSourceFactory(Context context, ExtractorsFactory extractorsFactory) {
    this(
        new DefaultDataSource.Factory(context),
        extractorsFactory,
        /* serverSideDaiMediaSourceFactory= */ null);
  }

  /**
   * Creates a new instance.
   *
   * @param dataSourceFactory A {@link DataSource.Factory} to create {@link DataSource} instances
   *     for requesting media data.
   */
  @UnstableApi
  public DefaultMediaSourceFactory(DataSource.Factory dataSourceFactory) {
    this(
        dataSourceFactory,
        new DefaultExtractorsFactory(),
        /* serverSideDaiMediaSourceFactory= */ null);
  }

  /**
   * Creates a new instance.
   *
   * @param dataSourceFactory A {@link DataSource.Factory} to create {@link DataSource} instances
   *     for requesting media data.
   * @param extractorsFactory An {@link ExtractorsFactory} used to extract progressive media from
   *     its container.
   * @param serverSideDaiMediaSourceFactory A {@link MediaSource.Factory} for creating server side
   *     inserted ad media sources.
   */
  @UnstableApi
  public DefaultMediaSourceFactory(
      DataSource.Factory dataSourceFactory,
      ExtractorsFactory extractorsFactory,
      @Nullable MediaSource.Factory serverSideDaiMediaSourceFactory) {
    this.dataSourceFactory = dataSourceFactory;
    // Temporary until factory registration is agreed upon.
    this.serverSideDaiMediaSourceFactory = serverSideDaiMediaSourceFactory;

    delegateFactoryLoader = new DelegateFactoryLoader(dataSourceFactory, extractorsFactory);
    liveTargetOffsetMs = C.TIME_UNSET;
    liveMinOffsetMs = C.TIME_UNSET;
    liveMaxOffsetMs = C.TIME_UNSET;
    liveMinSpeed = C.RATE_UNSET;
    liveMaxSpeed = C.RATE_UNSET;
  }

  /**
   * Sets whether a {@link ProgressiveMediaSource} or {@link SingleSampleMediaSource} is constructed
   * to handle {@link MediaItem.LocalConfiguration#subtitleConfigurations}. Defaults to false (i.e.
   * {@link SingleSampleMediaSource}.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   *
   * @param useProgressiveMediaSourceForSubtitles Indicates that {@link ProgressiveMediaSource}
   *     should be used for subtitles instead of {@link SingleSampleMediaSource}.
   * @return This factory, for convenience.
   */
  @UnstableApi
  public DefaultMediaSourceFactory experimentalUseProgressiveMediaSourceForSubtitles(
      boolean useProgressiveMediaSourceForSubtitles) {
    this.useProgressiveMediaSourceForSubtitles = useProgressiveMediaSourceForSubtitles;
    return this;
  }

  /**
   * Sets the {@link AdsLoader.Provider} that provides {@link AdsLoader} instances for media items
   * that have {@link MediaItem.LocalConfiguration#adsConfiguration ads configurations}.
   *
   * @param adsLoaderProvider A provider for {@link AdsLoader} instances.
   * @return This factory, for convenience.
   */
  public DefaultMediaSourceFactory setAdsLoaderProvider(
      @Nullable AdsLoader.Provider adsLoaderProvider) {
    this.adsLoaderProvider = adsLoaderProvider;
    return this;
  }

  /**
   * Sets the {@link AdViewProvider} that provides information about views for the ad playback UI.
   *
   * @param adViewProvider A provider for {@link AdsLoader} instances.
   * @return This factory, for convenience.
   */
  public DefaultMediaSourceFactory setAdViewProvider(@Nullable AdViewProvider adViewProvider) {
    this.adViewProvider = adViewProvider;
    return this;
  }

  /**
   * Sets the target live offset for live streams, in milliseconds.
   *
   * @param liveTargetOffsetMs The target live offset, in milliseconds, or {@link C#TIME_UNSET} to
   *     use the media-defined default.
   * @return This factory, for convenience.
   */
  @UnstableApi
  public DefaultMediaSourceFactory setLiveTargetOffsetMs(long liveTargetOffsetMs) {
    this.liveTargetOffsetMs = liveTargetOffsetMs;
    return this;
  }

  /**
   * Sets the minimum offset from the live edge for live streams, in milliseconds.
   *
   * @param liveMinOffsetMs The minimum allowed live offset, in milliseconds, or {@link
   *     C#TIME_UNSET} to use the media-defined default.
   * @return This factory, for convenience.
   */
  @UnstableApi
  public DefaultMediaSourceFactory setLiveMinOffsetMs(long liveMinOffsetMs) {
    this.liveMinOffsetMs = liveMinOffsetMs;
    return this;
  }

  /**
   * Sets the maximum offset from the live edge for live streams, in milliseconds.
   *
   * @param liveMaxOffsetMs The maximum allowed live offset, in milliseconds, or {@link
   *     C#TIME_UNSET} to use the media-defined default.
   * @return This factory, for convenience.
   */
  @UnstableApi
  public DefaultMediaSourceFactory setLiveMaxOffsetMs(long liveMaxOffsetMs) {
    this.liveMaxOffsetMs = liveMaxOffsetMs;
    return this;
  }

  /**
   * Sets the minimum playback speed for live streams.
   *
   * @param minSpeed The minimum factor by which playback can be sped up for live streams, or {@link
   *     C#RATE_UNSET} to use the media-defined default.
   * @return This factory, for convenience.
   */
  @UnstableApi
  public DefaultMediaSourceFactory setLiveMinSpeed(float minSpeed) {
    this.liveMinSpeed = minSpeed;
    return this;
  }

  /**
   * Sets the maximum playback speed for live streams.
   *
   * @param maxSpeed The maximum factor by which playback can be sped up for live streams, or {@link
   *     C#RATE_UNSET} to use the media-defined default.
   * @return This factory, for convenience.
   */
  @UnstableApi
  public DefaultMediaSourceFactory setLiveMaxSpeed(float maxSpeed) {
    this.liveMaxSpeed = maxSpeed;
    return this;
  }

  @UnstableApi
  @Override
  public DefaultMediaSourceFactory setDrmSessionManagerProvider(
      @Nullable DrmSessionManagerProvider drmSessionManagerProvider) {
    delegateFactoryLoader.setDrmSessionManagerProvider(drmSessionManagerProvider);
    return this;
  }

  @UnstableApi
  @Override
  public DefaultMediaSourceFactory setLoadErrorHandlingPolicy(
      @Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    delegateFactoryLoader.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
    return this;
  }

  @UnstableApi
  @Override
  public int[] getSupportedTypes() {
    return delegateFactoryLoader.getSupportedTypes();
  }

  @UnstableApi
  @Override
  public MediaSource createMediaSource(MediaItem mediaItem) {
    Assertions.checkNotNull(mediaItem.localConfiguration);
    @Nullable String scheme = mediaItem.localConfiguration.uri.getScheme();
    if (scheme != null && scheme.equals("imadai")) {
      return checkNotNull(serverSideDaiMediaSourceFactory).createMediaSource(mediaItem);
    }
    @C.ContentType
    int type =
        Util.inferContentTypeForUriAndMimeType(
            mediaItem.localConfiguration.uri, mediaItem.localConfiguration.mimeType);
    @Nullable
    MediaSource.Factory mediaSourceFactory = delegateFactoryLoader.getMediaSourceFactory(type);
    checkStateNotNull(
        mediaSourceFactory, "No suitable media source factory found for content type: " + type);

    MediaItem.LiveConfiguration.Builder liveConfigurationBuilder =
        mediaItem.liveConfiguration.buildUpon();
    if (mediaItem.liveConfiguration.targetOffsetMs == C.TIME_UNSET) {
      liveConfigurationBuilder.setTargetOffsetMs(liveTargetOffsetMs);
    }
    if (mediaItem.liveConfiguration.minPlaybackSpeed == C.RATE_UNSET) {
      liveConfigurationBuilder.setMinPlaybackSpeed(liveMinSpeed);
    }
    if (mediaItem.liveConfiguration.maxPlaybackSpeed == C.RATE_UNSET) {
      liveConfigurationBuilder.setMaxPlaybackSpeed(liveMaxSpeed);
    }
    if (mediaItem.liveConfiguration.minOffsetMs == C.TIME_UNSET) {
      liveConfigurationBuilder.setMinOffsetMs(liveMinOffsetMs);
    }
    if (mediaItem.liveConfiguration.maxOffsetMs == C.TIME_UNSET) {
      liveConfigurationBuilder.setMaxOffsetMs(liveMaxOffsetMs);
    }
    MediaItem.LiveConfiguration liveConfiguration = liveConfigurationBuilder.build();
    // Make sure to retain the very same media item instance, if no value needs to be overridden.
    if (!liveConfiguration.equals(mediaItem.liveConfiguration)) {
      mediaItem = mediaItem.buildUpon().setLiveConfiguration(liveConfiguration).build();
    }

    MediaSource mediaSource = mediaSourceFactory.createMediaSource(mediaItem);

    List<MediaItem.SubtitleConfiguration> subtitleConfigurations =
        castNonNull(mediaItem.localConfiguration).subtitleConfigurations;
    if (!subtitleConfigurations.isEmpty()) {
      MediaSource[] mediaSources = new MediaSource[subtitleConfigurations.size() + 1];
      mediaSources[0] = mediaSource;
      for (int i = 0; i < subtitleConfigurations.size(); i++) {
        if (useProgressiveMediaSourceForSubtitles) {
          Format format =
              new Format.Builder()
                  .setSampleMimeType(subtitleConfigurations.get(i).mimeType)
                  .setLanguage(subtitleConfigurations.get(i).language)
                  .setSelectionFlags(subtitleConfigurations.get(i).selectionFlags)
                  .setRoleFlags(subtitleConfigurations.get(i).roleFlags)
                  .setLabel(subtitleConfigurations.get(i).label)
                  .setId(subtitleConfigurations.get(i).id)
                  .build();
          ExtractorsFactory extractorsFactory =
              () ->
                  new Extractor[] {
                    SubtitleDecoderFactory.DEFAULT.supportsFormat(format)
                        ? new SubtitleExtractor(
                            SubtitleDecoderFactory.DEFAULT.createDecoder(format), format)
                        : new UnknownSubtitlesExtractor(format)
                  };
          mediaSources[i + 1] =
              new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                  .createMediaSource(
                      MediaItem.fromUri(subtitleConfigurations.get(i).uri.toString()));
        } else {
          SingleSampleMediaSource.Factory singleSampleSourceFactory =
              new SingleSampleMediaSource.Factory(dataSourceFactory)
                  .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
          mediaSources[i + 1] =
              singleSampleSourceFactory.createMediaSource(
                  subtitleConfigurations.get(i), /* durationUs= */ C.TIME_UNSET);
        }
      }

      mediaSource = new MergingMediaSource(mediaSources);
    }
    return maybeWrapWithAdsMediaSource(mediaItem, maybeClipMediaSource(mediaItem, mediaSource));
  }

  // internal methods

  private static MediaSource maybeClipMediaSource(MediaItem mediaItem, MediaSource mediaSource) {
    if (mediaItem.clippingConfiguration.startPositionMs == 0
        && mediaItem.clippingConfiguration.endPositionMs == C.TIME_END_OF_SOURCE
        && !mediaItem.clippingConfiguration.relativeToDefaultPosition) {
      return mediaSource;
    }
    return new ClippingMediaSource(
        mediaSource,
        Util.msToUs(mediaItem.clippingConfiguration.startPositionMs),
        Util.msToUs(mediaItem.clippingConfiguration.endPositionMs),
        /* enableInitialDiscontinuity= */ !mediaItem.clippingConfiguration.startsAtKeyFrame,
        /* allowDynamicClippingUpdates= */ mediaItem.clippingConfiguration.relativeToLiveWindow,
        mediaItem.clippingConfiguration.relativeToDefaultPosition);
  }

  private MediaSource maybeWrapWithAdsMediaSource(MediaItem mediaItem, MediaSource mediaSource) {
    checkNotNull(mediaItem.localConfiguration);
    @Nullable
    MediaItem.AdsConfiguration adsConfiguration = mediaItem.localConfiguration.adsConfiguration;
    if (adsConfiguration == null) {
      return mediaSource;
    }
    @Nullable AdsLoader.Provider adsLoaderProvider = this.adsLoaderProvider;
    @Nullable AdViewProvider adViewProvider = this.adViewProvider;
    if (adsLoaderProvider == null || adViewProvider == null) {
      Log.w(
          TAG,
          "Playing media without ads. Configure ad support by calling setAdsLoaderProvider and"
              + " setAdViewProvider.");
      return mediaSource;
    }
    @Nullable AdsLoader adsLoader = adsLoaderProvider.getAdsLoader(adsConfiguration);
    if (adsLoader == null) {
      Log.w(TAG, "Playing media without ads, as no AdsLoader was provided.");
      return mediaSource;
    }
    return new AdsMediaSource(
        mediaSource,
        new DataSpec(adsConfiguration.adTagUri),
        /* adsId= */ adsConfiguration.adsId != null
            ? adsConfiguration.adsId
            : ImmutableList.of(
                mediaItem.mediaId, mediaItem.localConfiguration.uri, adsConfiguration.adTagUri),
        /* adMediaSourceFactory= */ this,
        adsLoader,
        adViewProvider);
  }

  /** Loads media source factories lazily. */
  private static final class DelegateFactoryLoader {
    private final DataSource.Factory dataSourceFactory;
    private final ExtractorsFactory extractorsFactory;
    private final Map<Integer, @NullableType Supplier<MediaSource.Factory>>
        mediaSourceFactorySuppliers;
    private final Set<Integer> supportedTypes;
    private final Map<Integer, MediaSource.Factory> mediaSourceFactories;

    @Nullable private DrmSessionManagerProvider drmSessionManagerProvider;
    @Nullable private LoadErrorHandlingPolicy loadErrorHandlingPolicy;

    public DelegateFactoryLoader(
        DataSource.Factory dataSourceFactory, ExtractorsFactory extractorsFactory) {
      this.dataSourceFactory = dataSourceFactory;
      this.extractorsFactory = extractorsFactory;
      mediaSourceFactorySuppliers = new HashMap<>();
      supportedTypes = new HashSet<>();
      mediaSourceFactories = new HashMap<>();
    }

    @C.ContentType
    public int[] getSupportedTypes() {
      ensureAllSuppliersAreLoaded();
      return Ints.toArray(supportedTypes);
    }

    @SuppressWarnings("deprecation") // Forwarding to deprecated methods.
    @Nullable
    public MediaSource.Factory getMediaSourceFactory(@C.ContentType int contentType) {
      @Nullable MediaSource.Factory mediaSourceFactory = mediaSourceFactories.get(contentType);
      if (mediaSourceFactory != null) {
        return mediaSourceFactory;
      }
      @Nullable
      Supplier<MediaSource.Factory> mediaSourceFactorySupplier = maybeLoadSupplier(contentType);
      if (mediaSourceFactorySupplier == null) {
        return null;
      }

      mediaSourceFactory = mediaSourceFactorySupplier.get();
      if (drmSessionManagerProvider != null) {
        mediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider);
      }
      if (loadErrorHandlingPolicy != null) {
        mediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
      }
      mediaSourceFactories.put(contentType, mediaSourceFactory);
      return mediaSourceFactory;
    }

    public void setDrmSessionManagerProvider(
        @Nullable DrmSessionManagerProvider drmSessionManagerProvider) {
      this.drmSessionManagerProvider = drmSessionManagerProvider;
      for (MediaSource.Factory mediaSourceFactory : mediaSourceFactories.values()) {
        mediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider);
      }
    }

    public void setLoadErrorHandlingPolicy(
        @Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
      for (MediaSource.Factory mediaSourceFactory : mediaSourceFactories.values()) {
        mediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
      }
    }

    private void ensureAllSuppliersAreLoaded() {
      maybeLoadSupplier(C.TYPE_DASH);
      maybeLoadSupplier(C.TYPE_SS);
      maybeLoadSupplier(C.TYPE_HLS);
      maybeLoadSupplier(C.TYPE_RTSP);
      maybeLoadSupplier(C.TYPE_OTHER);
    }

    @Nullable
    private Supplier<MediaSource.Factory> maybeLoadSupplier(@C.ContentType int contentType) {
      if (mediaSourceFactorySuppliers.containsKey(contentType)) {
        return mediaSourceFactorySuppliers.get(contentType);
      }

      @Nullable Supplier<MediaSource.Factory> mediaSourceFactorySupplier = null;
      try {
        Class<? extends MediaSource.Factory> clazz;
        switch (contentType) {
          case C.TYPE_DASH:
            clazz =
                Class.forName("androidx.media3.exoplayer.dash.DashMediaSource$Factory")
                    .asSubclass(MediaSource.Factory.class);
            mediaSourceFactorySupplier = () -> newInstance(clazz, dataSourceFactory);
            break;
          case C.TYPE_SS:
            clazz =
                Class.forName("androidx.media3.exoplayer.smoothstreaming.SsMediaSource$Factory")
                    .asSubclass(MediaSource.Factory.class);
            mediaSourceFactorySupplier = () -> newInstance(clazz, dataSourceFactory);
            break;
          case C.TYPE_HLS:
            clazz =
                Class.forName("androidx.media3.exoplayer.hls.HlsMediaSource$Factory")
                    .asSubclass(MediaSource.Factory.class);
            mediaSourceFactorySupplier = () -> newInstance(clazz, dataSourceFactory);
            break;
          case C.TYPE_RTSP:
            clazz =
                Class.forName("androidx.media3.exoplayer.rtsp.RtspMediaSource$Factory")
                    .asSubclass(MediaSource.Factory.class);
            mediaSourceFactorySupplier = () -> newInstance(clazz);
            break;
          case C.TYPE_OTHER:
            mediaSourceFactorySupplier =
                () -> new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory);
            break;
          default:
            // Do nothing.
        }
      } catch (ClassNotFoundException e) {
        // Expected if the app was built without the specific module.
      }
      mediaSourceFactorySuppliers.put(contentType, mediaSourceFactorySupplier);
      if (mediaSourceFactorySupplier != null) {
        supportedTypes.add(contentType);
      }
      return mediaSourceFactorySupplier;
    }
  }

  private static final class UnknownSubtitlesExtractor implements Extractor {
    private final Format format;

    public UnknownSubtitlesExtractor(Format format) {
      this.format = format;
    }

    @Override
    public boolean sniff(ExtractorInput input) {
      return true;
    }

    @Override
    public void init(ExtractorOutput output) {
      TrackOutput trackOutput = output.track(/* id= */ 0, C.TRACK_TYPE_TEXT);
      output.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
      output.endTracks();
      trackOutput.format(
          format
              .buildUpon()
              .setSampleMimeType(MimeTypes.TEXT_UNKNOWN)
              .setCodecs(format.sampleMimeType)
              .build());
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
      int skipResult = input.skip(Integer.MAX_VALUE);
      if (skipResult == C.RESULT_END_OF_INPUT) {
        return RESULT_END_OF_INPUT;
      }
      return RESULT_CONTINUE;
    }

    @Override
    public void seek(long position, long timeUs) {}

    @Override
    public void release() {}
  }

  private static MediaSource.Factory newInstance(
      Class<? extends MediaSource.Factory> clazz, DataSource.Factory dataSourceFactory) {
    try {
      return clazz.getConstructor(DataSource.Factory.class).newInstance(dataSourceFactory);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static MediaSource.Factory newInstance(Class<? extends MediaSource.Factory> clazz) {
    try {
      return clazz.getConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}

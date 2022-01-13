/*
 * Copyright 2021 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.demo.session

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

class LoggingMediaSourceFactory(
    val delegate: MediaSource.Factory,
) : MediaSource.Factory {
    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider?): MediaSource.Factory =
        delegate.setDrmSessionManagerProvider(drmSessionManagerProvider)

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy?): MediaSource.Factory =
        delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)

    override fun getSupportedTypes(): IntArray = delegate.getSupportedTypes()

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        return delegate.createMediaSource(mediaItem).also {
            println("MediaSource: $it")
        }
    }
}
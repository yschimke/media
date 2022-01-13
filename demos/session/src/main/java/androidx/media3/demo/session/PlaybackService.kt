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
package androidx.media3.demo.session

import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.TaskStackBuilder
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import okhttp3.OkHttp
import okhttp3.OkHttpClient

class PlaybackService : MediaLibraryService() {
  private lateinit var player: ExoPlayer
  private lateinit var mediaLibrarySession: MediaLibrarySession
  private val librarySessionCallback = CustomMediaLibrarySessionCallback()

  companion object {
    private const val SEARCH_QUERY_PREFIX_COMPAT = "androidx://media3-session/playFromSearch"
    private const val SEARCH_QUERY_PREFIX = "androidx://media3-session/setMediaUri"
  }

  private inner class CustomMediaLibrarySessionCallback :
    MediaLibrarySession.MediaLibrarySessionCallback {
    override fun onGetLibraryRoot(
      session: MediaLibrarySession,
      browser: MediaSession.ControllerInfo,
      params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
      return Futures.immediateFuture(LibraryResult.ofItem(MediaItemTree.getRootItem(), params))
    }

    override fun onGetItem(
      session: MediaLibrarySession,
      browser: MediaSession.ControllerInfo,
      mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
      val item =
        MediaItemTree.getItem(mediaId)
          ?: return Futures.immediateFuture(
            LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
          )
      return Futures.immediateFuture(LibraryResult.ofItem(item, /* params= */ null))
    }

    override fun onGetChildren(
      session: MediaLibrarySession,
      browser: MediaSession.ControllerInfo,
      parentId: String,
      page: Int,
      pageSize: Int,
      params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
      val children =
        MediaItemTree.getChildren(parentId)
          ?: return Futures.immediateFuture(
            LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
          )

      return Futures.immediateFuture(LibraryResult.ofItemList(children, params))
    }

    private fun setMediaItemFromSearchQuery(query: String) {
      // Only accept query with pattern "play [Title]" or "[Title]"
      // Where [Title]: must be exactly matched
      // If no media with exact name found, play a random media instead
      val mediaTitle =
        if (query.startsWith("play ", ignoreCase = true)) {
          query.drop(5)
        } else {
          query
        }

      val item = MediaItemTree.getItemFromTitle(mediaTitle) ?: MediaItemTree.getRandomItem()
      player.setMediaItem(item)
      player.prepare()
    }

    override fun onSetMediaUri(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
      uri: Uri,
      extras: Bundle
    ): Int {

      if (uri.toString().startsWith(SEARCH_QUERY_PREFIX) ||
          uri.toString().startsWith(SEARCH_QUERY_PREFIX_COMPAT)
      ) {
        var searchQuery =
          uri.getQueryParameter("query") ?: return SessionResult.RESULT_ERROR_NOT_SUPPORTED
        setMediaItemFromSearchQuery(searchQuery)

        return SessionResult.RESULT_SUCCESS
      } else {
        return SessionResult.RESULT_ERROR_NOT_SUPPORTED
      }
    }
  }

  private class CustomMediaItemFiller : MediaSession.MediaItemFiller {
    override fun fillInLocalConfiguration(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
      mediaItem: MediaItem
    ): MediaItem {
      val item = MediaItemTree.getItem(mediaItem.mediaId)
      return if (item != null)
        item
      else
        mediaItem
                .buildUpon()
                .setUri(mediaItem.mediaMetadata.mediaUri)
                .build()
    }
  }

  override fun onCreate() {
    super.onCreate()
    initializeSessionAndPlayer()
  }

  override fun onDestroy() {
    player.release()
    mediaLibrarySession.release()
    super.onDestroy()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
    return mediaLibrarySession
  }

  private fun initializeSessionAndPlayer() {
    val client = OkHttpClient.Builder()
            .addInterceptor {
              println("Request: $it")
              it.proceed(it.request()).also {
                println("Response: $it")
              }
            }
            // java.net.UnknownServiceException: CLEARTEXT communication to as-hls-ww-live.akamaized.net not permitted by network security policy
            .followSslRedirects(false)
            .addInterceptor {
              it.proceed(it.request().newBuilder()
                      // use a BBC Radio web stream for testing only
                      .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:94.0) Gecko/20100101 Firefox/94.0")
                      .url(it.request().url.newBuilder().scheme("https").build())
                      .build())
            }
            .build()

    val dataSourceFactory = OkHttpDataSource.Factory(client)

    player =
      ExoPlayer.Builder(this)
        .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
        .setMediaSourceFactory(LoggingMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory)))
        .build()
    MediaItemTree.initialize(assets)

    val parentScreenIntent = Intent(this, MainActivity::class.java)
    val intent = Intent(this, PlayerActivity::class.java)

    val pendingIntent =
      TaskStackBuilder.create(this).run {
        addNextIntent(parentScreenIntent)
        addNextIntent(intent)

        val immutableFlag = if (Build.VERSION.SDK_INT >= 23) FLAG_IMMUTABLE else 0
        getPendingIntent(0, immutableFlag or FLAG_UPDATE_CURRENT)
      }

    mediaLibrarySession =
      MediaLibrarySession.Builder(this, player, librarySessionCallback)
        .setMediaItemFiller(CustomMediaItemFiller())
        .setSessionActivity(pendingIntent)
        .build()
  }
}

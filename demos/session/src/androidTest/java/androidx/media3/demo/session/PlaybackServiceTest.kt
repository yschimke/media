/*
 * Copyright 2022 Google Inc. All rights reserved.
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

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.demo.session.TestData.BBCRadio1
import androidx.media3.demo.session.TestData.anfieldIndexPodcast
import androidx.media3.demo.session.TestData.milkJawn
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.annotation.UiThreadTest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    private lateinit var context: Context
    private lateinit var browserFuture: ListenableFuture<MediaBrowser>

    @Before
    @UiThreadTest
    fun init() {
        context = ApplicationProvider.getApplicationContext()
        browserFuture = MediaBrowser.Builder(
            context,
            SessionToken(
                context,
                ComponentName(context, PlaybackService::class.java)
            )
        ).buildAsync()
    }

    @After
    @UiThreadTest
    fun cleanup() {
        MediaBrowser.releaseFuture(browserFuture)
    }

    @Test
    fun testConnectingToMediaBrowser() {
        runTest {
            val browser = browserFuture.await()

            withContext(Dispatchers.Main) {
                assertThat(browser.currentMediaItem).isNull()
            }
        }
    }

    @Test
    fun testEnabledActions() {
        runTest {
            val browser = browserFuture.await()

            withContext(Dispatchers.Main) {
                browser.setMediaItems(
                    listOf(
                        milkJawn.toMediaItem(),
                        anfieldIndexPodcast.toMediaItem()
                    )
                )

                // allow for async operations
                delay(1000)

                val currentMediaItem = browser.currentMediaItem
                assertThat(currentMediaItem?.mediaMetadata?.displayTitle).isEqualToIgnoringCase(milkJawn.title)

                val availableCommands = browser.availableCommands
                assertThat(availableCommands).matches(
                    { !it.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) },
                    "can't skip to previous item"
                )
                assertThat(browser.availableCommands).matches(
                    { it.contains(Player.COMMAND_PLAY_PAUSE) },
                    "can play pause"
                )
                assertThat(browser.availableCommands).matches(
                    { it.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) },
                    "can skip to next item"
                )
            }
        }
    }

    @Test
    fun testPlayCausesNotification() {
        runTest {
            val browser = browserFuture.await()

            withContext(Dispatchers.Main) {
                browser.setMediaItem(
                        milkJawn.toMediaItem(),
//                        BBCRadio1.toMediaItem(),
                )
                browser.prepare()
                browser.play()

                // delay to start
                delay(5000)

                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val activeNotifications = notificationManager.activeNotifications.toList()

                assertThat(activeNotifications).isNotEmpty

                val mediaNotifications = activeNotifications.filter { it.packageName == context.packageName }

                assertThat(activeNotifications).hasSize(1)
                val playbackNotification = mediaNotifications.first()
                val notification = playbackNotification.notification

                assertThat(playbackNotification.isOngoing).isTrue()
                assertThat(notification.category).isEqualTo(NotificationCompat.CATEGORY_TRANSPORT)
                // Media3 MediaNotificationHandler
                assertThat(notification.channelId).isEqualTo("default_channel_id")
                assertThat(notification.visibility).isEqualTo(Notification.VISIBILITY_PUBLIC)

                // allow for async operations
                delay(30000)

                assertThat(browser.isPlaying).isTrue()
                assertThat(browser.currentPosition).isGreaterThan(2000)
            }
        }
    }
}
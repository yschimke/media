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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.audio.AudioProcessor;
import androidx.media3.exoplayer.audio.AudioProcessor.AudioFormat;
import com.google.common.collect.ImmutableBiMap;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Thrown when a non-locally recoverable transformation failure occurs. */
@UnstableApi
public final class TransformationException extends Exception {

  /**
   * Codes that identify causes of {@link Transformer} errors.
   *
   * <p>This list of errors may be extended in future versions. The underlying values may also
   * change, so it is best to avoid relying on them directly without using the constants.
   */
  // TODO(b/209469847): Update the javadoc once the underlying values are fixed.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef(
      open = true,
      value = {
        ERROR_CODE_UNSPECIFIED,
        ERROR_CODE_FAILED_RUNTIME_CHECK,
        ERROR_CODE_IO_UNSPECIFIED,
        ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        ERROR_CODE_IO_BAD_HTTP_STATUS,
        ERROR_CODE_IO_FILE_NOT_FOUND,
        ERROR_CODE_IO_NO_PERMISSION,
        ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        ERROR_CODE_DECODER_INIT_FAILED,
        ERROR_CODE_DECODING_FAILED,
        ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        ERROR_CODE_ENCODER_INIT_FAILED,
        ERROR_CODE_ENCODING_FAILED,
        ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
        ERROR_CODE_GL_INIT_FAILED,
        ERROR_CODE_GL_PROCESSING_FAILED,
      })
  public @interface ErrorCode {}

  // Miscellaneous errors (1xxx).

  /** Caused by an error whose cause could not be identified. */
  public static final int ERROR_CODE_UNSPECIFIED = 1000;
  /**
   * Caused by a failed runtime check.
   *
   * <p>This can happen when transformer reaches an invalid state.
   */
  public static final int ERROR_CODE_FAILED_RUNTIME_CHECK = 1001;

  // Input/Output errors (2xxx).

  /** Caused by an Input/Output error which could not be identified. */
  public static final int ERROR_CODE_IO_UNSPECIFIED = 2000;
  /**
   * Caused by a network connection failure.
   *
   * <p>The following is a non-exhaustive list of possible reasons:
   *
   * <ul>
   *   <li>There is no network connectivity.
   *   <li>The URL's domain is misspelled or does not exist.
   *   <li>The target host is unreachable.
   *   <li>The server unexpectedly closes the connection.
   * </ul>
   */
  public static final int ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 2001;
  /** Caused by a network timeout, meaning the server is taking too long to fulfill a request. */
  public static final int ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002;
  /**
   * Caused by a server returning a resource with an invalid "Content-Type" HTTP header value.
   *
   * <p>For example, this can happen when the player is expecting a piece of media, but the server
   * returns a paywall HTML page, with content type "text/html".
   */
  public static final int ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE = 2003;
  /** Caused by an HTTP server returning an unexpected HTTP response status code. */
  public static final int ERROR_CODE_IO_BAD_HTTP_STATUS = 2004;
  /** Caused by a non-existent file. */
  public static final int ERROR_CODE_IO_FILE_NOT_FOUND = 2005;
  /**
   * Caused by lack of permission to perform an IO operation. For example, lack of permission to
   * access internet or external storage.
   */
  public static final int ERROR_CODE_IO_NO_PERMISSION = 2006;
  /**
   * Caused by the player trying to access cleartext HTTP traffic (meaning http:// rather than
   * https://) when the app's Network Security Configuration does not permit it.
   */
  public static final int ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED = 2007;
  /** Caused by reading data out of the data bound. */
  public static final int ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE = 2008;

  // Decoding errors (3xxx).

  /** Caused by a decoder initialization failure. */
  public static final int ERROR_CODE_DECODER_INIT_FAILED = 3001;
  /** Caused by a failure while trying to decode media samples. */
  public static final int ERROR_CODE_DECODING_FAILED = 3002;
  /** Caused by trying to decode content whose format is not supported. */
  public static final int ERROR_CODE_DECODING_FORMAT_UNSUPPORTED = 3003;

  // Encoding errors (4xxx).

  /** Caused by an encoder initialization failure. */
  public static final int ERROR_CODE_ENCODER_INIT_FAILED = 4001;
  /** Caused by a failure while trying to encode media samples. */
  public static final int ERROR_CODE_ENCODING_FAILED = 4002;
  /** Caused by requesting to encode content in a format that is not supported by the device. */
  public static final int ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED = 4003;

  // Video editing errors (5xxx).

  /** Caused by a GL initialization failure. */
  public static final int ERROR_CODE_GL_INIT_FAILED = 5001;
  /** Caused by a failure while using or releasing a GL program. */
  public static final int ERROR_CODE_GL_PROCESSING_FAILED = 5002;

  // Muxing errors (6xxx).

  /**
   * Caused by an output sample MIME type inferred from the input not being supported by the muxer.
   *
   * <p>Use {@link TransformationRequest.Builder#setAudioMimeType(String)} or {@link
   * TransformationRequest.Builder#setVideoMimeType(String)} to transcode to a supported MIME type.
   */
  public static final int ERROR_CODE_MUXER_SAMPLE_MIME_TYPE_UNSUPPORTED = 6001;

  private static final ImmutableBiMap<String, @ErrorCode Integer> NAME_TO_ERROR_CODE =
      new ImmutableBiMap.Builder<String, @ErrorCode Integer>()
          .put("ERROR_CODE_FAILED_RUNTIME_CHECK", ERROR_CODE_FAILED_RUNTIME_CHECK)
          .put("ERROR_CODE_IO_UNSPECIFIED", ERROR_CODE_IO_UNSPECIFIED)
          .put("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
          .put("ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT", ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
          .put("ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE", ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE)
          .put("ERROR_CODE_IO_BAD_HTTP_STATUS", ERROR_CODE_IO_BAD_HTTP_STATUS)
          .put("ERROR_CODE_IO_FILE_NOT_FOUND", ERROR_CODE_IO_FILE_NOT_FOUND)
          .put("ERROR_CODE_IO_NO_PERMISSION", ERROR_CODE_IO_NO_PERMISSION)
          .put("ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED", ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED)
          .put("ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE", ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
          .put("ERROR_CODE_DECODER_INIT_FAILED", ERROR_CODE_DECODER_INIT_FAILED)
          .put("ERROR_CODE_DECODING_FAILED", ERROR_CODE_DECODING_FAILED)
          .put("ERROR_CODE_DECODING_FORMAT_UNSUPPORTED", ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
          .put("ERROR_CODE_ENCODER_INIT_FAILED", ERROR_CODE_ENCODER_INIT_FAILED)
          .put("ERROR_CODE_ENCODING_FAILED", ERROR_CODE_ENCODING_FAILED)
          .put("ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED", ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED)
          .put("ERROR_CODE_GL_INIT_FAILED", ERROR_CODE_GL_INIT_FAILED)
          .put("ERROR_CODE_GL_PROCESSING_FAILED", ERROR_CODE_GL_PROCESSING_FAILED)
          .put(
              "ERROR_CODE_MUXER_SAMPLE_MIME_TYPE_UNSUPPORTED",
              ERROR_CODE_MUXER_SAMPLE_MIME_TYPE_UNSUPPORTED)
          .buildOrThrow();

  /** Returns the {@code errorCode} for a given name. */
  private static @ErrorCode int getErrorCodeForName(String errorCodeName) {
    return NAME_TO_ERROR_CODE.getOrDefault(errorCodeName, ERROR_CODE_UNSPECIFIED);
  }

  /** Returns the name of a given {@code errorCode}. */
  public static String getErrorCodeName(@ErrorCode int errorCode) {
    return NAME_TO_ERROR_CODE.inverse().getOrDefault(errorCode, "invalid error code");
  }

  /**
   * Equivalent to {@link TransformationException#getErrorCodeName(int)
   * TransformationException.getErrorCodeName(this.errorCode)}.
   */
  public final String getErrorCodeName() {
    return getErrorCodeName(errorCode);
  }

  /**
   * Creates an instance for a decoder or encoder related exception.
   *
   * @param cause The cause of the failure.
   * @param componentName The name of the component used, e.g. 'VideoEncoder'.
   * @param format The {@link Format} used for the decoder/encoder.
   * @param errorCode See {@link #errorCode}.
   * @return The created instance.
   */
  public static TransformationException createForCodec(
      Throwable cause, String componentName, Format format, int errorCode) {
    return new TransformationException(
        componentName + " error, format = " + format, cause, errorCode);
  }

  /**
   * Creates an instance for an {@link AudioProcessor} related exception.
   *
   * @param cause The cause of the failure.
   * @param componentName The name of the {@link AudioProcessor} used.
   * @param audioFormat The {@link AudioFormat} used.
   * @param errorCode See {@link #errorCode}.
   * @return The created instance.
   */
  public static TransformationException createForAudioProcessor(
      Throwable cause, String componentName, AudioFormat audioFormat, int errorCode) {
    return new TransformationException(
        componentName + " error, audio_format = " + audioFormat, cause, errorCode);
  }

  /**
   * Creates an instance for a {@link FrameEditor} related exception.
   *
   * @param cause The cause of the failure.
   * @param errorCode See {@link #errorCode}.
   * @return The created instance.
   */
  /* package */ static TransformationException createForFrameEditor(
      Throwable cause, int errorCode) {
    return new TransformationException("FrameEditor error", cause, errorCode);
  }

  /**
   * Creates an instance for a muxer related exception.
   *
   * @param cause The cause of the failure.
   * @param errorCode See {@link #errorCode}.
   * @return The created instance.
   */
  /* package */ static TransformationException createForMuxer(Throwable cause, int errorCode) {
    return new TransformationException("Muxer error", cause, errorCode);
  }

  /**
   * Creates an instance for an unexpected exception.
   *
   * <p>If the exception is a runtime exception, error code {@link #ERROR_CODE_FAILED_RUNTIME_CHECK}
   * is used. Otherwise, the created instance has error code {@link #ERROR_CODE_UNSPECIFIED}.
   *
   * @param cause The cause of the failure.
   * @return The created instance.
   */
  public static TransformationException createForUnexpected(Exception cause) {
    if (cause instanceof RuntimeException) {
      return new TransformationException(
          "Unexpected runtime error", cause, ERROR_CODE_FAILED_RUNTIME_CHECK);
    }
    return new TransformationException("Unexpected error", cause, ERROR_CODE_UNSPECIFIED);
  }

  /**
   * Creates an instance for a {@link PlaybackException}.
   *
   * <p>If there is a corresponding {@link TransformationException.ErrorCode} for the {@link
   * PlaybackException.ErrorCode}, this error code and the same message are used for the created
   * instance. Otherwise, this is equivalent to {@link #createForUnexpected(Exception)}.
   */
  /* package */ static TransformationException createForPlaybackException(
      PlaybackException exception) {
    @ErrorCode int errorCode = getErrorCodeForName(exception.getErrorCodeName());
    return errorCode == ERROR_CODE_UNSPECIFIED
        ? createForUnexpected(exception)
        : new TransformationException(exception.getMessage(), exception, errorCode);
  }

  /** An error code which identifies the cause of the transformation failure. */
  public final @ErrorCode int errorCode;

  /** The value of {@link SystemClock#elapsedRealtime()} when this exception was created. */
  public final long timestampMs;

  /**
   * Creates an instance.
   *
   * @param message See {@link #getMessage()}.
   * @param cause See {@link #getCause()}.
   * @param errorCode A number which identifies the cause of the error. May be one of the {@link
   *     ErrorCode ErrorCodes}.
   */
  private TransformationException(
      @Nullable String message, @Nullable Throwable cause, @ErrorCode int errorCode) {
    super(message, cause);
    this.errorCode = errorCode;
    this.timestampMs = Clock.DEFAULT.elapsedRealtime();
  }

  /**
   * Returns whether the error data associated to this exception equals the error data associated to
   * {@code other}.
   *
   * <p>Note that this method does not compare the exceptions' stack traces.
   */
  public boolean errorInfoEquals(@Nullable TransformationException other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    @Nullable Throwable thisCause = getCause();
    @Nullable Throwable thatCause = other.getCause();
    if (thisCause != null && thatCause != null) {
      if (!Util.areEqual(thisCause.getMessage(), thatCause.getMessage())) {
        return false;
      }
      if (!Util.areEqual(thisCause.getClass(), thatCause.getClass())) {
        return false;
      }
    } else if (thisCause != null || thatCause != null) {
      return false;
    }
    return errorCode == other.errorCode
        && Util.areEqual(getMessage(), other.getMessage())
        && timestampMs == other.timestampMs;
  }
}

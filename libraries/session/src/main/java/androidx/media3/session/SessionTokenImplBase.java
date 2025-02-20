/*
 * Copyright 2019 The Android Open Source Project
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
package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotEmpty;
import static androidx.media3.common.util.Assertions.checkNotNull;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.core.app.BundleCompat;
import androidx.media3.common.util.Util;
import com.google.common.base.Objects;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/* package */ final class SessionTokenImplBase implements SessionToken.SessionTokenImpl {

  private final int uid;

  @SessionToken.TokenType private final int type;

  private final int version;

  private final String packageName;

  private final String serviceName;

  @Nullable private final IBinder iSession;

  @Nullable private final ComponentName componentName;

  private final Bundle extras;

  public SessionTokenImplBase(ComponentName serviceComponent, int uid, int type) {
    componentName = checkNotNull(serviceComponent);
    packageName = serviceComponent.getPackageName();
    serviceName = serviceComponent.getClassName();
    this.uid = uid;
    this.type = type;
    version = 0;
    iSession = null;
    extras = Bundle.EMPTY;
  }

  public SessionTokenImplBase(
      int uid,
      int type,
      int version,
      String packageName,
      IMediaSession iSession,
      Bundle tokenExtras) {
    this.uid = uid;
    this.type = type;
    this.version = version;
    this.packageName = packageName;
    serviceName = "";
    componentName = null;
    this.iSession = iSession.asBinder();
    extras = checkNotNull(tokenExtras);
  }

  private SessionTokenImplBase(
      int uid,
      int type,
      int version,
      String packageName,
      String serviceName,
      @Nullable ComponentName componentName,
      @Nullable IBinder iSession,
      Bundle extras) {
    this.uid = uid;
    this.type = type;
    this.version = version;
    this.packageName = packageName;
    this.serviceName = serviceName;
    this.componentName = componentName;
    this.iSession = iSession;
    this.extras = extras;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(type, uid, packageName, serviceName);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof SessionTokenImplBase)) {
      return false;
    }
    SessionTokenImplBase other = (SessionTokenImplBase) obj;
    return uid == other.uid
        && TextUtils.equals(packageName, other.packageName)
        && TextUtils.equals(serviceName, other.serviceName)
        && type == other.type
        && Util.areEqual(iSession, other.iSession);
  }

  @Override
  public String toString() {
    return "SessionToken {pkg="
        + packageName
        + " type="
        + type
        + " version="
        + version
        + " service="
        + serviceName
        + " IMediaSession="
        + iSession
        + " extras="
        + extras
        + "}";
  }

  @Override
  public boolean isLegacySession() {
    return false;
  }

  @Override
  public int getUid() {
    return uid;
  }

  @Override
  public String getPackageName() {
    return packageName;
  }

  @Override
  public String getServiceName() {
    return serviceName;
  }

  @Override
  @Nullable
  public ComponentName getComponentName() {
    return componentName;
  }

  @Override
  @SessionToken.TokenType
  public int getType() {
    return type;
  }

  @Override
  public int getSessionVersion() {
    return version;
  }

  @Override
  public Bundle getExtras() {
    return new Bundle(extras);
  }

  @Override
  @Nullable
  public Object getBinder() {
    return iSession;
  }

  // Bundleable implementation.

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    FIELD_UID,
    FIELD_TYPE,
    FIELD_VERSION,
    FIELD_PACKAGE_NAME,
    FIELD_SERVICE_NAME,
    FIELD_ISESSION,
    FIELD_COMPONENT_NAME,
    FIELD_EXTRAS
  })
  private @interface FieldNumber {}

  private static final int FIELD_UID = 0;
  private static final int FIELD_TYPE = 1;
  private static final int FIELD_VERSION = 2;
  private static final int FIELD_PACKAGE_NAME = 3;
  private static final int FIELD_SERVICE_NAME = 4;
  private static final int FIELD_COMPONENT_NAME = 5;
  private static final int FIELD_ISESSION = 6;
  private static final int FIELD_EXTRAS = 7;

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(keyForField(FIELD_UID), uid);
    bundle.putInt(keyForField(FIELD_TYPE), type);
    bundle.putInt(keyForField(FIELD_VERSION), version);
    bundle.putString(keyForField(FIELD_PACKAGE_NAME), packageName);
    bundle.putString(keyForField(FIELD_SERVICE_NAME), serviceName);
    BundleCompat.putBinder(bundle, keyForField(FIELD_ISESSION), iSession);
    bundle.putParcelable(keyForField(FIELD_COMPONENT_NAME), componentName);
    bundle.putBundle(keyForField(FIELD_EXTRAS), extras);
    return bundle;
  }

  /** Object that can restore {@link SessionTokenImplBase} from a {@link Bundle}. */
  public static final Creator<SessionTokenImplBase> CREATOR = SessionTokenImplBase::fromBundle;

  private static SessionTokenImplBase fromBundle(Bundle bundle) {
    checkArgument(bundle.containsKey(keyForField(FIELD_UID)), "uid should be set.");
    int uid = bundle.getInt(keyForField(FIELD_UID));
    checkArgument(bundle.containsKey(keyForField(FIELD_TYPE)), "type should be set.");
    int type = bundle.getInt(keyForField(FIELD_TYPE));
    int version = bundle.getInt(keyForField(FIELD_VERSION), /* defaultValue= */ 0);
    String packageName =
        checkNotEmpty(
            bundle.getString(keyForField(FIELD_PACKAGE_NAME)), "package name should be set.");
    String serviceName = bundle.getString(keyForField(FIELD_SERVICE_NAME), /* defaultValue= */ "");
    @Nullable IBinder iSession = BundleCompat.getBinder(bundle, keyForField(FIELD_ISESSION));
    @Nullable ComponentName componentName = bundle.getParcelable(keyForField(FIELD_COMPONENT_NAME));
    @Nullable Bundle extras = bundle.getBundle(keyForField(FIELD_EXTRAS));
    return new SessionTokenImplBase(
        uid,
        type,
        version,
        packageName,
        serviceName,
        componentName,
        iSession,
        extras == null ? Bundle.EMPTY : extras);
  }

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}

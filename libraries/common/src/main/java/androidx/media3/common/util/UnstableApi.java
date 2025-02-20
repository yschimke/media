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
package androidx.media3.common.util;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import androidx.annotation.RequiresOptIn;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Signifies that a public API (class, method or field) is subject to incompatible changes, or even
 * removal, in a future release.
 *
 * <p>The presence of this annotation implies nothing about the quality or performance of the API in
 * question, only the fact that it is not "API-frozen."
 *
 * <p>This library follows <a href="https://semver.org/">semantic versioning</a> and the stable API
 * forms the 'public' API for the purposes of the versioning rules. Therefore an API bearing this
 * annotation is exempt from any compatibility guarantees implied by the semantic versioning rules.
 *
 * <p>It is generally safe for applications to depend on unstable APIs, at the cost of some extra
 * work during upgrades. However it is generally inadvisable for libraries (which get included on
 * users' CLASSPATHs, outside the library developers' control) to do so.
 *
 * <h2>Opting in to use unstable APIs</h2>
 *
 * <p>By default usages of APIs annotated with this annotation generate lint errors in Gradle and
 * Android Studio, in order to alert developers to the risk of breaking changes.
 *
 * <p>Individual usage sites can be opted-in to suppress the lint error by using the {@link
 * androidx.annotation.OptIn} annotation: {@code @androidx.annotation.OptIn(markerClass =
 * androidx.media3.common.util.UnstableApi.class)}.
 *
 * <p>Whole projects can be opted-in by suppressing the specific lint error in their <a
 * href="https://developer.android.com/studio/write/lint#pref">{@code lint.xml} file</a>:
 *
 * <pre>{@code
 * <?xml version="1.0" encoding="utf-8"?>
 * <lint>
 *   <issue id="UnsafeOptInUsageError">
 *     <ignore
 *         regexp='\(markerClass = androidx\.media3\.common\.util\.UnstableApi\.class\)' />
 *   </issue>
 * </lint>
 * }</pre>
 */
@Documented
@Retention(CLASS)
@Target({TYPE, METHOD, CONSTRUCTOR, FIELD})
@UnstableApi
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
public @interface UnstableApi {}

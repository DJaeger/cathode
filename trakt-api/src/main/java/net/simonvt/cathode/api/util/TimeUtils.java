/*
 * Copyright (C) 2014 Simon Vig Therkildsen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.simonvt.cathode.api.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class TimeUtils {

  private TimeUtils() {
  }

  public static long getMillis(String iso) {
    if (iso != null) {
      DateFormat dateFormat;

      final int length = iso.length();

      if (length <= 20) {
        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
      } else {
        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
      }

      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

      try {
        return dateFormat.parse(iso).getTime();
      } catch (ParseException e) {
        throw new IllegalArgumentException(e);
      }
    }

    return 0L;
  }

  public static String getIsoTime() {
    return getIsoTime(System.currentTimeMillis());
  }

  public static String getIsoTime(long millis) {
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    return dateFormat.format(new Date(millis));
  }
}

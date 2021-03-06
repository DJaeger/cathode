/*
 * Copyright (C) 2016 Simon Vig Therkildsen
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

package net.simonvt.cathode.api.enumeration;

public class Enums<T extends Enum<T>> {

  private T[] enums;

  public static <T extends Enum<T>> Enums<T> of(T... enums) {
    return new Enums<>(enums);
  }

  private Enums(T[] enums) {
    this.enums = enums;
  }

  @Override public String toString() {
    StringBuilder builder = null;

    for (Object e : enums) {
      if (builder == null) {
        builder = new StringBuilder();
      } else {
        builder.append(",");
      }

      builder.append(e.toString());
    }

    return builder.toString();
  }
}

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

package net.simonvt.cathode.api.entity;

import net.simonvt.cathode.api.enumeration.Action;
import net.simonvt.cathode.api.enumeration.ItemType;

public class Watching {

  IsoTime expiresAt;

  IsoTime startedAt;

  Action action;

  ItemType type;

  Show show;

  Episode episode;

  Movie movie;

  public IsoTime getExpiresAt() {
    return expiresAt;
  }

  public IsoTime getStartedAt() {
    return startedAt;
  }

  public Action getAction() {
    return action;
  }

  public ItemType getType() {
    return type;
  }

  public Show getShow() {
    return show;
  }

  public Episode getEpisode() {
    return episode;
  }

  public Movie getMovie() {
    return movie;
  }
}

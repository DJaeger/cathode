/*
 * Copyright (C) 2013 Simon Vig Therkildsen
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
package net.simonvt.cathode.remote.action.movies;

import javax.inject.Inject;
import net.simonvt.cathode.api.service.RecommendationsService;
import net.simonvt.cathode.remote.CallJob;
import net.simonvt.cathode.remote.Flags;
import okhttp3.ResponseBody;
import retrofit2.Call;

public class DismissMovieRecommendation extends CallJob<ResponseBody> {

  @Inject transient RecommendationsService recommendationsService;

  private long traktId;

  public DismissMovieRecommendation(long traktId) {
    super(Flags.REQUIRES_AUTH);
    this.traktId = traktId;
  }

  @Override public String key() {
    return "DismissShowRecommendation" + "&traktId=" + traktId;
  }

  @Override public int getPriority() {
    return PRIORITY_ACTIONS;
  }

  @Override public Call<ResponseBody> getCall() {
    return recommendationsService.dismissMovie(traktId);
  }

  @Override public void handleResponse(ResponseBody response) {
  }
}

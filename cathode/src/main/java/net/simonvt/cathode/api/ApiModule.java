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

package net.simonvt.cathode.api;

import android.content.Context;
import dagger.Module;
import dagger.Provides;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;
import net.simonvt.cathode.BuildConfig;
import net.simonvt.cathode.module.ApiSettings;
import net.simonvt.cathode.module.LoggingInterceptor;
import net.simonvt.cathode.tmdb.TmdbApiKey;
import okhttp3.Interceptor;

@Module(
    complete = false,
    library = true,

    includes = {
        TraktModule.class
    })
public class ApiModule {

  @Provides @Singleton TraktSettings provideTraktSettings(Context context) {
    return ApiSettings.getInstance(context);
  }

  @Provides @Trakt List<Interceptor> provideInterceptors() {
    List<Interceptor> interceptors = new ArrayList<>();
    interceptors.add(new LoggingInterceptor());
    return interceptors;
  }

  @Provides @TmdbApiKey String tmdbApiKey() {
    return BuildConfig.TMDB_API_KEY;
  }
}

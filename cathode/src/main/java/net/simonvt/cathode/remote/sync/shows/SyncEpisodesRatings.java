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

package net.simonvt.cathode.remote.sync.shows;

import android.content.ContentProviderOperation;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.simonvt.cathode.api.entity.RatingItem;
import net.simonvt.cathode.api.service.SyncService;
import net.simonvt.cathode.provider.EpisodeWrapper;
import net.simonvt.cathode.provider.SeasonWrapper;
import net.simonvt.cathode.provider.ShowWrapper;
import net.simonvt.cathode.provider.generated.CathodeProvider;
import net.simonvt.cathode.remote.TraktTask;
import timber.log.Timber;

import static net.simonvt.cathode.provider.DatabaseContract.EpisodeColumns;
import static net.simonvt.cathode.provider.ProviderSchematic.Episodes;

public class SyncEpisodesRatings extends TraktTask {

  @Inject transient SyncService syncService;

  @Override protected void doTask() {
    List<RatingItem> ratings = syncService.getSeasonRatings();

    Cursor episodes = getContentResolver().query(Episodes.EPISODES, new String[] {
        EpisodeColumns.ID,
    }, EpisodeColumns.RATED_AT + ">0", null, null);
    List<Long> episodeIds = new ArrayList<Long>();
    while (episodes.moveToNext()) {
      final long episodeId = episodes.getLong(episodes.getColumnIndex(EpisodeColumns.ID));
      episodeIds.add(episodeId);
    }
    episodes.close();

    ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

    for (RatingItem rating : ratings) {
      final int seasonNumber = rating.getEpisode().getSeason();
      final int episodeNumber = rating.getEpisode().getNumber();

      final long showTraktId = rating.getShow().getIds().getTrakt();
      boolean didShowExist = true;
      long showId = ShowWrapper.getShowId(getContentResolver(), showTraktId);
      if (showId == -1L) {
        didShowExist = false;
        showId = ShowWrapper.createShow(getContentResolver(), showTraktId);
        queueTask(new SyncShowTask(showTraktId));
      }

      boolean didSeasonExist = true;
      long seasonId = SeasonWrapper.getSeasonId(getContentResolver(), showId, seasonNumber);
      if (seasonId == -1L) {
        didSeasonExist = false;
        seasonId = SeasonWrapper.createSeason(getContentResolver(), showId, seasonNumber);
        if (didShowExist) {
          queueTask(new SyncSeasonTask(showTraktId, seasonNumber));
        }
      }

      long episodeId =
          EpisodeWrapper.getEpisodeId(getContentResolver(), showId, seasonNumber, episodeNumber);
      if (episodeId == -1L) {
        episodeId =
            EpisodeWrapper.createEpisode(getContentResolver(), showId, seasonId, episodeNumber);
        if (didSeasonExist) {
          queueTask(new SyncEpisodeTask(showTraktId, seasonNumber, episodeNumber));
        }
      }

      episodeIds.remove(seasonId);

      ContentProviderOperation op = ContentProviderOperation.newUpdate(Episodes.withId(episodeId))
          .withValue(EpisodeColumns.USER_RATING, rating.getRating())
          .withValue(EpisodeColumns.RATED_AT, rating.getRatedAt().getTimeInMillis())
          .build();
      ops.add(op);
    }

    for (Long episodeId : episodeIds) {
      ContentProviderOperation op = ContentProviderOperation.newUpdate(Episodes.withId(episodeId))
          .withValue(EpisodeColumns.USER_RATING, 0)
          .withValue(EpisodeColumns.RATED_AT, 0)
          .build();
      ops.add(op);
    }

    try {
      getContentResolver().applyBatch(CathodeProvider.AUTHORITY, ops);
      postOnSuccess();
    } catch (RemoteException e) {
      Timber.e(e, "Unable to sync season ratings");
      postOnFailure();
    } catch (OperationApplicationException e) {
      Timber.e(e, "Unable to sync season ratings");
      postOnFailure();
    }
  }
}

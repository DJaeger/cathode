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
package net.simonvt.cathode.scheduler;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import javax.inject.Inject;
import net.simonvt.cathode.api.enumeration.ItemType;
import net.simonvt.cathode.api.util.TimeUtils;
import net.simonvt.cathode.jobqueue.Job;
import net.simonvt.cathode.provider.DatabaseContract.EpisodeColumns;
import net.simonvt.cathode.provider.DatabaseContract.ShowColumns;
import net.simonvt.cathode.provider.EpisodeDatabaseHelper;
import net.simonvt.cathode.provider.ProviderSchematic.Episodes;
import net.simonvt.cathode.provider.ProviderSchematic.Shows;
import net.simonvt.cathode.provider.ShowDatabaseHelper;
import net.simonvt.cathode.remote.action.shows.CheckInEpisode;
import net.simonvt.cathode.remote.action.shows.CollectEpisode;
import net.simonvt.cathode.remote.action.shows.RateEpisode;
import net.simonvt.cathode.remote.action.shows.WatchedEpisode;
import net.simonvt.cathode.remote.action.shows.WatchlistEpisode;
import net.simonvt.cathode.remote.sync.SyncWatching;
import net.simonvt.cathode.remote.sync.comments.SyncComments;
import net.simonvt.cathode.remote.sync.shows.SyncSeason;
import net.simonvt.cathode.tmdb.api.show.SyncEpisodeImages;
import net.simonvt.cathode.util.DateUtils;
import net.simonvt.schematic.Cursors;

public class EpisodeTaskScheduler extends BaseTaskScheduler {

  @Inject ShowDatabaseHelper showHelper;
  @Inject EpisodeDatabaseHelper episodeHelper;

  public EpisodeTaskScheduler(Context context) {
    super(context);
  }

  public void sync(final long episodeId, final Job.OnDoneListener onDoneListener) {
    execute(new Runnable() {
      @Override public void run() {
        final long showId = episodeHelper.getShowId(episodeId);
        final int showTmdbId = showHelper.getTmdbId(showId);
        final long traktId = showHelper.getTraktId(showId);
        final int season = episodeHelper.getSeason(episodeId);
        final int episode = episodeHelper.getNumber(episodeId);

        queue(new SyncSeason(traktId, season));
        queue(new SyncEpisodeImages(showTmdbId, season, episode));
        Job syncComments = new SyncComments(ItemType.EPISODE, traktId, season, episode);
        syncComments.registerOnDoneListener(onDoneListener);
        queue(syncComments);

        ContentValues values = new ContentValues();
        values.put(EpisodeColumns.LAST_COMMENT_SYNC, System.currentTimeMillis());
        context.getContentResolver().update(Episodes.withId(episodeId), values, null, null);
      }
    });
  }

  /**
   * Add episodes watched outside of trakt to user library.
   *
   * @param episodeId The database id of the episode.
   * @param watched Whether the episode has been watched.
   */
  public void setWatched(final long episodeId, final boolean watched) {
    execute(new Runnable() {
      @Override public void run() {
        String watchedAt = null;
        long watchedAtMillis = 0L;
        if (watched) {
          watchedAt = TimeUtils.getIsoTime();
          watchedAtMillis = TimeUtils.getMillis(watchedAt);
        }

        Cursor c = episodeHelper.query(episodeId, EpisodeColumns.SHOW_ID, EpisodeColumns.SEASON,
            EpisodeColumns.EPISODE);
        c.moveToFirst();
        final long showId = Cursors.getLong(c, EpisodeColumns.SHOW_ID);
        final long traktId = showHelper.getTraktId(showId);
        final int season = Cursors.getInt(c, EpisodeColumns.SEASON);
        final int number = Cursors.getInt(c, EpisodeColumns.EPISODE);
        c.close();

        episodeHelper.setWatched(showId, episodeId, watched, watchedAtMillis);

        queue(new WatchedEpisode(traktId, season, number, watched, watchedAt));
      }
    });
  }

  public void checkin(final long episodeId, final String message, final boolean facebook,
      final boolean twitter, final boolean tumblr) {
    execute(new Runnable() {
      @Override public void run() {
        Cursor c = context.getContentResolver().query(Episodes.EPISODE_WATCHING, new String[] {
            EpisodeColumns.ID, EpisodeColumns.EXPIRES_AT,
        }, null, null, null);

        final long currentTime = System.currentTimeMillis();
        long expires = 0;
        if (c.moveToFirst()) {
          expires = Cursors.getLong(c, EpisodeColumns.EXPIRES_AT);
        }

        if (c.getCount() == 0 || (expires >= currentTime && expires > 0)) {
          Cursor episode = episodeHelper.query(episodeId, EpisodeColumns.TRAKT_ID);
          episode.moveToFirst();
          final long traktId = Cursors.getLong(episode, EpisodeColumns.TRAKT_ID);
          episode.close();

          final long showId = episodeHelper.getShowId(episodeId);

          Cursor show = context.getContentResolver().query(Shows.withId(showId), new String[] {
              ShowColumns.RUNTIME,
          }, null, null, null);

          show.moveToFirst();
          final int runtime = Cursors.getInt(show, ShowColumns.RUNTIME);
          show.close();

          final long startedAt = currentTime;
          final long expiresAt = startedAt + runtime * DateUtils.MINUTE_IN_MILLIS;

          ContentValues cv = new ContentValues();
          cv.put(EpisodeColumns.CHECKED_IN, true);
          cv.put(EpisodeColumns.STARTED_AT, startedAt);
          cv.put(EpisodeColumns.EXPIRES_AT, expiresAt);
          context.getContentResolver().update(Episodes.withId(episodeId), cv, null, null);

          queue(new CheckInEpisode(traktId, message, facebook, twitter, tumblr));
        }

        c.close();
        queue(new SyncWatching());
      }
    });
  }

  /**
   * Add episodes to user library collection.
   *
   * @param episodeId The database id of the episode.
   */
  public void setIsInCollection(final long episodeId, final boolean inCollection) {
    execute(new Runnable() {
      @Override public void run() {
        String collectedAt = null;
        long collectedAtMillis = 0L;
        if (inCollection) {
          collectedAt = TimeUtils.getIsoTime();
          collectedAtMillis = TimeUtils.getMillis(collectedAt);
        }

        Cursor c = episodeHelper.query(episodeId, EpisodeColumns.SHOW_ID, EpisodeColumns.SEASON,
            EpisodeColumns.EPISODE);
        c.moveToFirst();
        final long showId = Cursors.getLong(c, EpisodeColumns.SHOW_ID);
        final long traktId = showHelper.getTraktId(showId);
        final int season = Cursors.getInt(c, EpisodeColumns.SEASON);
        final int number = Cursors.getInt(c, EpisodeColumns.EPISODE);
        c.close();

        episodeHelper.setInCollection(episodeId, inCollection, collectedAtMillis);

        queue(new CollectEpisode(traktId, season, number, inCollection, collectedAt));
      }
    });
  }

  public void setIsInWatchlist(final long episodeId, final boolean inWatchlist) {
    execute(new Runnable() {
      @Override public void run() {
        String listedAt = null;
        long listeddAtMillis = 0L;
        if (inWatchlist) {
          listedAt = TimeUtils.getIsoTime();
          listeddAtMillis = TimeUtils.getMillis(listedAt);
        }

        Cursor c = episodeHelper.query(episodeId, EpisodeColumns.SHOW_ID, EpisodeColumns.SEASON,
            EpisodeColumns.EPISODE);
        c.moveToFirst();
        final long showId = Cursors.getLong(c, EpisodeColumns.SHOW_ID);
        final long traktId = showHelper.getTraktId(showId);
        final int season = Cursors.getInt(c, EpisodeColumns.SEASON);
        final int number = Cursors.getInt(c, EpisodeColumns.EPISODE);
        c.close();

        episodeHelper.setIsInWatchlist(episodeId, inWatchlist, listeddAtMillis);

        queue(new WatchlistEpisode(traktId, season, number, inWatchlist, listedAt));
      }
    });
  }

  /**
   * Rate an episode on trakt. Depending on the user settings, this will also send out social
   * updates to facebook,
   * twitter, and tumblr.
   *
   * @param episodeId The database id of the episode.
   * @param rating A rating betweeo 1 and 10. Use 0 to undo rating.
   */
  public void rate(final long episodeId, final int rating) {
    execute(new Runnable() {
      @Override public void run() {
        String ratedAt = TimeUtils.getIsoTime();
        long ratedAtMillis = TimeUtils.getMillis(ratedAt);

        final long showId = episodeHelper.getShowId(episodeId);
        final long showTraktId = showHelper.getTraktId(showId);
        Cursor c = episodeHelper.query(episodeId, EpisodeColumns.EPISODE, EpisodeColumns.SEASON);

        if (c.moveToFirst()) {
          final int episode = Cursors.getInt(c, EpisodeColumns.EPISODE);
          final int season = Cursors.getInt(c, EpisodeColumns.SEASON);

          ContentValues cv = new ContentValues();
          cv.put(EpisodeColumns.USER_RATING, rating);
          cv.put(EpisodeColumns.RATED_AT, ratedAtMillis);
          context.getContentResolver().update(Episodes.withId(episodeId), cv, null, null);

          queue(new RateEpisode(showTraktId, season, episode, rating, ratedAt));
        }
        c.close();
      }
    });
  }

  public void syncComments(final long episodeId) {
    execute(new Runnable() {
      @Override public void run() {
        final long showId = episodeHelper.getShowId(episodeId);
        final long traktId = showHelper.getTraktId(showId);
        final int season = episodeHelper.getSeason(episodeId);
        final int episode = episodeHelper.getNumber(episodeId);
        queue(new SyncComments(ItemType.EPISODE, traktId, season, episode));

        ContentValues values = new ContentValues();
        values.put(EpisodeColumns.LAST_COMMENT_SYNC, System.currentTimeMillis());
        context.getContentResolver().update(Episodes.withId(episodeId), values, null, null);
      }
    });
  }
}

/*
 * Copyright (C) 2015 Simon Vig Therkildsen
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
import javax.inject.Inject;
import net.simonvt.cathode.api.enumeration.Privacy;
import net.simonvt.cathode.api.service.SyncService;
import net.simonvt.cathode.provider.DatabaseContract;
import net.simonvt.cathode.provider.DatabaseContract.ListItemColumns;
import net.simonvt.cathode.provider.EpisodeDatabaseHelper;
import net.simonvt.cathode.provider.ListWrapper;
import net.simonvt.cathode.provider.MovieDatabaseHelper;
import net.simonvt.cathode.provider.PersonWrapper;
import net.simonvt.cathode.provider.ProviderSchematic;
import net.simonvt.cathode.provider.ProviderSchematic.ListItems;
import net.simonvt.cathode.provider.SeasonDatabaseHelper;
import net.simonvt.cathode.provider.ShowDatabaseHelper;
import net.simonvt.cathode.remote.action.lists.AddEpisode;
import net.simonvt.cathode.remote.action.lists.AddMovie;
import net.simonvt.cathode.remote.action.lists.AddPerson;
import net.simonvt.cathode.remote.action.lists.AddSeason;
import net.simonvt.cathode.remote.action.lists.AddShow;
import net.simonvt.cathode.remote.action.lists.CreateList;
import net.simonvt.cathode.remote.action.lists.DeleteList;
import net.simonvt.cathode.remote.action.lists.RemoveEpisode;
import net.simonvt.cathode.remote.action.lists.RemoveMovie;
import net.simonvt.cathode.remote.action.lists.RemovePerson;
import net.simonvt.cathode.remote.action.lists.RemoveSeason;
import net.simonvt.cathode.remote.action.lists.RemoveShow;
import net.simonvt.cathode.remote.action.lists.UpdateList;

public class ListsTaskScheduler extends BaseTaskScheduler {

  @Inject SyncService syncService;

  @Inject ShowDatabaseHelper showHelper;
  @Inject SeasonDatabaseHelper seasonHelper;
  @Inject EpisodeDatabaseHelper episodeHelper;
  @Inject MovieDatabaseHelper movieHelper;

  public ListsTaskScheduler(Context context) {
    super(context);
  }

  public void createList(final String name, final String description, final Privacy privacy,
      final boolean displayNumbers, final boolean allowComments) {
    execute(new Runnable() {
      @Override public void run() {
        final long listId =
            ListWrapper.createList(context.getContentResolver(), name, description, privacy,
                displayNumbers, allowComments);

        queue(new CreateList(listId, name, description, privacy, displayNumbers, allowComments));
      }
    });
  }

  public void updateList(final long listId, final String name, final String description,
      final Privacy privacy, final boolean displayNumbers, final boolean allowComments) {
    execute(new Runnable() {
      @Override public void run() {
        final long traktId = ListWrapper.getTraktId(context.getContentResolver(), listId);

        ListWrapper.updateList(context.getContentResolver(), listId, name, description, privacy,
            displayNumbers, allowComments);

        queue(new UpdateList(traktId, name, description, privacy, displayNumbers, allowComments));
      }
    });
  }

  public void deleteList(final long listId) {
    execute(new Runnable() {
      @Override public void run() {
        final long traktId = ListWrapper.getTraktId(context.getContentResolver(), listId);

        context.getContentResolver().delete(ListItems.inList(listId), null, null);
        context.getContentResolver().delete(ProviderSchematic.Lists.withId(listId), null, null);

        queue(new DeleteList(traktId));
      }
    });
  }

  public void addItem(final long listId, final int itemType, final long itemId) {
    updateListItem(listId, itemType, itemId, true);
  }

  public void removeItem(final long listId, final int itemType, final long itemId) {
    updateListItem(listId, itemType, itemId, false);
  }

  private void updateListItem(final long listId, final int itemType, final long itemId,
      final boolean add) {
    execute(new Runnable() {
      @Override public void run() {
        final long listTraktId = ListWrapper.getTraktId(context.getContentResolver(), listId);
        if (add) {
          ContentValues values = new ContentValues();
          values.put(ListItemColumns.LIST_ID, listId);
          values.put(ListItemColumns.LISTED_AT, System.currentTimeMillis());
          values.put(ListItemColumns.ITEM_ID, itemId);
          values.put(ListItemColumns.ITEM_TYPE, itemType);

          context.getContentResolver().insert(ListItems.LIST_ITEMS, values);
        } else {
          context.getContentResolver()
              .delete(ListItems.inList(listId),
                  ListItemColumns.ITEM_TYPE + "=? AND " + ListItemColumns.ITEM_ID + "=?",
                  new String[] {
                      String.valueOf(itemType), String.valueOf(itemId),
                  });
        }

        switch (itemType) {
          case DatabaseContract.ItemType.SHOW: {
            final long showTraktId = showHelper.getTraktId(itemId);

            if (add) {
              queue(new AddShow(listTraktId, showTraktId));
            } else {
              queue(new RemoveShow(listTraktId, showTraktId));
            }
            break;
          }

          case DatabaseContract.ItemType.SEASON: {
            final long showId = seasonHelper.getShowId(itemId);
            final long showTraktId = showHelper.getTraktId(showId);
            final int seasonNumber = seasonHelper.getNumber(itemId);

            if (add) {
              queue(new AddSeason(listTraktId, showTraktId, seasonNumber));
            } else {
              queue(new RemoveSeason(listTraktId, showTraktId, seasonNumber));
            }
            break;
          }

          case DatabaseContract.ItemType.EPISODE: {
            final long showId = episodeHelper.getShowId(itemId);
            final long showTraktId = showHelper.getTraktId(showId);
            final int seasonNumber = episodeHelper.getSeason(itemId);
            final int episodeNumber = episodeHelper.getNumber(itemId);

            if (add) {
              queue(new AddEpisode(listTraktId, showTraktId, seasonNumber, episodeNumber));
            } else {
              queue(new RemoveEpisode(listTraktId, showTraktId, seasonNumber, episodeNumber));
            }
            break;
          }

          case DatabaseContract.ItemType.MOVIE: {
            final long movieTraktId = movieHelper.getTraktId(itemId);

            if (add) {
              queue(new AddMovie(listTraktId, movieTraktId));
            } else {
              queue(new RemoveMovie(listTraktId, movieTraktId));
            }
            break;
          }

          case DatabaseContract.ItemType.PERSON: {
            final long personTraktId =
                PersonWrapper.getTraktId(context.getContentResolver(), itemId);

            if (add) {
              queue(new AddPerson(listTraktId, personTraktId));
            } else {
              queue(new RemovePerson(listTraktId, personTraktId));
            }
            break;
          }
        }
      }
    });
  }
}

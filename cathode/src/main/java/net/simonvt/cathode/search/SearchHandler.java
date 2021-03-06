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

package net.simonvt.cathode.search;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.simonvt.cathode.CathodeApp;
import net.simonvt.cathode.api.entity.Movie;
import net.simonvt.cathode.api.entity.SearchResult;
import net.simonvt.cathode.api.entity.Show;
import net.simonvt.cathode.api.enumeration.Enums;
import net.simonvt.cathode.api.enumeration.Extended;
import net.simonvt.cathode.api.enumeration.ItemType;
import net.simonvt.cathode.api.service.SearchService;
import net.simonvt.cathode.images.ImageUri;
import net.simonvt.cathode.provider.DatabaseContract.MovieColumns;
import net.simonvt.cathode.provider.DatabaseContract.ShowColumns;
import net.simonvt.cathode.provider.MovieDatabaseHelper;
import net.simonvt.cathode.provider.ProviderSchematic.Movies;
import net.simonvt.cathode.provider.ProviderSchematic.Shows;
import net.simonvt.cathode.provider.ShowDatabaseHelper;
import net.simonvt.cathode.scheduler.MovieTaskScheduler;
import net.simonvt.cathode.scheduler.ShowTaskScheduler;
import net.simonvt.cathode.images.ImageType;
import net.simonvt.cathode.util.MainHandler;
import net.simonvt.schematic.Cursors;
import retrofit2.Call;
import retrofit2.Response;
import timber.log.Timber;

public class SearchHandler {

  public interface SearchListener {

    void onSearchResult(List<Result> results, boolean localResults);
  }

  private static final int LIMIT = 50;

  @Inject SearchService searchService;

  @Inject ShowTaskScheduler showScheduler;
  @Inject MovieTaskScheduler movieScheduler;

  @Inject ShowDatabaseHelper showHelper;
  @Inject MovieDatabaseHelper movieHelper;

  private final List<WeakReference<SearchListener>> listeners = new ArrayList<>();

  private List<Result> results;

  private boolean localResults;

  private Context context;

  private SearchExecutor executor = new SearchExecutor();

  public SearchHandler(Context context) {
    this.context = context;
    CathodeApp.inject(context, this);
  }

  public void addListener(SearchListener listener) {
    WeakReference<SearchListener> listenerRef = new WeakReference<>(listener);
    listeners.add(listenerRef);

    if (results != null) {
      listener.onSearchResult(results, localResults);
    }
  }

  public void removeListener(SearchListener listener) {
    for (int i = listeners.size() - 1; i >= 0; i--) {
      WeakReference<SearchListener> listenerRef = listeners.get(i);
      SearchListener searchListener = listenerRef.get();
      if (searchListener == null || listener == searchListener) {
        listeners.remove(listenerRef);
      }
    }
  }

  public void clear() {
    executor.cancelRunning();
    results = null;
    localResults = false;
  }

  public void publishResult(final List<Result> results, final boolean localResults) {
    Timber.d("Publishing %d results", results.size());
    SearchHandler.this.results = results;
    SearchHandler.this.localResults = localResults;

    for (int i = listeners.size() - 1; i >= 0; i--) {
      WeakReference<SearchListener> listenerRef = listeners.get(i);
      SearchListener listener = listenerRef.get();

      if (listener == null) {
        listeners.remove(listenerRef);
      } else {
        listener.onSearchResult(results, localResults);
      }
    }
  }

  public void forceSearch(final String query) {
    executor.cancelRunning();
    search(query);
  }

  public void search(final String query) {
    executor.execute(new SearchRunnable(query));
  }

  class SearchRunnable implements Runnable {

    volatile boolean canceled;

    final String query;

    private List<Result> results;

    public SearchRunnable(String query) {
      this.query = query;
    }

    public void cancel() {
      synchronized (this) {
        canceled = true;
      }
    }

    @Override public void run() {
      Enums<ItemType> types = Enums.of(ItemType.SHOW, ItemType.MOVIE);
      try {
        Call<List<SearchResult>> call =
            searchService.search(types, query, Extended.FULL, LIMIT);
        Response<List<SearchResult>> response = call.execute();

        if (response.isSuccessful()) {
          int relevance = 0;
          List<SearchResult> searchResults = response.body();
          final List<Result> results = new ArrayList<>(searchResults.size());

          for (SearchResult searchResult : searchResults) {
            if (searchResult.getType() == ItemType.SHOW) {
              Show show = searchResult.getShow();
              if (!TextUtils.isEmpty(show.getTitle())) {
                final long showId = showHelper.partialUpdate(show);

                String title = show.getTitle();
                String overview = show.getOverview();
                float rating = show.getRating() == null ? 0.0f : show.getRating();

                Result result =
                    new Result(ItemType.SHOW, showId, title, overview, rating, relevance++);
                results.add(result);
              }
            } else if (searchResult.getType() == ItemType.MOVIE) {
              Movie movie = searchResult.getMovie();
              if (!TextUtils.isEmpty(movie.getTitle())) {
                final long movieId = movieHelper.partialUpdate(movie);

                String title = movie.getTitle();
                String overview = movie.getOverview();
                float rating = movie.getRating() == null ? 0.0f : movie.getRating();

                Result result =
                    new Result(ItemType.MOVIE, movieId, title, overview, rating, relevance++);
                results.add(result);
              }
            }
          }

          publish(results, false);
          return;
        }
      } catch (IOException e) {
        Timber.d(e, "Search failed");
      } catch (Throwable t) {
        Timber.e(t, "Search failed");
      }

      int relevance = 0;
      List<Result> results = new ArrayList<>();

      Cursor shows = context.getContentResolver().query(Shows.SHOWS, new String[] {
          ShowColumns.ID, ShowColumns.TITLE, ShowColumns.OVERVIEW, ShowColumns.RATING,
      }, ShowColumns.TITLE + " LIKE ?", new String[] {
          "%" + query + "%",
      }, null);

      while (shows.moveToNext()) {
        final long id = Cursors.getLong(shows, ShowColumns.ID);
        final String title = Cursors.getString(shows, ShowColumns.TITLE);
        final String overview = Cursors.getString(shows, ShowColumns.OVERVIEW);
        final float rating = Cursors.getFloat(shows, ShowColumns.RATING);

        final String poster =
            ImageUri.create(ImageUri.ITEM_SHOW, ImageType.POSTER, id);

        Result result = new Result(ItemType.SHOW, id, title, overview, rating, relevance++);
        results.add(result);
      }

      shows.close();

      Cursor movies = context.getContentResolver().query(Movies.MOVIES, new String[] {
          MovieColumns.ID, MovieColumns.TITLE, MovieColumns.OVERVIEW, MovieColumns.RATING,
      }, MovieColumns.TITLE + " LIKE ?", new String[] {
          "%" + query + "%",
      }, null);

      while (movies.moveToNext()) {
        final long id = Cursors.getLong(movies, MovieColumns.ID);
        final String title = Cursors.getString(movies, MovieColumns.TITLE);
        final String overview = Cursors.getString(movies, MovieColumns.OVERVIEW);
        final float rating = Cursors.getFloat(movies, MovieColumns.RATING);

        final String poster =
            ImageUri.create(ImageUri.ITEM_MOVIE, ImageType.POSTER, id);

        Result result =
            new Result(ItemType.MOVIE, id, title, overview, rating, relevance++);
        results.add(result);
      }

      movies.close();

      publish(results, true);
    }

    private void publish(final List<Result> results, final boolean localResults) {
      MainHandler.post(new Runnable() {
        @Override public void run() {
          synchronized (this) {
            if (!canceled) {
              publishResult(results, localResults);
            }
          }
        }
      });
    }
  }
}

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
package net.simonvt.cathode.ui.shows.collected;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.simonvt.cathode.R;
import net.simonvt.cathode.database.SimpleCursor;
import net.simonvt.cathode.database.SimpleCursorLoader;
import net.simonvt.cathode.jobqueue.Job;
import net.simonvt.cathode.provider.ProviderSchematic.Shows;
import net.simonvt.cathode.remote.sync.shows.SyncShowsCollection;
import net.simonvt.cathode.settings.Settings;
import net.simonvt.cathode.ui.LibraryType;
import net.simonvt.cathode.ui.lists.ListDialog;
import net.simonvt.cathode.ui.shows.ShowsFragment;
import net.simonvt.cathode.ui.shows.ShowsWithNextAdapter;

public class CollectedShowsFragment extends ShowsFragment implements ListDialog.Callback {

  public static final String TAG = "net.simonvt.cathode.ui.shows.collected.ShowsCollectionFragment";

  private static final int LOADER_SHOWS_COLLECTION = 1;

  private enum SortBy {
    TITLE("title", Shows.SORT_TITLE),
    COLLECTED("collected", Shows.SORT_COLLECTED);

    private String key;

    private String sortOrder;

    SortBy(String key, String sortOrder) {
      this.key = key;
      this.sortOrder = sortOrder;
    }

    public String getKey() {
      return key;
    }

    public String getSortOrder() {
      return sortOrder;
    }

    @Override public String toString() {
      return key;
    }

    private static final Map<String, SortBy> STRING_MAPPING = new HashMap<>();

    static {
      for (SortBy via : SortBy.values()) {
        STRING_MAPPING.put(via.toString().toUpperCase(Locale.US), via);
      }
    }

    public static SortBy fromValue(String value) {
      SortBy sortBy = STRING_MAPPING.get(value.toUpperCase(Locale.US));
      if (sortBy == null) {
        sortBy = TITLE;
      }
      return sortBy;
    }
  }

  private static final String DIALOG_SORT =
      "net.simonvt.cathode.ui.fragment.ShowCollectionFragment.sortDialog";

  private SharedPreferences settings;

  private SortBy sortBy;

  @Override public void onCreate(Bundle inState) {
    settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
    sortBy =
        SortBy.fromValue(settings.getString(Settings.Sort.SHOW_COLLECTED, SortBy.TITLE.getKey()));

    super.onCreate(inState);

    setEmptyText(R.string.empty_show_collection);
    setTitle(R.string.title_shows_collection);
  }

  @Override public void createMenu(Toolbar toolbar) {
    super.createMenu(toolbar);
    toolbar.inflateMenu(R.menu.fragment_shows_collected);
  }

  private Job.OnDoneListener onDoneListener = new Job.OnDoneListener() {
    @Override public void onDone(Job job) {
      setRefreshing(false);
    }
  };

  @Override public void onRefresh() {
    Job job = new SyncShowsCollection();
    job.registerOnDoneListener(onDoneListener);
    jobManager.addJob(job);
  }

  @Override public boolean onMenuItemClick(MenuItem item) {
    if (item.getItemId() == R.id.menu_sort) {
      ArrayList<ListDialog.Item> items = new ArrayList<>();
      items.add(new ListDialog.Item(R.id.sort_title, R.string.sort_title));
      items.add(new ListDialog.Item(R.id.sort_collected, R.string.sort_collected));
      ListDialog.newInstance(R.string.action_sort_by, items, this)
          .show(getFragmentManager(), DIALOG_SORT);
      return true;
    }

    return super.onMenuItemClick(item);
  }

  @Override public void onItemSelected(int id) {
    switch (id) {
      case R.id.sort_title:
        if (sortBy != SortBy.TITLE) {
          sortBy = SortBy.TITLE;
          settings.edit().putString(Settings.Sort.SHOW_COLLECTED, SortBy.TITLE.getKey()).apply();
          getLoaderManager().restartLoader(getLoaderId(), null, this);
          scrollToTop = true;
        }
        break;

      case R.id.sort_collected:
        if (sortBy != SortBy.COLLECTED) {
          sortBy = SortBy.COLLECTED;
          settings.edit()
              .putString(Settings.Sort.SHOW_COLLECTED, SortBy.COLLECTED.getKey())
              .apply();
          getLoaderManager().restartLoader(getLoaderId(), null, this);
          scrollToTop = true;
        }
        break;
    }
  }

  @Override protected LibraryType getLibraryType() {
    return LibraryType.COLLECTION;
  }

  @Override protected int getLoaderId() {
    return LOADER_SHOWS_COLLECTION;
  }

  @Override public Loader<SimpleCursor> onCreateLoader(int i, Bundle bundle) {
    final Uri contentUri = Shows.SHOWS_COLLECTION;
    return new SimpleCursorLoader(getActivity(), contentUri, ShowsWithNextAdapter.PROJECTION, null,
        null, sortBy.getSortOrder());
  }
}

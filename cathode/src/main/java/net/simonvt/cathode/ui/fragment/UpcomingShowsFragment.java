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
package net.simonvt.cathode.ui.fragment;

import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.view.MenuItem;
import android.view.View;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.simonvt.cathode.CathodeApp;
import net.simonvt.cathode.R;
import net.simonvt.cathode.database.MutableCursor;
import net.simonvt.cathode.database.MutableCursorLoader;
import net.simonvt.cathode.provider.DatabaseContract.EpisodeColumns;
import net.simonvt.cathode.provider.DatabaseContract.ShowColumns;
import net.simonvt.cathode.provider.ProviderSchematic.Shows;
import net.simonvt.cathode.remote.TraktTaskQueue;
import net.simonvt.cathode.remote.sync.SyncTask;
import net.simonvt.cathode.settings.Settings;
import net.simonvt.cathode.ui.BaseActivity;
import net.simonvt.cathode.ui.LibraryType;
import net.simonvt.cathode.ui.ShowsNavigationListener;
import net.simonvt.cathode.ui.adapter.HeaderSpanLookup;
import net.simonvt.cathode.ui.adapter.ShowSuggestionAdapter;
import net.simonvt.cathode.ui.adapter.SuggestionsAdapter;
import net.simonvt.cathode.ui.adapter.UpcomingAdapter;
import net.simonvt.cathode.ui.dialog.ListDialog;
import net.simonvt.cathode.widget.AnimatorHelper;
import net.simonvt.cathode.widget.SearchView;

public class UpcomingShowsFragment extends ToolbarGridFragment<RecyclerView.ViewHolder>
    implements UpcomingAdapter.OnRemoveListener, ListDialog.Callback,
    LoaderCallbacks<MutableCursor>, UpcomingAdapter.OnItemClickListener {

  private enum SortBy {
    TITLE("title", Shows.SORT_TITLE),
    NEXT_EPISODE("nextEpisode", Shows.SORT_NEXT_EPISODE);

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

    private static final Map<String, SortBy> STRING_MAPPING = new HashMap<String, SortBy>();

    static {
      for (SortBy via : SortBy.values()) {
        STRING_MAPPING.put(via.toString().toUpperCase(), via);
      }
    }

    public static SortBy fromValue(String value) {
      return STRING_MAPPING.get(value.toUpperCase());
    }
  }

  private static final String DIALOG_SORT =
      "net.simonvt.cathode.ui.fragment.UpcomingShowsFragment.sortDialog";

  @Inject TraktTaskQueue queue;

  private SharedPreferences settings;

  private boolean showHidden;

  private SortBy sortBy;

  private ShowsNavigationListener navigationListener;

  private int columnCount;

  private UpcomingAdapter adapter;

  @Override public void onAttach(Activity activity) {
    super.onAttach(activity);
    try {
      navigationListener = (ShowsNavigationListener) activity;
    } catch (ClassCastException e) {
      throw new ClassCastException(activity.toString() + " must implement ShowsNavigationListener");
    }
  }

  @Override public void onCreate(Bundle inState) {
    super.onCreate(inState);
    CathodeApp.inject(getActivity(), this);
    settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
    sortBy =
        SortBy.fromValue(settings.getString(Settings.SORT_SHOW_UPCOMING, SortBy.TITLE.getKey()));

    showHidden = settings.getBoolean(Settings.SHOW_HIDDEN, false);

    getLoaderManager().initLoader(BaseActivity.LOADER_SHOWS_UPCOMING, null, this);

    setEmptyText(R.string.empty_show_upcoming);

    columnCount = getResources().getInteger(R.integer.showsColumns);

    setTitle(R.string.title_shows_upcoming);
  }

  @Override protected int getColumnCount() {
    return columnCount;
  }

  @Override protected GridLayoutManager.SpanSizeLookup getSpanSizeLookup() {
    return new HeaderSpanLookup(ensureAdapter(), columnCount);
  }

  @Override public boolean displaysMenuIcon() {
    return true;
  }

  @Override public void createMenu(Toolbar toolbar) {
    super.createMenu(toolbar);
    toolbar.inflateMenu(R.menu.fragment_shows_upcoming);
    toolbar.getMenu().findItem(R.id.menu_hidden).setChecked(showHidden);

    final MenuItem searchItem = toolbar.getMenu().findItem(R.id.menu_search);
    SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
    searchView.setAdapter(new ShowSuggestionAdapter(searchView.getContext()));

    searchView.setListener(new SearchView.SearchViewListener() {
      @Override public void onTextChanged(String newText) {
      }

      @Override public void onSubmit(String query) {
        navigationListener.searchShow(query);

        MenuItemCompat.collapseActionView(searchItem);
      }

      @Override public void onSuggestionSelected(Object suggestion) {
        SuggestionsAdapter.Suggestion item = (SuggestionsAdapter.Suggestion) suggestion;
        if (item.getId() != null) {
          navigationListener.onDisplayShow(item.getId(), item.getTitle(), LibraryType.WATCHED);
        } else {
          navigationListener.searchShow(item.getTitle());
        }

        MenuItemCompat.collapseActionView(searchItem);
      }
    });
  }

  @Override public boolean onMenuItemClick(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_hidden:
        showHidden = !showHidden;
        settings.edit().putBoolean(Settings.SHOW_HIDDEN, showHidden).apply();
        getLoaderManager().restartLoader(BaseActivity.LOADER_SHOWS_UPCOMING, null,
            UpcomingShowsFragment.this);
        item.setChecked(showHidden);
        return true;

      case R.id.sort_by:
        ArrayList<ListDialog.Item> items = new ArrayList<ListDialog.Item>();
        items.add(new ListDialog.Item(R.id.sort_title, R.string.sort_title));
        items.add(new ListDialog.Item(R.id.sort_next_episode, R.string.sort_next_episode));
        ListDialog.newInstance(R.string.action_sort_by, items, UpcomingShowsFragment.this)
            .show(getFragmentManager(), DIALOG_SORT);
        return true;

      case R.id.menu_refresh:
        queue.add(new SyncTask());
        return true;

      default:
        return super.onMenuItemClick(item);
    }
  }

  @Override public void onShowClicked(View v, int position, long id) {
    Cursor c = ((UpcomingAdapter) getAdapter()).getCursor(position);
    navigationListener.onDisplayShow(id, c.getString(c.getColumnIndex(ShowColumns.TITLE)),
        LibraryType.WATCHED);
  }

  @Override public void onItemSelected(int id) {
    switch (id) {
      case R.id.sort_title:
        sortBy = SortBy.TITLE;
        settings.edit().putString(Settings.SORT_SHOW_UPCOMING, SortBy.TITLE.getKey()).apply();
        getLoaderManager().restartLoader(BaseActivity.LOADER_SHOWS_UPCOMING, null, this);
        break;

      case R.id.sort_next_episode:
        sortBy = SortBy.NEXT_EPISODE;
        settings.edit()
            .putString(Settings.SORT_SHOW_UPCOMING, SortBy.NEXT_EPISODE.getKey())
            .apply();
        getLoaderManager().restartLoader(BaseActivity.LOADER_SHOWS_UPCOMING, null, this);
        break;
    }
  }

  @Override public void onRemove(View view, int position) {
    Loader loader = getLoaderManager().getLoader(BaseActivity.LOADER_SHOWS_UPCOMING);
    MutableCursorLoader cursorLoader = (MutableCursorLoader) loader;
    cursorLoader.throttle(2000);
    //AnimatorHelper.removeView(getRecyclerView(), view, animatorCallback);

    MutableCursor cursor = (MutableCursor) ((UpcomingAdapter) getAdapter()).getCursor(position);
    cursor.remove(cursor.getPosition());
    ((UpcomingAdapter) getAdapter()).notifyChanged();
  }

  private AnimatorHelper.Callback animatorCallback = new AnimatorHelper.Callback() {
    @Override public void removeItem(int position) {
      int correctedPosition = ((UpcomingAdapter) getAdapter()).getCursorPosition(position);
      if (correctedPosition != -1) {
        MutableCursor cursor = (MutableCursor) ((UpcomingAdapter) getAdapter()).getCursor(position);
        cursor.remove(correctedPosition);
      }
    }

    @Override public void onAnimationEnd() {
    }
  };

  private UpcomingAdapter ensureAdapter() {
    if (adapter == null) {
      adapter = new UpcomingAdapter(getActivity(), this, this);
      adapter.addHeader(R.string.header_aired);
      adapter.addHeader(R.string.header_upcoming);
    }

    return adapter;
  }

  protected void setCursor(MutableCursor cursor) {
    UpcomingAdapter adapter = (UpcomingAdapter) getAdapter();
    if (adapter == null) {
      adapter = ensureAdapter();
      setAdapter(adapter);
    }

    final long currentTime = System.currentTimeMillis();

    MutableCursor airedCursor = new MutableCursor(cursor.getColumnNames());
    MutableCursor unairedCursor = new MutableCursor(cursor.getColumnNames());

    final int airedIndex = cursor.getColumnIndex(EpisodeColumns.FIRST_AIRED);

    cursor.moveToPosition(-1);
    while (cursor.moveToNext()) {
      Object[] data = cursor.get();
      final long firstAired = cursor.getLong(airedIndex);
      if (firstAired <= currentTime) {
        airedCursor.add(data);
      } else {
        unairedCursor.add(data);
      }
    }

    // TODO: StaggeredGridAnimator animator = new StaggeredGridAnimator(getGridView());
    adapter.updateCursorForHeader(R.string.header_aired, airedCursor);
    adapter.updateCursorForHeader(R.string.header_upcoming, unairedCursor);
    //animator.animate();
  }

  @Override public Loader<MutableCursor> onCreateLoader(int id, Bundle args) {
    final Uri contentUri = Shows.SHOWS_UPCOMING;
    String where = null;
    if (!showHidden) {
      where = ShowColumns.HIDDEN + "=0";
    }
    MutableCursorLoader cl =
        new MutableCursorLoader(getActivity(), contentUri, UpcomingAdapter.PROJECTION, where, null,
            sortBy.getSortOrder());
    cl.setUpdateThrottle(2 * DateUtils.SECOND_IN_MILLIS);
    return cl;
  }

  @Override public void onLoadFinished(Loader<MutableCursor> loader, MutableCursor data) {
    setCursor(data);
  }

  @Override public void onLoaderReset(Loader<MutableCursor> loader) {

  }
}

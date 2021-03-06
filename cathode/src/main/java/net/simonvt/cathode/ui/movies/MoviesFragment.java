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
package net.simonvt.cathode.ui.movies;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import javax.inject.Inject;
import net.simonvt.cathode.CathodeApp;
import net.simonvt.cathode.R;
import net.simonvt.cathode.database.SimpleCursor;
import net.simonvt.cathode.jobqueue.JobManager;
import net.simonvt.cathode.provider.DatabaseContract.MovieColumns;
import net.simonvt.cathode.ui.LibraryType;
import net.simonvt.cathode.ui.MoviesNavigationListener;
import net.simonvt.cathode.ui.adapter.RecyclerCursorAdapter;
import net.simonvt.cathode.ui.fragment.ToolbarSwipeRefreshRecyclerFragment;
import net.simonvt.cathode.ui.listener.MovieClickListener;
import net.simonvt.schematic.Cursors;

public abstract class MoviesFragment
    extends ToolbarSwipeRefreshRecyclerFragment<MoviesAdapter.ViewHolder>
    implements LoaderManager.LoaderCallbacks<SimpleCursor>, MovieClickListener {

  @Inject protected JobManager jobManager;

  private MoviesNavigationListener navigationListener;

  private Cursor cursor;

  private int columnCount;

  protected boolean scrollToTop;

  @Override public void onAttach(Activity activity) {
    super.onAttach(activity);
    navigationListener = (MoviesNavigationListener) activity;
  }

  @Override public void onCreate(Bundle inState) {
    super.onCreate(inState);
    CathodeApp.inject(getActivity(), this);

    getLoaderManager().initLoader(getLoaderId(), null, this);

    columnCount = getResources().getInteger(R.integer.movieColumns);
  }

  @Override public boolean displaysMenuIcon() {
    return amITopLevel();
  }

  @Override public void createMenu(Toolbar toolbar) {
    super.createMenu(toolbar);
    toolbar.inflateMenu(R.menu.fragment_movies);
  }

  @Override public boolean onMenuItemClick(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_search:
        navigationListener.onSearchClicked();
        return true;

      default:
        return super.onMenuItemClick(item);
    }
  }

  @Override protected int getColumnCount() {
    return columnCount;
  }

  @Override public void onMovieClicked(View v, int position, long id) {
    cursor.moveToPosition(position);
    final String title = Cursors.getString(cursor, MovieColumns.TITLE);
    final String overview = Cursors.getString(cursor, MovieColumns.OVERVIEW);
    navigationListener.onDisplayMovie(id, title, overview);
  }

  protected RecyclerView.Adapter<MoviesAdapter.ViewHolder> getAdapter(Cursor cursor) {
    return new MoviesAdapter(getActivity(), this, cursor, getLibraryType());
  }

  void setCursor(Cursor cursor) {
    this.cursor = cursor;
    if (getAdapter() == null) {
      setAdapter(getAdapter(cursor));
    } else {
      ((RecyclerCursorAdapter) getAdapter()).changeCursor(cursor);
    }

    if (scrollToTop) {
      getRecyclerView().scrollToPosition(0);
      scrollToTop = false;
    }
  }

  protected abstract LibraryType getLibraryType();

  protected abstract int getLoaderId();

  @Override public void onLoadFinished(Loader<SimpleCursor> loader, SimpleCursor data) {
    setCursor(data);
  }

  @Override public void onLoaderReset(Loader<SimpleCursor> loader) {
  }
}

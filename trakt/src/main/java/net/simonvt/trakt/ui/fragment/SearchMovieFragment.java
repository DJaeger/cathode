package net.simonvt.trakt.ui.fragment;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import net.simonvt.trakt.R;
import net.simonvt.trakt.TraktApp;
import net.simonvt.trakt.event.MovieSearchResult;
import net.simonvt.trakt.provider.TraktContract;
import net.simonvt.trakt.provider.TraktDatabase;
import net.simonvt.trakt.ui.MoviesNavigationListener;
import net.simonvt.trakt.ui.adapter.MovieSearchAdapter;
import net.simonvt.trakt.util.LogWrapper;
import net.simonvt.trakt.util.MovieSearchHandler;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.SearchView;

import java.util.List;

import javax.inject.Inject;

public class SearchMovieFragment extends AbsAdapterFragment<MovieSearchAdapter>
        implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "SearchMovieFragment";

    private static final String ARGS_QUERY = "net.simonvt.trakt.ui.SearchMovieFragment.query";

    private static final String STATE_QUERY = "net.simonvt.trakt.ui.SearchMovieFragment.query";

    private static final int LOADER_SEARCH = 300;

    @Inject MovieSearchHandler mSearchHandler;

    @Inject Bus mBus;

    private MovieSearchAdapter mMovieAdapter;

    private List<Long> mSearchMovieIds;

    private String mQuery;

    private MoviesNavigationListener mNavigationListener;

    public static SearchMovieFragment newInstance(String query) {
        SearchMovieFragment f = new SearchMovieFragment();

        Bundle args = new Bundle();
        args.putString(ARGS_QUERY, query);
        f.setArguments(args);

        return f;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mNavigationListener = (MoviesNavigationListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement MoviesNavigationListener");
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        TraktApp.inject(getActivity(), this);
        mBus.register(this);
        setHasOptionsMenu(true);

        if (state == null) {
            Bundle args = getArguments();
            mQuery = args.getString(ARGS_QUERY);
            mSearchHandler.search(mQuery);

        } else {
            mQuery = state.getString(STATE_QUERY);
            if (mSearchMovieIds == null && !mSearchHandler.isSearching()) {
                mSearchHandler.search(mQuery);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_QUERY, mQuery);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list_cards, container, false);
    }

    @Override
    public void onDestroy() {
        mBus.unregister(this);
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_search_movie, menu);

        SearchView searchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                LogWrapper.v(TAG, "Query: " + query);
                mSearchHandler.search(query);
                mMovieAdapter = null;
                setAdapter(null);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }

    @Override
    protected void onItemClick(AdapterView l, View v, int position, long id) {
        mNavigationListener.onDisplayMovie(id);
    }

    @Subscribe
    public void onShowSearchEvent(MovieSearchResult result) {
        mSearchMovieIds = result.getMovieIds();
        getLoaderManager().restartLoader(LOADER_SEARCH, null, this);
    }

    private void setCursor(Cursor cursor) {
        if (mMovieAdapter == null) {
            mMovieAdapter = new MovieSearchAdapter(getActivity());
            setAdapter(mMovieAdapter);
        }

        mMovieAdapter.changeCursor(cursor);
    }

    protected static final String[] PROJECTION = new String[] {
            TraktDatabase.Tables.MOVIES + "." + TraktContract.Movies._ID,
            TraktDatabase.Tables.MOVIES + "." + TraktContract.Movies.TITLE,
            TraktDatabase.Tables.MOVIES + "." + TraktContract.Movies.OVERVIEW,
            TraktDatabase.Tables.MOVIES + "." + TraktContract.Movies.POSTER,
    };

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        StringBuilder where = new StringBuilder();
        where.append(TraktContract.Movies._ID).append(" in (");
        final int showCount = mSearchMovieIds.size();
        String[] ids = new String[showCount];
        for (int i = 0; i < showCount; i++) {
            ids[i] = String.valueOf(mSearchMovieIds.get(i));

            where.append("?");
            if (i < showCount - 1) {
                where.append(",");
            }
        }
        where.append(")");

        CursorLoader loader =
                new CursorLoader(getActivity(), TraktContract.Movies.CONTENT_URI, PROJECTION, where.toString(),
                        ids, null);

        return loader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        setCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }
}
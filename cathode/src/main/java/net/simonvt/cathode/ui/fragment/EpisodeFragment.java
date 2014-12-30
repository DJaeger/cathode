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
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import butterknife.ButterKnife;
import butterknife.InjectView;
import com.squareup.otto.Bus;
import javax.inject.Inject;
import net.simonvt.cathode.CathodeApp;
import net.simonvt.cathode.R;
import net.simonvt.cathode.provider.DatabaseContract.EpisodeColumns;
import net.simonvt.cathode.provider.ProviderSchematic.Episodes;
import net.simonvt.cathode.scheduler.EpisodeTaskScheduler;
import net.simonvt.cathode.scheduler.ShowTaskScheduler;
import net.simonvt.cathode.ui.BaseActivity;
import net.simonvt.cathode.ui.FragmentContract;
import net.simonvt.cathode.ui.NavigationClickListener;
import net.simonvt.cathode.ui.dialog.AboutDialog;
import net.simonvt.cathode.ui.dialog.CheckInDialog;
import net.simonvt.cathode.ui.dialog.CheckInDialog.Type;
import net.simonvt.cathode.ui.dialog.RatingDialog;
import net.simonvt.cathode.util.DateUtils;
import net.simonvt.cathode.widget.CircularProgressIndicator;
import net.simonvt.cathode.widget.ObservableScrollView;
import net.simonvt.cathode.widget.RemoteImageView;

public class EpisodeFragment extends DialogFragment implements FragmentContract {

  private static final String ARG_EPISODEID =
      "net.simonvt.cathode.ui.fragment.EpisodeFragment.episodeId";
  private static final String ARG_SHOW_TITLE =
      "net.simonvt.cathode.ui.fragment.EpisodeFragment.showTitle";

  private static final String DIALOG_RATING =
      "net.simonvt.cathode.ui.fragment.EpisodeFragment.ratingDialog";

  private static final int STATE_NONE = -1;
  private static final int STATE_PROGRESS_VISIBLE = 0;
  private static final int STATE_CONTENT_VISIBLE = 1;

  @Inject ShowTaskScheduler showScheduler;
  @Inject EpisodeTaskScheduler episodeScheduler;
  @Inject Bus bus;

  @InjectView(R.id.toolbar) Toolbar toolbar;

  @InjectView(R.id.title) TextView title;
  @InjectView(R.id.fanart) RemoteImageView fanart;
  @InjectView(R.id.overview) TextView overview;
  @InjectView(R.id.firstAired) TextView firstAired;

  @InjectView(R.id.rating) CircularProgressIndicator rating;

  @InjectView(R.id.isWatched) View watchedView;
  @InjectView(R.id.inCollection) View inCollectionView;
  @InjectView(R.id.inWatchlist) View inWatchlistView;

  @InjectView(R.id.contentContainer) ObservableScrollView content;

  @InjectView(R.id.progressContainer) View progress;

  private boolean animating;

  private boolean wait;

  private int currentState = STATE_PROGRESS_VISIBLE;
  private int pendingStateChange = STATE_NONE;

  private long episodeId;

  private String episodeTitle;

  private String showTitle;
  private int season = -1;

  private int currentRating;

  private boolean loaded;

  private boolean watched;

  private boolean collected;

  private boolean inWatchlist;

  private boolean watching;

  private boolean checkedIn;

  private boolean isTablet;

  private NavigationClickListener navigationListener;

  public static Bundle getArgs(long episodeId, String showTitle) {
    Bundle args = new Bundle();
    args.putLong(ARG_EPISODEID, episodeId);
    args.putString(ARG_SHOW_TITLE, showTitle);
    return args;
  }

  @Override public void onAttach(Activity activity) {
    super.onAttach(activity);
    try {
      navigationListener = (NavigationClickListener) activity;
    } catch (ClassCastException e) {
      throw new ClassCastException(activity.toString() + " must implement NavigationClickListener");
    }
  }

  @Override public void onCreate(Bundle inState) {
    super.onCreate(inState);
    CathodeApp.inject(getActivity(), this);

    isTablet = getResources().getBoolean(R.bool.isTablet);

    Bundle args = getArguments();
    episodeId = args.getLong(ARG_EPISODEID);
    showTitle = args.getString(ARG_SHOW_TITLE);
    getLoaderManager().initLoader(BaseActivity.LOADER_EPISODE, null, episodeCallbacks);

    if (getShowsDialog()) {
      setStyle(DialogFragment.STYLE_NO_TITLE, 0);
    }
  }

  private void updateTitle() {
    if (toolbar != null && !isTablet) {
      toolbar.setTitle(getTitle());
      toolbar.setSubtitle(getSubtitle());
    }
  }

  public long getEpisodeId() {
    return episodeId;
  }

  public String getTitle() {
    return showTitle;
  }

  public String getSubtitle() {
    return season == -1 ? null : getString(R.string.season_x, season);
  }

  @Override public boolean onBackPressed() {
    return false;
  }

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle inState) {
    return inflater.inflate(R.layout.fragment_episode, container, false);
  }

  @Override public void onViewCreated(View view, Bundle inState) {
    super.onViewCreated(view, inState);
    ButterKnife.inject(this, view);

    if (!isTablet) {
      toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
    }

    toolbar.setNavigationOnClickListener(navigationClickListener);
    createMenu(toolbar);
    toolbar.setOnMenuItemClickListener(menuClickListener);
    updateTitle();

    wait = true;
    view.getViewTreeObserver()
        .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override public void onGlobalLayout() {
            getView().getViewTreeObserver().removeOnGlobalLayoutListener(this);
            wait = false;

            if (currentState == STATE_CONTENT_VISIBLE) {
              content.setAlpha(1.0f);
              content.setVisibility(View.VISIBLE);
              progress.setVisibility(View.GONE);
            } else {
              content.setVisibility(View.GONE);
              progress.setAlpha(1.0f);
              progress.setVisibility(View.VISIBLE);
            }

            if (!isTablet
                && getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
              content.setScrollY(fanart.getHeight() / 2);
            }
          }
        });

    rating.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View view) {
        RatingDialog.newInstance(RatingDialog.Type.EPISODE, episodeId, currentRating)
            .show(getFragmentManager(), DIALOG_RATING);
      }
    });

    if (!isTablet) {
      content.setListener(new ObservableScrollView.ScrollListener() {
        @Override public void onScrollChanged(int l, int t) {
          final int offset = (int) (t / 2.0f);
          fanart.setTranslationY(offset);
        }
      });
    }
  }

  private View.OnClickListener navigationClickListener = new View.OnClickListener() {
    @Override public void onClick(View v) {
      navigationListener.onHomeClicked();
    }
  };

  @Override public void onDestroyView() {
    ButterKnife.reset(this);
    super.onDestroyView();
  }

  @Override public void onDestroy() {
    if (getActivity().isFinishing() || isRemoving()) {
      getLoaderManager().destroyLoader(BaseActivity.LOADER_EPISODE);
    }
    super.onDestroy();
  }

  private void createMenu(Toolbar toolbar) {
    Menu menu = toolbar.getMenu();
    menu.clear();

    toolbar.inflateMenu(R.menu.activity_base);

    if (loaded) {
      if (checkedIn) {
        menu.add(0, R.id.action_checkin_cancel, 1, R.string.action_checkin_cancel)
            .setIcon(R.drawable.ic_action_cancel)
            .setShowAsActionFlags(
                isTablet ? MenuItem.SHOW_AS_ACTION_NEVER : MenuItem.SHOW_AS_ACTION_ALWAYS);
      } else if (!watching) {
        menu.add(0, R.id.action_checkin, 2, R.string.action_checkin)
            .setIcon(R.drawable.ic_action_checkin)
            .setShowAsActionFlags(
                isTablet ? MenuItem.SHOW_AS_ACTION_NEVER : MenuItem.SHOW_AS_ACTION_ALWAYS);
      }

      if (watched) {
        menu.add(0, R.id.action_unwatched, 3, R.string.action_unwatched);
      } else {
        menu.add(0, R.id.action_watched, 4, R.string.action_watched);
        if (inWatchlist) {
          menu.add(0, R.id.action_watchlist_remove, 5, R.string.action_watchlist_remove);
        } else {
          menu.add(0, R.id.action_watchlist_add, 6, R.string.action_watchlist_add);
        }
      }

      if (collected) {
        menu.add(0, R.id.action_collection_remove, 7, R.string.action_collection_remove);
      } else {
        menu.add(0, R.id.action_collection_add, 8, R.string.action_collection_add);
      }
    }
  }

  private Toolbar.OnMenuItemClickListener menuClickListener =
      new Toolbar.OnMenuItemClickListener() {
        @Override public boolean onMenuItemClick(MenuItem item) {
          switch (item.getItemId()) {
            case R.id.action_watched:
              episodeScheduler.setWatched(episodeId, true);
              return true;

            case R.id.action_unwatched:
              episodeScheduler.setWatched(episodeId, false);
              return true;

            case R.id.action_checkin:
              CheckInDialog.showDialogIfNecessary(getActivity(), Type.SHOW, episodeTitle,
                  episodeId);
              return true;

            case R.id.action_checkin_cancel:
              showScheduler.cancelCheckin();
              return true;

            case R.id.action_collection_add:
              episodeScheduler.setIsInCollection(episodeId, true);
              return true;

            case R.id.action_collection_remove:
              episodeScheduler.setIsInCollection(episodeId, false);
              return true;

            case R.id.action_watchlist_add:
              episodeScheduler.setIsInWatchlist(episodeId, true);
              return true;

            case R.id.action_watchlist_remove:
              episodeScheduler.setIsInWatchlist(episodeId, false);
              return true;

            case R.id.menu_about:
              new AboutDialog().show(getFragmentManager(), BaseActivity.DIALOG_ABOUT);
              return true;
          }

          return false;
        }
      };

  @Override public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
    Animation animation = null;
    if (nextAnim != 0) {
      animation = AnimationUtils.loadAnimation(getActivity(), nextAnim);
      animation.setAnimationListener(new Animation.AnimationListener() {
        @Override public void onAnimationStart(Animation animation) {
          animating = true;
        }

        @Override public void onAnimationEnd(Animation animation) {
          animating = false;
          if (pendingStateChange != STATE_NONE) {
            changeState(pendingStateChange, true);
            pendingStateChange = STATE_NONE;
          }
        }

        @Override public void onAnimationRepeat(Animation animation) {
        }
      });
    }

    return animation;
  }

  protected void setContentVisible(boolean contentVisible) {
    if (getView() == null) {
      currentState = contentVisible ? STATE_CONTENT_VISIBLE : STATE_PROGRESS_VISIBLE;
      return;
    }

    if (contentVisible) {
      changeState(STATE_CONTENT_VISIBLE, true);
    } else {
      changeState(STATE_PROGRESS_VISIBLE, true);
    }
  }

  private void changeState(final int newState, final boolean animate) {
    if (newState == currentState) {
      return;
    }

    if (animating) {
      pendingStateChange = newState;
      return;
    }

    currentState = newState;

    if (wait || progress == null) {
      return;
    }

    if (newState == STATE_PROGRESS_VISIBLE && content.getVisibility() != View.VISIBLE) {
      return;
    }

    if (newState == STATE_CONTENT_VISIBLE && !animate) {
      content.setVisibility(View.VISIBLE);
      progress.setVisibility(View.GONE);
    } else if (newState == STATE_PROGRESS_VISIBLE && !animate) {
      content.setVisibility(View.GONE);
      progress.setVisibility(View.VISIBLE);
    } else {
      content.setVisibility(View.VISIBLE);
      progress.setVisibility(View.VISIBLE);

      if (newState == STATE_CONTENT_VISIBLE) {
        progress.animate().alpha(0.0f);
        if (content.getAlpha() == 1.0f) content.setAlpha(0.0f);
        content.animate().alpha(1.0f).withEndAction(new Runnable() {
          @Override public void run() {
            if (progress == null) {
              // In case fragment is removed before animation is done
              return;
            }
            progress.setVisibility(View.GONE);
          }
        });
      } else {
        if (progress.getAlpha() == 1.0f) progress.setAlpha(0.0f);
        progress.animate().alpha(1.0f);
        content.animate().alpha(0.0f).withEndAction(new Runnable() {
          @Override public void run() {
            if (progress == null) {
              // In case fragment is removed before animation is done
              return;
            }
            content.setVisibility(View.GONE);
          }
        });
      }
    }
  }

  private void updateEpisodeViews(final Cursor cursor) {
    if (cursor.moveToFirst()) {
      loaded = true;

      episodeTitle = cursor.getString(cursor.getColumnIndex(EpisodeColumns.TITLE));
      title.setText(episodeTitle);
      overview.setText(cursor.getString(cursor.getColumnIndex(EpisodeColumns.OVERVIEW)));
      fanart.setImage(cursor.getString(cursor.getColumnIndex(EpisodeColumns.SCREEN)));
      firstAired.setText(DateUtils.millisToString(getActivity(),
          cursor.getLong(cursor.getColumnIndex(EpisodeColumns.FIRST_AIRED)), true));
      season = cursor.getInt(cursor.getColumnIndex(EpisodeColumns.SEASON));

      watched = cursor.getInt(cursor.getColumnIndex(EpisodeColumns.WATCHED)) == 1;
      collected = cursor.getInt(cursor.getColumnIndex(EpisodeColumns.IN_COLLECTION)) == 1;
      inWatchlist = cursor.getInt(cursor.getColumnIndex(EpisodeColumns.IN_WATCHLIST)) == 1;
      watching = cursor.getInt(cursor.getColumnIndex(EpisodeColumns.WATCHING)) == 1;
      checkedIn = cursor.getInt(cursor.getColumnIndex(EpisodeColumns.CHECKED_IN)) == 1;

      watchedView.setVisibility(watched ? View.VISIBLE : View.GONE);
      inCollectionView.setVisibility(collected ? View.VISIBLE : View.GONE);
      inWatchlistView.setVisibility(inWatchlist ? View.VISIBLE : View.GONE);

      currentRating = cursor.getInt(cursor.getColumnIndex(EpisodeColumns.USER_RATING));
      final float ratingAll = cursor.getFloat(cursor.getColumnIndex(EpisodeColumns.RATING));
      rating.setValue(ratingAll);

      setContentVisible(true);

      createMenu(toolbar);
      updateTitle();
    }
  }

  private static final String[] EPISODE_PROJECTION = new String[] {
      EpisodeColumns.TITLE, EpisodeColumns.SCREEN, EpisodeColumns.OVERVIEW,
      EpisodeColumns.FIRST_AIRED, EpisodeColumns.WATCHED, EpisodeColumns.IN_COLLECTION,
      EpisodeColumns.IN_WATCHLIST, EpisodeColumns.WATCHING, EpisodeColumns.CHECKED_IN,
      EpisodeColumns.USER_RATING, EpisodeColumns.RATING, EpisodeColumns.SEASON,
  };

  private LoaderManager.LoaderCallbacks<Cursor> episodeCallbacks =
      new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override public Loader<Cursor> onCreateLoader(int id, Bundle args) {
          CursorLoader cl =
              new CursorLoader(getActivity(), Episodes.withId(episodeId), EPISODE_PROJECTION, null,
                  null, null);
          cl.setUpdateThrottle(2 * android.text.format.DateUtils.SECOND_IN_MILLIS);
          return cl;
        }

        @Override public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor data) {
          updateEpisodeViews(data);
        }

        @Override public void onLoaderReset(Loader<Cursor> cursorLoader) {
        }
      };
}

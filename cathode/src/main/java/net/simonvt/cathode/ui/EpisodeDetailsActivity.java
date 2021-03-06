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

package net.simonvt.cathode.ui;

import android.content.Intent;
import android.os.Bundle;
import java.util.ArrayList;
import net.simonvt.cathode.R;
import net.simonvt.cathode.ui.show.EpisodeFragment;
import net.simonvt.cathode.ui.show.ShowFragment;
import net.simonvt.cathode.util.FragmentStack.StackEntry;

public class EpisodeDetailsActivity extends NavigationListenerActivity {

  public static final String EXTRA_ID = "net.simonvt.cathode.ui.DetailsActivity.id";
  public static final String EXTRA_SHOW_ID = "net.simonvt.cathode.ui.DetailsActivity.showId";
  public static final String EXTRA_SHOW_TITLE = "net.simonvt.cathode.ui.DetailsActivity.showTitle";
  public static final String EXTRA_SHOW_OVERVIEW =
      "net.simonvt.cathode.ui.DetailsActivity.showOverview";

  private long id;

  private long showId;

  private String showTitle;

  private String showOverview;

  @Override protected void onCreate(Bundle inState) {
    super.onCreate(inState);
    setContentView(R.layout.activity_details);

    Intent intent = getIntent();
    id = intent.getLongExtra(EXTRA_ID, -1L);
    showId = intent.getLongExtra(EXTRA_SHOW_ID, -1L);
    showTitle = intent.getStringExtra(EXTRA_SHOW_TITLE);
    showOverview = intent.getStringExtra(EXTRA_SHOW_OVERVIEW);

    if (inState == null) {
      Bundle args = EpisodeFragment.getArgs(id, showTitle);
      EpisodeFragment f = new EpisodeFragment();
      f.setArguments(args);
      getSupportFragmentManager().beginTransaction()
          .add(R.id.content, f, EpisodeFragment.getTag(id))
          .commitNow();
    }
  }

  @Override public void onHomeClicked() {
    ArrayList<StackEntry> stack = new ArrayList<>();

    StackEntry showEntry = new StackEntry(ShowFragment.class, ShowFragment.getTag(showId),
        ShowFragment.getArgs(showId, showTitle, showOverview, LibraryType.WATCHED));
    stack.add(showEntry);

    Intent i = new Intent(this, HomeActivity.class);
    i.setAction(HomeActivity.ACTION_REPLACE_STACK);
    i.putParcelableArrayListExtra(HomeActivity.EXTRA_STACK_ENTRIES, stack);

    startActivity(i);
    finish();
  }
}

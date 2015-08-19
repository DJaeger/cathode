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

package net.simonvt.cathode.ui;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.support.v7.widget.Toolbar;
import android.view.View;
import net.simonvt.cathode.R;
import net.simonvt.cathode.settings.Accounts;
import net.simonvt.cathode.settings.Permissions;
import net.simonvt.cathode.settings.Settings;
import timber.log.Timber;

public class SettingsActivity extends BaseActivity {

  private static final String FRAGMENT_SETTINGS =
      "net.simonvt.cathode.ui.SettingsActivity.settingsFragment";

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_toolbar);
    if (getFragmentManager().findFragmentByTag(FRAGMENT_SETTINGS) == null) {
      SettingsFragment settings = new SettingsFragment();
      getFragmentManager().beginTransaction()
          .add(R.id.content, settings, FRAGMENT_SETTINGS)
          .commit();
    }

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    toolbar.setTitle(R.string.title_settings);
    toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
    toolbar.setNavigationOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        finish();
      }
    });
  }

  public static class SettingsFragment extends PreferenceFragment {

    private static final int PERMISSION_REQUEST_CALENDAR = 11;

    SharedPreferences settings;

    SwitchPreference syncCalendar;

    @Override public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      settings = PreferenceManager.getDefaultSharedPreferences(getActivity());

      addPreferencesFromResource(R.xml.preferences_settings);

      syncCalendar = (SwitchPreference) findPreference(Settings.CALENDAR_SYNC);
      syncCalendar.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
        @Override public boolean onPreferenceChange(Preference preference, Object newValue) {
          boolean checked = (boolean) newValue;
          syncCalendar.setChecked(checked);

          if (checked && !Permissions.hasCalendarPermission(getActivity())) {
              requestPermissions(new String[] {
                  Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR,
              }, PERMISSION_REQUEST_CALENDAR);
          } else {
            Accounts.requestCalendarSync(getActivity());
          }

          return true;
        }
      });
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
        int[] grantResults) {
      if (requestCode == PERMISSION_REQUEST_CALENDAR) {
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          Timber.d("Calendar permission granted");
          Accounts.requestCalendarSync(getActivity());
        } else {
          Timber.d("Calendar permission not granted");
        }
      }
    }
  }
}

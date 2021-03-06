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

package net.simonvt.cathode.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.view.View;
import net.simonvt.cathode.CathodeApp;
import net.simonvt.cathode.R;
import net.simonvt.cathode.notification.NotificationUtils;
import net.simonvt.cathode.ui.BaseActivity;

public class NotificationSettingsActivity extends BaseActivity {

  private static final String FRAGMENT_SETTINGS =
      "net.simonvt.cathode.settings.SettingsActivity.settingsFragment";

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_toolbar);
    if (getFragmentManager().findFragmentByTag(FRAGMENT_SETTINGS) == null) {
      NotificationSettingsFragment settings = new NotificationSettingsFragment();
      getFragmentManager().beginTransaction()
          .add(R.id.content, settings, FRAGMENT_SETTINGS)
          .commit();
    }

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    toolbar.setTitle(R.string.title_settings);
    toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24dp);
    toolbar.setNavigationOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        finish();
      }
    });
  }

  public static class NotificationSettingsFragment extends PreferenceFragment
      implements NotificationTimeDialog.NotificationTimeSelectedListener {

    private static final String DIALOG_NOTIFIACTION_TIME =
        "net.simonvt.cathode.settings.NotifiactionSettingsActivity.NotificationSettingsFragment.notificationTIme";

    SharedPreferences settings;

    private boolean isTablet;

    @Override public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      CathodeApp.inject(getActivity(), this);
      settings = PreferenceManager.getDefaultSharedPreferences(getActivity());

      isTablet = getResources().getBoolean(R.bool.isTablet);

      addPreferencesFromResource(R.xml.settings_notifications);

      findPreference(Settings.NOTIFICACTIONS_ENABLED).setOnPreferenceChangeListener(
          new Preference.OnPreferenceChangeListener() {
            @Override public boolean onPreferenceChange(Preference preference, Object newValue) {
              scheduleNotificationService();
              return true;
            }
          });

      findPreference(Settings.NOTIFICACTION_TIME).setOnPreferenceClickListener(
          new Preference.OnPreferenceClickListener() {
            @Override public boolean onPreferenceClick(Preference preference) {
              NotificationTime notificationTime = NotificationTime.fromValue(
                  settings.getLong(Settings.NOTIFICACTION_TIME,
                      NotificationTime.HOURS_1.getNotificationTime()));
              NotificationTimeDialog dialog = NotificationTimeDialog.newInstance(notificationTime);
              dialog.setTargetFragment(NotificationSettingsFragment.this, 0);
              dialog.show(getFragmentManager(), DIALOG_NOTIFIACTION_TIME);
              return true;
            }
          });
    }

    private void scheduleNotificationService() {
      NotificationUtils.scheduleNotifications(getActivity(), 0);
    }

    @Override public void onNotificationTimeSelected(NotificationTime value) {
      settings.edit().putLong(Settings.NOTIFICACTION_TIME, value.getNotificationTime()).apply();
      scheduleNotificationService();
    }
  }
}

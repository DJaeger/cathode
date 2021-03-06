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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import javax.inject.Inject;
import net.simonvt.cathode.CathodeApp;
import net.simonvt.cathode.R;
import net.simonvt.cathode.jobqueue.JobManager;
import net.simonvt.cathode.remote.Flags;
import net.simonvt.cathode.remote.LogoutJob;
import net.simonvt.cathode.settings.login.LoginActivity;

public class LogoutDialog extends DialogFragment {

  @Inject JobManager jobManager;

  @Override public Dialog onCreateDialog(Bundle inState) {
    return new AlertDialog.Builder(getActivity()).setTitle(R.string.logout_title)
        .setMessage(R.string.logout_message)
        .setPositiveButton(R.string.logout_button, new DialogInterface.OnClickListener() {
          @Override public void onClick(DialogInterface dialog, int which) {
            final Context context = getActivity().getApplicationContext();
            CathodeApp.inject(context, LogoutDialog.this);

            PreferenceManager.getDefaultSharedPreferences(getActivity()) //
                .edit() //
                .putBoolean(Settings.TRAKT_LOGGED_IN, false) //
                .apply();

            Settings.clearUserSettings(getActivity());
            TraktTimestamps.clear(getActivity());

            jobManager.addJob(new LogoutJob());
            jobManager.removeJobsWithFlag(Flags.REQUIRES_AUTH);

            Intent login = new Intent(getActivity(), LoginActivity.class);
            login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(login);
          }
        })
        .setNegativeButton(R.string.cancel, null)
        .create();
  }
}

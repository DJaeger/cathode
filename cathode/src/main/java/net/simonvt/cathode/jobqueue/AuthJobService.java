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

package net.simonvt.cathode.jobqueue;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.format.DateUtils;
import javax.inject.Inject;
import net.simonvt.cathode.CathodeApp;
import net.simonvt.cathode.event.SyncEvent;
import net.simonvt.cathode.remote.Flags;
import net.simonvt.cathode.util.WakeLock;
import timber.log.Timber;

public class AuthJobService extends Service {

  static final String WAKELOCK_TAG = "net.simonvt.cathode.sync.AuthJobService";

  static final String RETRY_DELAY = "net.simonvt.cathode.sync.AuthJobService.retryDelay";

  private static final int MAX_RETRY_DELAY = 60; // In minutes

  @Inject JobManager jobManager;

  private volatile int retryDelay = -1;

  private JobExecutor executor;

  @Override public void onCreate() {
    super.onCreate();
    Timber.d("AuthJobService started");
    CathodeApp.inject(this);
    SyncEvent.authServiceStarted();

    WakeLock.acquire(this, WAKELOCK_TAG);

    HandlerThread thread = new HandlerThread("AuthJobService");
    thread.start();

    if (jobManager.hasJobs(Flags.REQUIRES_AUTH, 0)) {
      executor = new JobExecutor(jobManager, executorListener, 1, Flags.REQUIRES_AUTH, 0);
    } else {
      stopSelf();
    }
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    if (retryDelay == -1) {
      if (intent != null) {
        int delay = intent.getIntExtra(RETRY_DELAY, -1);
        if (delay != -1) {
          retryDelay = delay;
        }
      }
    }

    return START_STICKY;
  }

  private JobExecutor.JobExecutorListener executorListener = new JobExecutor.JobExecutorListener() {
    @Override public void onQueueEmpty() {
      Timber.d("AuthJobService queue empty");
      stopSelf();
    }

    @Override public void onQueueFailed() {
      Timber.d("AuthJobService queue failed");
      scheduleAlarm();
      stopSelf();
    }
  };

  private void scheduleAlarm() {
    Intent intent = new Intent(AuthJobService.this, AuthJobReceiver.class);
    final int retryDelay = Math.max(1, this.retryDelay);
    final int nextDelay = Math.min(retryDelay * 2, MAX_RETRY_DELAY);
    intent.putExtra(RETRY_DELAY, nextDelay);

    PendingIntent pi = PendingIntent.getBroadcast(AuthJobService.this, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT);

    AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    am.cancel(pi);

    final long runAt = SystemClock.elapsedRealtime() + retryDelay * DateUtils.MINUTE_IN_MILLIS;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && retryDelay < 10) {
      am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, runAt, pi);
    } else {
      am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, runAt, pi);
    }

    Timber.d("Scheduling alarm in %d minutes", retryDelay);
  }

  @Override public void onDestroy() {
    if (executor != null) {
      executor.destroy();
    }

    SyncEvent.authServiceStopped();

    WakeLock.release(this, WAKELOCK_TAG);

    Timber.d("AuthJobService stopped");
    super.onDestroy();
  }

  @Override public IBinder onBind(Intent intent) {
    return null;
  }
}

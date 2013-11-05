package net.simonvt.cathode.remote.sync;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.simonvt.cathode.api.entity.TvShow;
import net.simonvt.cathode.api.service.RecommendationsService;
import net.simonvt.cathode.provider.CathodeContract;
import net.simonvt.cathode.provider.CathodeProvider;
import net.simonvt.cathode.provider.ShowWrapper;
import net.simonvt.cathode.remote.TraktTask;
import retrofit.RetrofitError;

public class SyncShowRecommendations extends TraktTask {

  @Inject transient RecommendationsService recommendationsService;

  @Override protected void doTask() {
    try {
      ContentResolver resolver = service.getContentResolver();

      List<TvShow> shows = recommendationsService.shows();
      List<Long> showIds = new ArrayList<Long>();

      ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
      Cursor c = resolver.query(CathodeContract.Shows.SHOWS_RECOMMENDED, null, null, null, null);
      while (c.moveToNext()) {
        showIds.add(c.getLong(c.getColumnIndex(CathodeContract.Shows._ID)));
      }
      c.close();

      for (int index = 0, count = Math.min(shows.size(), 25); index < count; index++) {
        TvShow show = shows.get(index);
        long showId = ShowWrapper.getShowId(resolver, show);
        if (showId == -1L) {
          queueTask(new SyncShowTask(show.getTvdbId()));
          showId = ShowWrapper.insertShow(resolver, show);
        }

        showIds.remove(showId);

        ContentProviderOperation op =
            ContentProviderOperation.newUpdate(CathodeContract.Shows.buildFromId(showId))
                .withValue(CathodeContract.Shows.RECOMMENDATION_INDEX, index)
                .build();
        ops.add(op);
      }

      for (Long id : showIds) {
        ContentProviderOperation op =
            ContentProviderOperation.newUpdate(CathodeContract.Shows.buildFromId(id))
                .withValue(CathodeContract.Shows.RECOMMENDATION_INDEX, -1)
                .build();
        ops.add(op);
      }

      resolver.applyBatch(CathodeProvider.AUTHORITY, ops);
      postOnSuccess();
    } catch (RetrofitError error) {
      error.printStackTrace();
      postOnFailure();
    } catch (RemoteException e) {
      e.printStackTrace();
      postOnFailure();
    } catch (OperationApplicationException e) {
      e.printStackTrace();
      postOnFailure();
    }
  }
}

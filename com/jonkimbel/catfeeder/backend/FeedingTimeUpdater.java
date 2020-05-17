package com.jonkimbel.catfeeder.backend;

import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.Preferences;
import com.jonkimbel.catfeeder.backend.storage.api.PreferencesStorage;
import com.jonkimbel.catfeeder.proto.CatFeeder.EmbeddedRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FeedingTimeUpdater {
  /**
  * Updates preferences with any new feeding times reported by the embedded client. Returns whether
  * a new feeding time was written to storage.
  */
  public boolean update(EmbeddedRequest request) {
    Preferences.Builder preferencesBuilder = PreferencesStorage.get().toBuilder()
        .setLastPhotonCheckInMsSinceEpoch(System.currentTimeMillis());

    boolean wroteLastFeedingTime = false;
    if (request.hasTimeSinceLastFeedingMs()) {
      List<Long> feedingHistory = new ArrayList<>(
          preferencesBuilder.getFeedingPreferencesBuilder()
              .getLastTenFeedingTimesMsSinceEpochList());
      while (feedingHistory.size() >= 10) {
        feedingHistory.remove(feedingHistory.size() - 1);
      }
      feedingHistory.add(0, Instant.now().toEpochMilli() - request.getTimeSinceLastFeedingMs());

      preferencesBuilder.getFeedingPreferencesBuilder()
          .clearLastTenFeedingTimesMsSinceEpoch()
          .addAllLastTenFeedingTimesMsSinceEpoch(feedingHistory)
          .clearFeedAsap();

      wroteLastFeedingTime = true;
    }

    PreferencesStorage.set(preferencesBuilder.build());

    return wroteLastFeedingTime;
  }
}

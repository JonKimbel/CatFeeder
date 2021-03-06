package com.jonkimbel.catfeeder.backend;

import com.jonkimbel.catfeeder.backend.server.HttpResponse;
import com.jonkimbel.catfeeder.backend.storage.api.PreferencesStorage;
import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.Preferences;
import com.jonkimbel.catfeeder.backend.time.Time;
import com.jonkimbel.catfeeder.proto.CatFeeder;

import java.io.IOException;

public class ProtoBodyRenderer {
  public byte[] render(boolean wroteLastFeedingTime) {
    CatFeeder.EmbeddedResponse.Builder response = CatFeeder.EmbeddedResponse.newBuilder()
        .setDelayUntilNextCheckInMs(Time.getTimeToNextCheckInMs())
        .setLastFeedingTimeConsumed(wroteLastFeedingTime);

    if (PreferencesStorage.get().getFeedingPreferences().getFeedAsap()) {
      response.setDelayUntilNextFeedingMs(0).setScoopsToFeed(1);
    } else {
      response.setDelayUntilNextFeedingMs(Time.getTimeToNextFeedingMs());

      response.setScoopsToFeed(Math.max(
          PreferencesStorage.get().getFeedingPreferences().getNumberOfScoopsPerFeeding(),
          /* min scoops per feeding */ 1));
    }

    return response.build().toByteArray();
  }
}

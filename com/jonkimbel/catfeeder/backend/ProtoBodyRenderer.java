package com.jonkimbel.catfeeder.backend;

import com.jonkimbel.catfeeder.backend.server.HttpResponse;
import com.jonkimbel.catfeeder.backend.storage.api.PreferencesStorage;
import com.jonkimbel.catfeeder.backend.time.Time;
import com.jonkimbel.catfeeder.proto.CatFeeder;

import java.io.IOException;

public class ProtoBodyRenderer {
  public void render(HttpResponse.Builder responseBuilder, boolean wroteLastFeedingTime) {
    CatFeeder.EmbeddedResponse.Builder response = CatFeeder.EmbeddedResponse.newBuilder();

    response.setDelayUntilNextCheckInMs(Time.getTimeToNextCheckInMs());
    Long value = Time.getTimeToNextFeedingMs();
    if (value != null) {
      response.setDelayUntilNextFeedingMs(value);
    }
    response.setLastFeedingTimeConsumed(wroteLastFeedingTime);

    response.setScoopsToFeed(Math.max(
        PreferencesStorage.get().getFeedingPreferences().getNumberOfScoopsPerFeeding(),
        /* min scoops per feeding */ 1));

    responseBuilder.setProtobufBody(response.build().toByteArray());
  }
}

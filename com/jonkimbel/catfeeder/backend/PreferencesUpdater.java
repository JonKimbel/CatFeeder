package com.jonkimbel.catfeeder.backend;

import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass;
import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.Preferences;
import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.FeedingPreferences;
import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.FeedingPreferences.FeedingSchedule;
import com.jonkimbel.catfeeder.backend.storage.api.PreferencesStorage;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

public class PreferencesUpdater {
  public void update(Map<String, String> formKeysAndValues) {
    Preferences.Builder builder = PreferencesStorage.get().toBuilder();

    updateFeedingSchedule(formKeysAndValues.get("feed_schedule"),
        builder.getFeedingPreferencesBuilder());
    updateScoopsPerFeeding(formKeysAndValues.get("number_of_scoops_per_feeding"),
        builder.getFeedingPreferencesBuilder());

    PreferencesStorage.set(builder.build());
  }

  public void feedAsap() {
    Preferences.Builder builder = PreferencesStorage.get().toBuilder();
    builder.getFeedingPreferencesBuilder().setFeedAsap(true);
    PreferencesStorage.set(builder.build());
  }

  private static void updateScoopsPerFeeding(String scoopsPerFeeding,
      FeedingPreferences.Builder builder) {
    Integer numberOfScoopsPerFeeding;
    try {
      numberOfScoopsPerFeeding = Integer.parseInt(scoopsPerFeeding);
    } catch (NumberFormatException e) {
      numberOfScoopsPerFeeding = null;
    }

    if (numberOfScoopsPerFeeding == null
        || numberOfScoopsPerFeeding < /* min scoops per feeding */ 1) {
      return;
    }

    builder.setNumberOfScoopsPerFeeding(numberOfScoopsPerFeeding);
  }

  private static void updateFeedingSchedule(String feedingScheduleArgument,
      FeedingPreferences.Builder builder) {
    if (feedingScheduleArgument == null) {
      return;
    }

    boolean scheduleRecognized = false;
    scheduleRecognized |= applyFeedingSchedule(
        builder,
        feedingScheduleArgument,
        /* candidateScheduleString = */ "half_calories",
        /* candidateSchedule = */ FeedingSchedule.FEED_HALF_CALORIES);
    scheduleRecognized |= applyFeedingSchedule(
        builder,
        feedingScheduleArgument,
        /* candidateScheduleString = */ "all_calories",
        /* candidateSchedule = */ FeedingSchedule.FEED_ALL_CALORIES);
    scheduleRecognized |= applyFeedingSchedule(
        builder,
        feedingScheduleArgument,
        /* candidateScheduleString = */ "never",
        /* candidateSchedule = */ FeedingSchedule.NEVER_AUTO_FEED);

    if (!scheduleRecognized) {
      System.err.printf("%s - Unrecognized feeding schedule:%s\n",
          new Date(), feedingScheduleArgument);
    }
  }

  private static boolean applyFeedingSchedule(FeedingPreferences.Builder builder,
      String scheduleArgument, String candidateScheduleString, FeedingSchedule candidateSchedule) {
    if (scheduleArgument.equals(candidateScheduleString)) {
      if (builder.getFeedingSchedule() != candidateSchedule) {
        builder.setFeedingSchedule(candidateSchedule)
            .setLastFeedingScheduleChangeMsSinceEpoch(Instant.now().toEpochMilli());
      }
      return true;
    }
    return false;
  }
}

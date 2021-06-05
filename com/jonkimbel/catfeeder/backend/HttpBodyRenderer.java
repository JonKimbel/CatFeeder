package com.jonkimbel.catfeeder.backend;

import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.FeedingPreferences;
import com.jonkimbel.catfeeder.backend.server.HttpResponse;
import com.jonkimbel.catfeeder.backend.storage.api.PreferencesStorage;
import com.jonkimbel.catfeeder.backend.template.Template;
import com.jonkimbel.catfeeder.backend.template.TemplateFiller;
import com.jonkimbel.catfeeder.backend.time.Time;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class HttpBodyRenderer {
  private static final int MAX_FEEDINGS_TO_DISPLAY = 10;

  public String render(Template template) throws IOException {
    switch (template) {
      case INDEX:
        return renderIndex(Template.INDEX.toString());
      case LOGIN:
        return Template.LOGIN.toString();
      default:
        System.err.printf("%s - unrecognized template: %s\n", new Date(), template);
        return "";
    }
  }

  private static String renderIndex(String templateHtml) throws IOException {
    // TODO [V2]: Implement support for user-defined feeding times - just take a # of scoops and a
    //            time per feeding, pace the feedings out automatically.

    Map<String, String> templateValues = new HashMap<>();
    FeedingPreferences feedingPrefs = PreferencesStorage.get().getFeedingPreferences();

    // Show recent feedings.
    int feedingsCount = feedingPrefs.getLastTenFeedingTimesMsSinceEpochCount();
    if (feedingsCount == 0) {
      templateValues.put("recent_feedings_display", "none");
    } else {
      templateValues.put("recent_feedings_display", "inherit");
      StringBuilder feedingTimeString = new StringBuilder();
      for (int i = 0; i < Math.min(feedingsCount, MAX_FEEDINGS_TO_DISPLAY); i++) {
        feedingTimeString.append(
            Time.format(Time.fromUnixMillis(feedingPrefs.getLastTenFeedingTimesMsSinceEpoch(i))));
        feedingTimeString.append("<br>");
      }
      templateValues.put("recent_feedings", feedingTimeString.toString());
    }

    templateValues.put("next_feeding", Time.format(Time.getTimeOfNextFeedingForDisplay()));
    int scoopsPerFeeding = Math.max(
        feedingPrefs.getNumberOfScoopsPerFeeding(),
        /* min scoops per feeding */ 1);
    templateValues.put("number_of_scoops_per_feeding", String.valueOf(scoopsPerFeeding));

    templateValues.put("check_in_warning_display",
        Time.wasLastCheckInRecent() ? "none" : "inherit");
    templateValues.put("check_in_warning_time", Time.format(Time.getTimeOfLastCheckIn()));

    if (feedingPrefs.getFeedingSchedule() ==
        FeedingPreferences.FeedingSchedule.FEED_HALF_CALORIES) {
      templateValues.put("feed_schedule_half_calories", "checked");
    } else if (feedingPrefs.getFeedingSchedule() ==
        FeedingPreferences.FeedingSchedule.FEED_ALL_CALORIES) {
      templateValues.put("feed_schedule_all_calories", "checked");
    } else {
      templateValues.put("feed_schedule_never", "checked");
    }

    return new TemplateFiller(templateHtml).fill(templateValues);
  }
}

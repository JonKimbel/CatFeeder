package com.jonkimbel.catfeeder.backend.time;

import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass;
import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.FeedingPreferences;
import com.jonkimbel.catfeeder.backend.storage.api.PreferencesStorage;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Time {
  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);
  private static final ZoneId DEVICE_TIME_ZONE = ZoneId.of("US/Pacific");
  private static final int MORNING_TIME_MINUTES_INTO_DAY = 6 * 60; // 6AM.
  private static final int EVENING_TIME_MINUTES_INTO_DAY = 18 * 60; // 6PM.
  private static final long INTERVAL_BETWEEN_CHECK_INS_MS = 10 * 60 * 1000; // 10 min.
  private static final int MAX_PHOTON_TIME_SKEW_S = 30;

  // TODO [V3]: Implement support for user-defined device timezones.
  // TODO [V1]: Implement support for user-defined feeding times.

  public static String format(@Nullable ZonedDateTime time) {
    if (time == null) {
      return "never";
    }
    return TIME_FORMATTER.format(time);
  }

  public static boolean wasLastCheckInRecent() {
    @Nullable ZonedDateTime lastCheckInDate = getTimeOfLastCheckIn();
    if (lastCheckInDate == null) {
      return false;
    }

    ZonedDateTime fifteenMinutesAgo = ZonedDateTime.now(DEVICE_TIME_ZONE).minusMinutes(15);
    return lastCheckInDate.isAfter(fifteenMinutesAgo);
  }

  @Nullable
  public static ZonedDateTime getTimeOfLastCheckIn() {
    if (PreferencesStorage.get().hasLastPhotonCheckInMsSinceEpoch()) {
      return ZonedDateTime.ofInstant(
          Instant.ofEpochMilli(PreferencesStorage.get().getLastPhotonCheckInMsSinceEpoch()),
          DEVICE_TIME_ZONE);
    }
    return null;
  }

  @Nullable
  public static ZonedDateTime getTimeOfLastFeedingScheduleChange() {
    FeedingPreferences feedingPrefs = PreferencesStorage.get().getFeedingPreferences();
    if (feedingPrefs.hasLastFeedingScheduleChangeMsSinceEpoch()) {
      return ZonedDateTime.ofInstant(
          Instant.ofEpochMilli(feedingPrefs.getLastFeedingScheduleChangeMsSinceEpoch()),
          DEVICE_TIME_ZONE);
    }
    return null;
  }

  @Nullable
  public static ZonedDateTime getTimeOfLastFeeding() {
    FeedingPreferences feedingPrefs = PreferencesStorage.get().getFeedingPreferences();
    if (feedingPrefs.getLastTenFeedingTimesMsSinceEpochCount() > 0) {
      return ZonedDateTime.ofInstant(
          Instant.ofEpochMilli(feedingPrefs.getLastTenFeedingTimesMsSinceEpoch(0)),
          DEVICE_TIME_ZONE);
    }
    return null;
  }

  @Nullable
  public static ZonedDateTime getTimeOfNextFeeding() {
    // TODO [V1]: add override feeding time when the user taps "feed now", clear this override once
    // the photon has fed it.

    ZonedDateTime now = ZonedDateTime.now(DEVICE_TIME_ZONE);
    ZonedDateTime midnightThisMorning = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
    ZonedDateTime morningToday = midnightThisMorning.plusMinutes(MORNING_TIME_MINUTES_INTO_DAY);
    ZonedDateTime eveningToday = midnightThisMorning.plusMinutes(EVENING_TIME_MINUTES_INTO_DAY);
    ZonedDateTime morningTomorrow = morningToday.plusDays(1);

    // All of the feeding times we might need to feed. This list must remain in chronological order.
    List<ZonedDateTime> upcomingFeedingTimes = new ArrayList<ZonedDateTime>();

    switch (PreferencesStorage.get().getFeedingPreferences().getFeedingSchedule()) {
      case AUTO_FEED_IN_MORNINGS:
        upcomingFeedingTimes.add(morningToday);
        upcomingFeedingTimes.add(morningTomorrow);
        break;
      case AUTO_FEED_IN_MORNINGS_AND_EVENINGS:
        upcomingFeedingTimes.add(morningToday);
        upcomingFeedingTimes.add(eveningToday);
        upcomingFeedingTimes.add(morningTomorrow);
        break;
      default:
        break;
    }

    // TODO [V2]: ensure we don't miss a feeding or over-feed during daylight savings time changes.

    @Nullable ZonedDateTime timeOfLastFeeding = getTimeOfLastFeeding();
    @Nullable ZonedDateTime timeOfLastCheckIn = getTimeOfLastCheckIn();
    @Nullable ZonedDateTime timeOfLastFeedingScheduleChange = getTimeOfLastFeedingScheduleChange();

    for (ZonedDateTime upcomingFeedingTime : upcomingFeedingTimes) {
      if (upcomingFeedingTime.isAfter(now)) {
        if (timeOfLastFeeding != null
            && timeOfLastFeeding.isAfter(
                upcomingFeedingTime.minusSeconds(MAX_PHOTON_TIME_SKEW_S))) {
          System.out.printf("%s - Skipped feeding at %s because the device *just* fed at %s",
              new Date(), upcomingFeedingTime, timeOfLastFeeding);
          continue;
        }

        // This is the first feeding the device hasn't done yet, feed it.
        return upcomingFeedingTime;

      } else if (timeOfLastFeeding != null
          && upcomingFeedingTime.isAfter(now.minusSeconds(MAX_PHOTON_TIME_SKEW_S))
          && timeOfLastFeeding.isBefore(now.minusSeconds(2 * MAX_PHOTON_TIME_SKEW_S))) {
        System.out.printf("%s - Feeding immediately because the device last fed at %s and we " +
            "were supposed to feed at %s", new Date(), timeOfLastFeeding, upcomingFeedingTime);
        return now;

      } else if (timeOfLastFeeding != null && timeOfLastCheckIn != null
          && timeOfLastFeedingScheduleChange != null
          && timeOfLastCheckIn.isBefore(timeOfLastFeedingScheduleChange)
          && timeOfLastFeeding.isBefore(upcomingFeedingTime.minusSeconds(MAX_PHOTON_TIME_SKEW_S))) {
        System.out.printf("%s - Feeding immediately because the device last fed at %s and last " +
            "checked in at %s, but the schedule changed at %s and we were supposed to feed at %s",
            new Date(), timeOfLastFeeding, timeOfLastCheckIn, timeOfLastFeedingScheduleChange,
            upcomingFeedingTime);
        return now;
      }
    }
    return null;
  }

  public static long getTimeToNextCheckInMs() {
    return INTERVAL_BETWEEN_CHECK_INS_MS;
  }

  @Nullable
  public static Long getTimeToNextFeedingMs() {
    @Nullable ZonedDateTime nextFeedingTime = Time.getTimeOfNextFeeding();
    if (nextFeedingTime == null) {
      return null;
    }
    return Math.max(0, nextFeedingTime.toInstant().toEpochMilli() - Instant.now().toEpochMilli());
  }
}

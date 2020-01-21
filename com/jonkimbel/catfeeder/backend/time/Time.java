package com.jonkimbel.catfeeder.backend.time;

import com.jonkimbel.catfeeder.backend.storage.api.PreferencesStorage;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

public class Time {
  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);
  private static final ZoneId DEVICE_TIME_ZONE = ZoneId.of("US/Pacific");
  private static final int MORNING_TIME_MINUTES_INTO_DAY = 6 * 60 - 15; // 5:45AM.
  private static final int EVENING_TIME_MINUTES_INTO_DAY = 18 * 60; // 6PM.

  // TODO [CLEANUP]: Implement support for user-defined device timezones.
  // TODO [CLEANUP]: Implement support for user-defined feeding times.

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
  public static ZonedDateTime getTimeOfLastFeeding() {
    if (PreferencesStorage.get().getFeedingPreferences().hasLastFeedingTimeMsSinceEpoch()) {
      return ZonedDateTime.ofInstant(
          Instant.ofEpochMilli(
              PreferencesStorage.get().getFeedingPreferences().getLastFeedingTimeMsSinceEpoch()),
          DEVICE_TIME_ZONE);
    }
    return null;
  }

  @Nullable
  public static ZonedDateTime getTimeOfNextFeeding() {
    ZonedDateTime now = ZonedDateTime.now(DEVICE_TIME_ZONE);
    ZonedDateTime morningToday = getTimeAtMinutesIntoToday(MORNING_TIME_MINUTES_INTO_DAY);
    ZonedDateTime eveningToday = getTimeAtMinutesIntoToday(EVENING_TIME_MINUTES_INTO_DAY);
    ZonedDateTime morningTomorrow = morningToday.plusDays(1);

    switch (PreferencesStorage.get().getFeedingPreferences().getFeedingSchedule()) {
      case AUTO_FEED_IN_MORNINGS:
        if (morningToday.isAfter(now)) {
          return morningToday;
        } else {
          return morningTomorrow;
        }
      case AUTO_FEED_IN_MORNINGS_AND_EVENINGS:
        if (morningToday.isAfter(now)) {
          return morningToday;
        } else if (eveningToday.isAfter(now)) {
          return eveningToday;
        }
        return morningTomorrow;
      default:
        return null;
    }
  }

  private static ZonedDateTime getTimeAtMinutesIntoToday(int minutes_into_day) {
    return ZonedDateTime.now(DEVICE_TIME_ZONE)
        .withHour(0).withMinute(0).withSecond(0).withNano(0)
        .plusMinutes(minutes_into_day);
  }
}

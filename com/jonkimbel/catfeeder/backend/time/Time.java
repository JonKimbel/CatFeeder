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
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class Time {
  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);
  private static final ZoneId DEVICE_TIME_ZONE = ZoneId.of("US/Pacific");
  // TODO [V2]: maybe make the UI ask the user to type in a number of calories to feed per day along
  // with the number of calories the cat needs in one day.
  //
  // 366 cal/cup, each feeding is 1/8 cup (46 cal),
  // cat gets ~63 cal wet food/day, cat needs 180+ cal/day.
  private static final int[] HALF_CALORIE_FEEDING_TIMES_MINUTES_INTO_DAY = new int[] { // 92 cal.
      6 * 60,  // 6 AM.
      18 * 60};  // 6 PM.
  private static final int[] REMAINING_CALORIE_FEEDING_TIMES_MINUTES_INTO_DAY = new int[] { // 92 cal.
      6 * 60 + 20,  // 6:20 AM.
      18 * 60 + 10};  // 6:10 PM.
  // Cat is manually feed 60 cal wet food in the evenings.
  private static final long INTERVAL_BETWEEN_CHECK_INS_MS = 10 * 60 * 1000; // 10 min.
  private static final int MAX_PHOTON_TIME_SKEW_S = 30;

  // TODO [V3]: Implement support for user-defined device timezones.

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

  public static ZonedDateTime fromUnixMillis(long unixMillis) {
    return ZonedDateTime.ofInstant(
        Instant.ofEpochMilli(unixMillis),
        DEVICE_TIME_ZONE);
  }

  @Nullable
  public static ZonedDateTime getTimeOfLastCheckIn() {
    if (PreferencesStorage.get().hasLastPhotonCheckInMsSinceEpoch()) {
      return fromUnixMillis(PreferencesStorage.get().getLastPhotonCheckInMsSinceEpoch());
    }
    return null;
  }

  @Nullable
  public static ZonedDateTime getTimeOfLastFeedingScheduleChange() {
    FeedingPreferences feedingPrefs = PreferencesStorage.get().getFeedingPreferences();
    if (feedingPrefs.hasLastFeedingScheduleChangeMsSinceEpoch()) {
      return fromUnixMillis(feedingPrefs.getLastFeedingScheduleChangeMsSinceEpoch());
    }
    return null;
  }

  @Nullable
  public static ZonedDateTime getTimeOfLastFeeding() {
    FeedingPreferences feedingPrefs = PreferencesStorage.get().getFeedingPreferences();
    if (feedingPrefs.getLastTenFeedingTimesMsSinceEpochCount() > 0) {
      return fromUnixMillis(feedingPrefs.getLastTenFeedingTimesMsSinceEpoch(0));
    }
    return null;
  }

  @Nullable
  public static ZonedDateTime getTimeOfNextFeedingForDisplay() {
    // If we're meant to feed ASAP, return the time of the device's next checkin.
    if (PreferencesStorage.get().getFeedingPreferences().getFeedAsap()) {
      @Nullable ZonedDateTime lastCheckIn = getTimeOfLastCheckIn();
      if (lastCheckIn == null) {
        return ZonedDateTime.now(DEVICE_TIME_ZONE);
      }
      return lastCheckIn.plus(INTERVAL_BETWEEN_CHECK_INS_MS, ChronoUnit.MILLIS);
    }

    return getTimeOfNextFeeding();
  }

  @Nullable
  public static ZonedDateTime getTimeOfNextFeeding() {
    ZonedDateTime now = ZonedDateTime.now(DEVICE_TIME_ZONE);

    // Determine all of the feeding times we might need to feed.
    List<ZonedDateTime> upcomingFeedingTimes = new ArrayList<>();
    switch (PreferencesStorage.get().getFeedingPreferences().getFeedingSchedule()) {
      case FEED_HALF_CALORIES:
        addFeedingTimes(upcomingFeedingTimes, HALF_CALORIE_FEEDING_TIMES_MINUTES_INTO_DAY);
        break;
      case FEED_ALL_CALORIES:
        addFeedingTimes(upcomingFeedingTimes, HALF_CALORIE_FEEDING_TIMES_MINUTES_INTO_DAY);
        addFeedingTimes(upcomingFeedingTimes, REMAINING_CALORIE_FEEDING_TIMES_MINUTES_INTO_DAY);
        break;
      default:
        break;
    }

    // TODO [V2]: ensure we don't miss a feeding or over-feed during daylight savings time changes.

    @Nullable ZonedDateTime timeOfLastFeeding = getTimeOfLastFeeding();
    @Nullable ZonedDateTime timeOfLastFeedingScheduleChange = getTimeOfLastFeedingScheduleChange();

    upcomingFeedingTimes.sort((time, otherTime) -> time.compareTo(otherTime));
    for (ZonedDateTime upcomingFeedingTime : upcomingFeedingTimes) {
      if (upcomingFeedingTime.isAfter(now)) {
        if (timeOfLastFeeding != null
            && timeOfLastFeeding.isAfter(
                upcomingFeedingTime.minusSeconds(MAX_PHOTON_TIME_SKEW_S))) {
          System.out.printf("%s - Skipped feeding at %s because the device *just* fed at %s\n",
              new Date(), upcomingFeedingTime, timeOfLastFeeding);
          continue;
        }

        // This is the first feeding the device hasn't done yet, feed it.
        return upcomingFeedingTime;

      } else if (timeOfLastFeeding != null
          && timeOfLastFeedingScheduleChange != null
          && timeOfLastFeedingScheduleChange.isBefore(upcomingFeedingTime)
          && timeOfLastFeeding.isBefore(upcomingFeedingTime.minusSeconds(MAX_PHOTON_TIME_SKEW_S))) {
        System.out.printf("%s - Feeding immediately because the device last fed at %s and we " +
            "were supposed to feed at %s\n", new Date(), timeOfLastFeeding, upcomingFeedingTime);
        return now;
      }
    }
    return null;
  }

  private static void addFeedingTimes(List<ZonedDateTime> upcomingFeedingTimes,
      int[] feedingTimesMinutesIntoDay) {
    ZonedDateTime midnightThisMorning = ZonedDateTime.now(DEVICE_TIME_ZONE)
        .withHour(0).withMinute(0).withSecond(0).withNano(0);
    for (int minutesIntoDay : feedingTimesMinutesIntoDay) {
      upcomingFeedingTimes.add(midnightThisMorning.plusMinutes(minutesIntoDay));
      upcomingFeedingTimes.add(midnightThisMorning.plusMinutes(minutesIntoDay).plusDays(1));
    }
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

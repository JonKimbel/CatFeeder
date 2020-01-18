package com.jonkimbel.catfeeder.backend.time;

import jdk.internal.jline.internal.Nullable;

import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.Preferences;
import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.Preferences.FeedingSchedule;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class Time {
  private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a (z)");
  private static final TimeZone DEVICE_TIME_ZONE = TimeZone.getTimeZone("US/Pacific");
  private static final int MORNING_TIME_MINUTES_INTO_DAY = 6*60; // 6AM.
  private static final int EVENING_TIME_MINUTES_INTO_DAY = 18*60; // 6PM.

  // TODO [CLEANUP]: Implement support for user-defined device timezones.
  // TODO [CLEANUP]: Implement support for user-defined feeding times.

  public static String format(Date date) {
    return DATE_FORMAT.format(date);
  }

  @Nullable
  public static Date calculateNextFeedingTime(Preferences preferences,
      @Nullable Date dateOfLastFeeding) {
    Calendar morningTodayCalendar = timeOfDayToday(MORNING_TIME_MINUTES_INTO_DAY);
    Calendar morningTomorrowCalendar = copyADayLater(morningTodayCalendar);
    Calendar eveningTodayCalendar = timeOfDayToday(EVENING_TIME_MINUTES_INTO_DAY);
    Date now = new Date();

    switch (preferences.getFeedingSchedule()) {
      case AUTO_FEED_IN_MORNINGS:
        if (morningTodayCalendar.after(now)) {
          return morningTodayCalendar.getTime();
        } else {
          return morningTomorrowCalendar.getTime();
        }
      case AUTO_FEED_IN_MORNINGS_AND_EVENINGS:
        if (morningTodayCalendar.after(now)) {
          return morningTodayCalendar.getTime();
        } else if (eveningTodayCalendar.after(now)) {
          return eveningTodayCalendar.getTime();
        }
        return morningTomorrowCalendar.getTime();
      default:
        return null;
    }
  }

  private static Calendar timeOfDayToday(int minutes_into_day) {
    Calendar calendar = Calendar.getInstance(DEVICE_TIME_ZONE);
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, minutes_into_day);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar;
  }

  private static Calendar copyADayLater(Calendar calendar) {
    Calendar copy = (Calendar) calendar.clone();
    copy.add(Calendar.DAY_OF_YEAR, 1);
    return copy;
  }
}

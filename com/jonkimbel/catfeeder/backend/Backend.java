package com.jonkimbel.catfeeder.backend;

import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.Preferences;
import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.FeedingPreferences;
import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.FeedingPreferences.FeedingSchedule;
import com.jonkimbel.catfeeder.backend.server.*;
import com.jonkimbel.catfeeder.backend.storage.api.PreferencesStorage;
import com.jonkimbel.catfeeder.proto.CatFeeder.EmbeddedRequest;
import com.jonkimbel.catfeeder.proto.CatFeeder.EmbeddedResponse;
import com.jonkimbel.catfeeder.backend.server.HttpServer.RequestHandler;
import com.jonkimbel.catfeeder.backend.template.TemplateFiller;
import com.jonkimbel.catfeeder.backend.time.Time;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

// TODO [V3]: Add nullability tests.
// TODO [V3]: Add unit tests.

public class Backend implements RequestHandler {
  private static final int PORT = 80;
  private static final String TEMPLATE_PATH = "/com/jonkimbel/catfeeder/backend/template.html";
  private static final int MIN_SCOOPS_PER_FEEDING = 1;
  private static final int MAX_FEEDINGS_TO_DISPLAY = 3;

  // TODO [V2]: find a way to kill the server gracefully so the embedded device isn't stuck trying
  //            to transfer data. OR get the device to be resilient to such cases.

  private final int port;

  public static void main(String[] args) throws IOException {
    // TODO [V3]: take port as an argument.
    new Backend(PORT).run();
  }

  private Backend(int port) {
    this.port = port;
  }

  private void run() throws IOException {
    ServerSocket socket = new ServerSocket(port);
    System.out.printf("%s - listening on port %s\n", new Date(), port);

    while (true) {
      Thread thread = HttpServer.threadForConnection(socket.accept(), this);
      thread.start();
    }
  }

  // TODO [V1]: Refactor into multiple classes.

  @Override
  public HttpResponse handleRequest(HttpHeader requestHeader, String requestBody)
      throws IOException {
    HttpResponse.Builder responseBuilder = HttpResponse.builder();
    boolean isFormInput =
        requestHeader.path.equals("/") && requestHeader.method == Http.Method.POST;

    if (requestHeader.method != Http.Method.GET && !isFormInput) {
      return responseBuilder.setResponseCode(Http.ResponseCode.NOT_IMPLEMENTED).build();
    }

    if (isFormInput) {
      updateFeedingPreferences(MapParser.parsePostBody(requestBody));
      return responseBuilder
          .setResponseCode(Http.ResponseCode.FOUND)
          .setLocation("/")
          .build();
    } else if (requestHeader.path.equals("/")) {
      // TODO [V2]: add a "feed now" button. Override the next feeding time when the user taps
      //            "feed now", clear this override once the photon has fed.
      return responseBuilder
          .setResponseCode(Http.ResponseCode.OK)
          .setHtmlBody(getHtmlResponse(TEMPLATE_PATH))
          .build();
    } else if (requestHeader.path.startsWith("/photon")) {
      // TODO [V2]: split out into a separate method.
      EmbeddedRequest request = EmbeddedRequest.parseFrom(requestBody.getBytes());

      Preferences.Builder preferencesBuilder = PreferencesStorage.get().toBuilder()
          .setLastPhotonCheckInMsSinceEpoch(System.currentTimeMillis());

      boolean wroteLastFeedingTime = false;
      if (request.hasTimeSinceLastFeedingMs()) {
        List<Long> feedingHistory = new ArrayList<>(
            preferencesBuilder.getFeedingPreferencesBuilder().getLastTenFeedingTimesMsSinceEpochList());
        while (feedingHistory.size() >= 10) {
          feedingHistory.remove(9);
        }
        feedingHistory.add(0, Instant.now().toEpochMilli() - request.getTimeSinceLastFeedingMs());

        preferencesBuilder.getFeedingPreferencesBuilder()
            .clearLastTenFeedingTimesMsSinceEpoch()
            .addAllLastTenFeedingTimesMsSinceEpoch(feedingHistory);
        wroteLastFeedingTime = true;
      }

      PreferencesStorage.set(preferencesBuilder.build());

      return responseBuilder
          .setResponseCode(Http.ResponseCode.OK)
          .setProtobufBody(getProtobufResponse(/* wroteLastFeedingTime = */ wroteLastFeedingTime))
          .build();
    }

    return responseBuilder.setResponseCode(Http.ResponseCode.NOT_FOUND).build();
  }

  private byte[] getProtobufResponse(boolean wroteLastFeedingTime) {
    EmbeddedResponse.Builder response = EmbeddedResponse.newBuilder();

    response.setDelayUntilNextCheckInMs(Time.getTimeToNextCheckInMs());
    response.setDelayUntilNextFeedingMs(Time.getTimeToNextFeedingMs());
    response.setLastFeedingTimeConsumed(wroteLastFeedingTime);

    response.setScoopsToFeed(Math.max(
        PreferencesStorage.get().getFeedingPreferences().getNumberOfScoopsPerFeeding(),
        MIN_SCOOPS_PER_FEEDING));

    return response.build().toByteArray();
  }

  private String getHtmlResponse(String templatePath) throws IOException {
    // TODO [V1]: Implement support for user-defined feeding times - just take a # of scoops and a
    //            time per feeding, pace the feedings out automatically.

    // TODO [V1]: Add passcode to web UI.
    // Oauth? Probably not allowed over HTTP, but worth investigation.
    // See this info on cookie protocol:
    // https://stackoverflow.com/questions/3467114/how-are-cookies-passed-in-the-http-protocol

    String template = new String(getClass().getResourceAsStream(templatePath).readAllBytes(),
        StandardCharsets.UTF_8);
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

    templateValues.put("next_feeding", Time.format(Time.getTimeOfNextFeeding()));
    int scoopsPerFeeding = Math.max(
        feedingPrefs.getNumberOfScoopsPerFeeding(),
        MIN_SCOOPS_PER_FEEDING);
    templateValues.put("number_of_scoops_per_feeding", String.valueOf(scoopsPerFeeding));

    templateValues.put("check_in_warning_display",
        Time.wasLastCheckInRecent() ? "none" : "inherit");
    templateValues.put("check_in_warning_time", Time.format(Time.getTimeOfLastCheckIn()));

    if (feedingPrefs.getFeedingSchedule() == FeedingSchedule.AUTO_FEED_IN_MORNINGS) {
      templateValues.put("feed_schedule_mornings", "checked");
    } else if (feedingPrefs.getFeedingSchedule() ==
        FeedingSchedule.AUTO_FEED_IN_MORNINGS_AND_EVENINGS) {
      templateValues.put("feed_schedule_mornings_and_evenings", "checked");
    } else {
      templateValues.put("feed_schedule_never", "checked");
    }

    return new TemplateFiller(template).fill(templateValues);
  }

  private void updateFeedingPreferences(Map<String, String> queryKeysAndValues) {
    Preferences.Builder builder = PreferencesStorage.get().toBuilder();

    updateFeedingSchedule(queryKeysAndValues.get("feed_schedule"),
        builder.getFeedingPreferencesBuilder());
    updateScoopsPerFeeding(queryKeysAndValues.get("number_of_scoops_per_feeding"),
        builder.getFeedingPreferencesBuilder());

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

    if (numberOfScoopsPerFeeding == null || numberOfScoopsPerFeeding < MIN_SCOOPS_PER_FEEDING) {
      return;
    }

    builder.setNumberOfScoopsPerFeeding(numberOfScoopsPerFeeding);
  }

  private static void updateFeedingSchedule(String feedingScheduleArgument,
      FeedingPreferences.Builder builder) {
    if (feedingScheduleArgument == null) {
      return;
    }

    boolean scheduleRecognized =
        applyFeedingSchedule(builder, feedingScheduleArgument,
            "mornings", FeedingSchedule.AUTO_FEED_IN_MORNINGS);
    scheduleRecognized |= applyFeedingSchedule(builder, feedingScheduleArgument,
        "mornings_and_evenings",
        FeedingSchedule.AUTO_FEED_IN_MORNINGS_AND_EVENINGS);
    scheduleRecognized |= applyFeedingSchedule(builder, feedingScheduleArgument,
        "never", FeedingSchedule.NEVER_AUTO_FEED);

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

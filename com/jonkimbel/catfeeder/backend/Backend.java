package com.jonkimbel.catfeeder.backend;

import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.Preferences;
import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.FeedingPreferences;
import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.FeedingPreferences.FeedingSchedule;
import com.jonkimbel.catfeeder.backend.server.*;
import com.jonkimbel.catfeeder.backend.storage.api.PreferencesStorage;
import com.jonkimbel.catfeeder.proto.CatFeeder;
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
import java.time.ZonedDateTime;
import java.util.*;

// TODO [V2]: Add nullability tests.
// TODO [V2]: Add unit tests.

public class Backend implements RequestHandler {
  private static final int PORT = 80;
  private static final String TEMPLATE_PATH = "/com/jonkimbel/catfeeder/backend/template.html";
  private static final long INTERVAL_BETWEEN_CHECK_INS_MS = 10 * 60 * 1000; // 10 min.
  private static final long INTERVAL_TO_GIVE_DEVICE_TO_COMPLETE_CHECK_IN_MS = 60 * 1000; // 1 min.
  private static final int MIN_SCOOPS_PER_FEEDING = 1;

  // TODO [V2]: find a way to kill the server gracefully so the embedded device isn't stuck trying
  // to transfer data. OR get the device to be resilient to such cases.

  private final int port;

  public static void main(String[] args) throws IOException {
    // TODO [V2]: take port as an argument.
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

  @Override
  public HttpResponse handleRequest(HttpHeader requestHeader, String requestBody)
      throws IOException {
    HttpResponse.Builder responseBuilder = HttpResponse.builder();

    if (requestHeader.method != Http.Method.GET) {
      return responseBuilder.setResponseCode(Http.ResponseCode.NOT_IMPLEMENTED).build();
    }

    if (requestHeader.path.equals("/")) {
      return responseBuilder
          .setResponseCode(Http.ResponseCode.OK)
          .setHtmlBody(getHtmlResponse(TEMPLATE_PATH))
          .build();
    } else if (requestHeader.path.startsWith("/write?")) {
      // TODO [V1]: switch from GET to POST for this, it results in weird re-sending behavior when
      //  you refresh the page. Or maybe redirect to "/"?
      Map<String, String> queryKeysAndValues = QueryParser.parseQuery(requestHeader.path);
      updateFeedingPreferences(queryKeysAndValues);

      // TODO [V1]: add a "feed now" button.

      return responseBuilder
          .setResponseCode(Http.ResponseCode.OK)
          .setHtmlBody(getHtmlResponse(TEMPLATE_PATH))
          .build();
    } else if (requestHeader.path.startsWith("/photon")) {
      // TODO [V2]: split out into a separate method.
      EmbeddedRequest request = EmbeddedRequest.parseFrom(requestBody.getBytes());

      Preferences.Builder preferencesBuilder = PreferencesStorage.get().toBuilder()
          .setLastPhotonCheckInMsSinceEpoch(System.currentTimeMillis());

      if (request.hasTimeSinceLastFeedingMs()) {
        preferencesBuilder.getFeedingPreferencesBuilder().setLastFeedingTimeMsSinceEpoch(
            Instant.now().toEpochMilli() - request.getTimeSinceLastFeedingMs());
      }

      PreferencesStorage.set(preferencesBuilder.build());

      // TODO [V1]: account for poor photon time keeping, ensure we never over-feed due to a check
      // in right after a slightly early feed.
      return responseBuilder
          .setResponseCode(Http.ResponseCode.OK)
          .setProtobufBody(getProtobufResponse())
          .build();
    }

    return responseBuilder.setResponseCode(Http.ResponseCode.NOT_FOUND).build();
  }

  // TODO: continue scanning from here, then go through backend packages.

  private byte[] getProtobufResponse() {
    EmbeddedResponse.Builder response = EmbeddedResponse.newBuilder();

    @Nullable ZonedDateTime nextFeedingTime = Time.getTimeOfNextFeeding();
    if (nextFeedingTime == null) {
      response.setDelayUntilNextCheckInMs(INTERVAL_BETWEEN_CHECK_INS_MS);
    } else {
      long durationUntilNextFeedingMs =
          nextFeedingTime.toInstant().toEpochMilli() - Instant.now().toEpochMilli();
      response.setDelayUntilNextFeedingMs(durationUntilNextFeedingMs);

      long durationUntilNextCheckInMs = INTERVAL_BETWEEN_CHECK_INS_MS;
      while (Math.abs(durationUntilNextCheckInMs - durationUntilNextFeedingMs) <
          INTERVAL_TO_GIVE_DEVICE_TO_COMPLETE_CHECK_IN_MS) {
        durationUntilNextCheckInMs += INTERVAL_TO_GIVE_DEVICE_TO_COMPLETE_CHECK_IN_MS;
      }
      response.setDelayUntilNextCheckInMs(durationUntilNextCheckInMs);
    }

    response.setScoopsToFeed(Math.max(
        PreferencesStorage.get().getFeedingPreferences().getNumberOfScoopsPerFeeding(),
        MIN_SCOOPS_PER_FEEDING));

    return response.build().toByteArray();
  }

  private String getHtmlResponse(String templatePath) throws IOException {
    // TODO [V1]: Add passcode to web UI
    // Oauth? Probably not allowed over HTTP, but worth investigation.
    // See this info on cookie protocol:
    // https://stackoverflow.com/questions/3467114/how-are-cookies-passed-in-the-http-protocol

    String template = new String(getClass().getResourceAsStream(templatePath).readAllBytes(),
        StandardCharsets.UTF_8);
    Map<String, String> templateValues = new HashMap<>();

    templateValues.put("last_feeding", Time.format(Time.getTimeOfLastFeeding()));
    templateValues.put("next_feeding", Time.format(Time.getTimeOfNextFeeding()));
    int scoopsPerFeeding = Math.max(
        PreferencesStorage.get().getFeedingPreferences().getNumberOfScoopsPerFeeding(),
        MIN_SCOOPS_PER_FEEDING);
    templateValues.put("number_of_scoops_per_feeding", String.valueOf(scoopsPerFeeding));

    templateValues.put("check_in_warning_display",
        Time.wasLastCheckInRecent() ? "none" : "inherit");
    templateValues.put("check_in_warning_time", Time.format(Time.getTimeOfLastCheckIn()));

    if (PreferencesStorage.get().getFeedingPreferences().getFeedingSchedule() ==
        FeedingSchedule.AUTO_FEED_IN_MORNINGS) {
      templateValues.put("feed_schedule_mornings", "checked");
    } else if (PreferencesStorage.get().getFeedingPreferences().getFeedingSchedule() ==
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

    builder
        .setNumberOfScoopsPerFeeding(numberOfScoopsPerFeeding)
        .setLastFeedingScheduleChangeMsSinceEpoch(Instant.now().toEpochMilli());
  }

  private static void updateFeedingSchedule(String feedingSchedule,
      FeedingPreferences.Builder builder) {
    if (feedingSchedule == null) {
      return;
    }

    if (feedingSchedule.equals("mornings")) {
      builder
          .setFeedingSchedule(FeedingSchedule.AUTO_FEED_IN_MORNINGS)
          .setLastFeedingScheduleChangeMsSinceEpoch(Instant.now().toEpochMilli());
    } else if (feedingSchedule.equals("mornings_and_evenings")) {
      builder
          .setFeedingSchedule(FeedingSchedule.AUTO_FEED_IN_MORNINGS_AND_EVENINGS)
          .setLastFeedingScheduleChangeMsSinceEpoch(Instant.now().toEpochMilli());
    } else if (feedingSchedule.equals("never")) {
      builder
          .setFeedingSchedule(FeedingSchedule.NEVER_AUTO_FEED)
          .setLastFeedingScheduleChangeMsSinceEpoch(Instant.now().toEpochMilli());
    } else {
      System.err.printf("%s - Unrecognized feeding schedule:%s\n", new Date(), feedingSchedule);
    }
  }
}

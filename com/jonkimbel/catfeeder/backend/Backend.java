package com.jonkimbel.catfeeder.backend;

import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.Preferences;
import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.FeedingPreferences;
import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.FeedingPreferences.FeedingSchedule;
import com.jonkimbel.catfeeder.backend.storage.api.PreferencesStorage;
import com.jonkimbel.catfeeder.proto.CatFeeder.EmbeddedResponse;
import com.jonkimbel.catfeeder.backend.server.Http;
import com.jonkimbel.catfeeder.backend.server.HttpServer;
import com.jonkimbel.catfeeder.backend.server.HttpServer.RequestHandler;
import com.jonkimbel.catfeeder.backend.server.QueryParser;
import com.jonkimbel.catfeeder.backend.server.HttpResponse;
import com.jonkimbel.catfeeder.backend.template.TemplateFiller;
import com.jonkimbel.catfeeder.backend.time.Time;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

// TODO [CLEANUP]: Add nullability tests.
// TODO [CLEANUP]: Add unit tests.

public class Backend implements RequestHandler {
  private static final int PORT = 80;
  private static final String TEMPLATE_PATH = "/com/jonkimbel/catfeeder/backend/template.html";
  private static final long INTERVAL_BETWEEN_CHECK_INS_MS = 10 * 60 * 1000; // 10 min.
  private static final long INTERVAL_TO_GIVE_DEVICE_TO_COMPLETE_CHECK_IN = 60 * 1000; // 1 min.
  private static final int MIN_SCOOPS_PER_FEEDING = 1;

  private final int port;

  public static void main(String[] args) throws IOException {
    // TODO [CLEANUP]: take port as an argument.
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
  public HttpResponse handleRequest(Http.Method method, String requestPath) throws IOException {
    HttpResponse.Builder responseBuilder = HttpResponse.builder();

    if (method != Http.Method.GET) {
      return responseBuilder.setResponseCode(Http.ResponseCode.NOT_IMPLEMENTED).build();
    }

    if (requestPath.equals("/")) {
      return responseBuilder
          .setResponseCode(Http.ResponseCode.OK)
          .setHtmlBody(getHtmlResponse(TEMPLATE_PATH))
          .build();
    } else if (requestPath.startsWith("/write?")) {
      // TODO [CLEANUP]: switch from GET to POST for this, it results in weird re-sending behavior
      // when you refresh the page.
      Map<String, String> queryKeysAndValues = QueryParser.parseQuery(requestPath);
      updateFeedingPreferences(queryKeysAndValues);

      return responseBuilder
          .setResponseCode(Http.ResponseCode.OK)
          .setHtmlBody(getHtmlResponse(TEMPLATE_PATH))
          .build();
    } else if (requestPath.startsWith("/photon")) {
      PreferencesStorage.set(PreferencesStorage.get().toBuilder()
          .setLastPhotonCheckInMsSinceEpoch(System.currentTimeMillis())
          .build());
      return responseBuilder
          .setResponseCode(Http.ResponseCode.OK)
          .setProtobufBody(getProtobufResponse())
          .build();
    }

    return responseBuilder.setResponseCode(Http.ResponseCode.NOT_FOUND).build();
  }

  private byte[] getProtobufResponse() {
    EmbeddedResponse.Builder response = EmbeddedResponse.newBuilder();

    @Nullable Date nextFeedingTime = Time.calculateNextFeedingTime();
    if (nextFeedingTime == null) {
      response.setDelayUntilNextCheckInMs(INTERVAL_BETWEEN_CHECK_INS_MS);
    } else {
      long durationUntilNextFeedingMs =
          nextFeedingTime.toInstant().toEpochMilli() - Instant.now().toEpochMilli();
      response.setDelayUntilNextFeedingMs(durationUntilNextFeedingMs);

      long durationUntilNextCheckInMs = INTERVAL_BETWEEN_CHECK_INS_MS;
      while (Math.abs(durationUntilNextCheckInMs - durationUntilNextFeedingMs) <
          INTERVAL_TO_GIVE_DEVICE_TO_COMPLETE_CHECK_IN) {
        durationUntilNextCheckInMs += INTERVAL_TO_GIVE_DEVICE_TO_COMPLETE_CHECK_IN;
      }
      response.setDelayUntilNextCheckInMs(durationUntilNextCheckInMs);
    }

    response.setScoopsToFeed(Math.max(
        PreferencesStorage.get().getFeedingPreferences().getNumberOfScoopsPerFeeding(),
        MIN_SCOOPS_PER_FEEDING));

    return response.build().toByteArray();
  }

  private String getHtmlResponse(String templatePath) throws IOException {
    String template = new String(getClass().getResourceAsStream(templatePath).readAllBytes(),
        StandardCharsets.UTF_8);
    Map<String, String> templateValues = new HashMap<>();

    templateValues.put("last_feeding", Time.format(Time.getLastFeedingDate()));
    templateValues.put("next_feeding", Time.format(Time.calculateNextFeedingTime()));
    int scoopsPerFeeding = Math.max(
        PreferencesStorage.get().getFeedingPreferences().getNumberOfScoopsPerFeeding(),
        MIN_SCOOPS_PER_FEEDING);
    templateValues.put("number_of_scoops_per_feeding", String.valueOf(scoopsPerFeeding));

    // TODO: Use preferences.lastPhotonCheckInMsSinceEpoch() to warn the viewer if the embedded
    // device hasn't communicated with the server in a while.

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

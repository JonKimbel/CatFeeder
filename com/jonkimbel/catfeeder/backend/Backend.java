package com.jonkimbel.catfeeder.backend;

import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.Preferences;
import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.Preferences.FeedingSchedule;
import com.jonkimbel.catfeeder.backend.storage.api.PreferencesStorage;
import com.jonkimbel.catfeeder.proto.CatFeeder.EmbeddedResponse;
import com.jonkimbel.catfeeder.backend.server.Http;
import com.jonkimbel.catfeeder.backend.server.HttpServer;
import com.jonkimbel.catfeeder.backend.server.HttpServer.RequestHandler;
import com.jonkimbel.catfeeder.backend.server.QueryParser;
import com.jonkimbel.catfeeder.backend.server.HttpResponse;
import com.jonkimbel.catfeeder.backend.template.TemplateFiller;
import com.jonkimbel.catfeeder.backend.time.Time;
import jdk.internal.jline.internal.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.*;

// TODO [CLEANUP]: Add nullability tests.
// TODO [CLEANUP]: Add unit tests.

public class Backend implements RequestHandler {
  private static final int PORT = 80;
  private static final String TEMPLATE_PATH = "/com/jonkimbel/catfeeder/backend/template.html";

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
          .setPrintBody(formatHtml(TEMPLATE_PATH))
          .build();
    } else if (requestPath.startsWith("/write?")) {
      // TODO [CLEANUP]: switch from GET to POST for this, it results in weird
      // re-sending issues when you refresh the page.
      Map<String, String> queryKeysAndValues = QueryParser.parseQuery(requestPath);
      updateFeedingSchedule(queryKeysAndValues.get("feed_schedule"));

      return responseBuilder
          .setResponseCode(Http.ResponseCode.OK)
          .setPrintBody(formatHtml(TEMPLATE_PATH))
          .build();
    } else if (requestPath.startsWith("/photon")) {
      // TODO: populate this proto before returning it.
      EmbeddedResponse.Builder response = EmbeddedResponse.newBuilder();
      response.setDelayUntilNextCheckInMs(5000);
      response.setDelayUntilNextFeedingMs(1000);
      return responseBuilder
          .setResponseCode(Http.ResponseCode.OK)
          .setByteBody(response.build().toByteArray())
          .build();
    }

    return responseBuilder.setResponseCode(Http.ResponseCode.NOT_FOUND).build();
  }

  private void updateFeedingSchedule(String feedingSchedule) {
    if (feedingSchedule == null) {
      return;
    }

    if (feedingSchedule.equals("mornings")) {
      PreferencesStorage.set(
          PreferencesStorage.get().toBuilder()
              .setFeedingSchedule(Preferences.FeedingSchedule.AUTO_FEED_IN_MORNINGS).build());
    } else if (feedingSchedule.equals("mornings_and_evenings")) {
      PreferencesStorage.set(
          PreferencesStorage.get().toBuilder()
              .setFeedingSchedule(Preferences.FeedingSchedule.AUTO_FEED_IN_MORNINGS_AND_EVENINGS)
              .build());
    } else {
      System.err.printf("%s - Unrecognized feeding schedule:%s\n", new Date(), feedingSchedule);
    }
  }

  private String formatHtml(String templatePath) throws IOException {
    String template = new String(getClass().getResourceAsStream(templatePath).readAllBytes(),
        StandardCharsets.UTF_8);
    Map<String, String> templateValues = new HashMap<>();

    Date dateOfLastFeeding = null;
    if (PreferencesStorage.get().hasLastFeedingTimeMsSinceEpoch()) {
      dateOfLastFeeding = new Date(PreferencesStorage.get().getLastFeedingTimeMsSinceEpoch());
      templateValues.put("last_feeding", Time.format(dateOfLastFeeding));
    } else {
      templateValues.put("last_feeding", "never");
    }

    @Nullable Date nextFeedingTime = Time.calculateNextFeedingTime(PreferencesStorage.get(),
        dateOfLastFeeding);
    if (nextFeedingTime != null) {
      templateValues.put("next_feeding", Time.format(nextFeedingTime));
    } else {
      templateValues.put("next_feeding", "never");
    }

    // TODO [CLEANUP]: Use preferences.lastPhotonCheckInMsSinceEpoch() to warn the viewer if the
    // embedded device hasn't communicated with the server in a while.

    // TODO: Implement support for a "never auto feed" option.

    if (PreferencesStorage.get().getFeedingSchedule() == FeedingSchedule.AUTO_FEED_IN_MORNINGS) {
      templateValues.put("mornings", "checked");
    } else if (PreferencesStorage.get().getFeedingSchedule() ==
        FeedingSchedule.AUTO_FEED_IN_MORNINGS_AND_EVENINGS) {
      templateValues.put("mornings_and_evenings", "checked");
    }

    return new TemplateFiller(template).fill(templateValues);
  }
}

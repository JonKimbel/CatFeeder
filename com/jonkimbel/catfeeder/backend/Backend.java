package com.jonkimbel.catfeeder.backend;

import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.Preferences;
import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.Preferences.FeedingSchedule;
import com.jonkimbel.catfeeder.proto.CatFeeder.EmbeddedResponse;
import com.jonkimbel.catfeeder.backend.server.Http;
import com.jonkimbel.catfeeder.backend.server.HttpServer;
import com.jonkimbel.catfeeder.backend.server.HttpServer.RequestHandler;
import com.jonkimbel.catfeeder.backend.server.QueryParser;
import com.jonkimbel.catfeeder.backend.server.HttpResponse;
import com.jonkimbel.catfeeder.backend.storage.Storage;
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

  private final Storage storage;
  private final int port;

  public static void main(String[] args) throws IOException {
    // TODO [CLEANUP]: take port as an argument.
    new Backend(PORT).run();
  }

  private Backend(int port) {
    this.port = port;
    this.storage = Storage.getStorage();
  }

  private void run() throws IOException {
    ServerSocket socket = new ServerSocket(port);
    System.out.printf("%s - listening on port %s\n", new Date(), port);

    while (true) {
      Thread thread = HttpServer.threadForConnection(socket.accept(), this);
      System.out.printf("%s - connection opened\n", new Date());
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
          .setPrintBody(formatBody(TEMPLATE_PATH))
          .build();
    } else if (requestPath.startsWith("/write?")) {
      // TODO [CLEANUP]: switch from GET to POST for this, it results in weird
      // re-sending issues when you refresh the page.
      Map<String, String> queryKeysAndValues = QueryParser.parseQuery(requestPath);
      updateFeedingSchedule(queryKeysAndValues.get("feed_schedule"));

      return responseBuilder
          .setResponseCode(Http.ResponseCode.OK)
          .setPrintBody(formatBody(TEMPLATE_PATH))
          .build();
    } else if (requestPath.startsWith("/photon")) {
      // TODO: populate this proto before returning it.
      return responseBuilder
          .setResponseCode(Http.ResponseCode.OK)
          .setByteBody(EmbeddedResponse.getDefaultInstance().toByteArray())
          .build();
    }

    return responseBuilder.setResponseCode(Http.ResponseCode.NOT_FOUND).build();
  }

  private void updateFeedingSchedule(String feedingSchedule) {
    if (feedingSchedule == null) {
      return;
    }

    Preferences preferences = (Preferences) storage.getItemBlocking(Storage.Item.PREFERENCES);
    if (feedingSchedule.equals("mornings")) {
      storage.setItemBlocking(
          Storage.Item.PREFERENCES,
          preferences.toBuilder()
              .setFeedingSchedule(Preferences.FeedingSchedule.AUTO_FEED_IN_MORNINGS).build());
    } else if (feedingSchedule.equals("mornings_and_evenings")) {
      storage.setItemBlocking(
          Storage.Item.PREFERENCES,
          preferences.toBuilder()
              .setFeedingSchedule(Preferences.FeedingSchedule.AUTO_FEED_IN_MORNINGS_AND_EVENINGS)
              .build());
    } else {
      System.err.printf("%s - Unrecognized feeding schedule:%s\n", new Date(), feedingSchedule);
    }
  }

  private String formatBody(String templatePath) throws IOException {
    String template = new String(getClass().getResourceAsStream(templatePath).readAllBytes(),
        StandardCharsets.UTF_8);
    Map<String, String> templateValues = new HashMap<>();
    Preferences preferences = (Preferences) storage.getItemBlocking(Storage.Item.PREFERENCES);

    Date dateOfLastFeeding = null;
    if (preferences.hasLastFeedingTimeMsSinceEpoch()) {
      dateOfLastFeeding = new Date(preferences.getLastFeedingTimeMsSinceEpoch());
      templateValues.put("last_feeding", Time.format(dateOfLastFeeding));
    } else {
      templateValues.put("last_feeding", "never");
    }

    @Nullable Date nextFeedingTime = Time.calculateNextFeedingTime(preferences, dateOfLastFeeding);
    if (nextFeedingTime != null) {
      templateValues.put("next_feeding", Time.format(nextFeedingTime));
    } else {
      templateValues.put("next_feeding", "never");
    }

    // TODO [CLEANUP]: Use preferences.lastPhotonCheckInMsSinceEpoch() to warn the viewer if the
    // embedded device hasn't communicated with the server in a while.

    // TODO: Implement support for a "never auto feed" option.

    if (preferences.getFeedingSchedule() == FeedingSchedule.AUTO_FEED_IN_MORNINGS) {
      templateValues.put("mornings", "checked");
    } else if (preferences.getFeedingSchedule() == FeedingSchedule.AUTO_FEED_IN_MORNINGS_AND_EVENINGS) {
      templateValues.put("mornings_and_evenings", "checked");
    }

    return new TemplateFiller(template).fill(templateValues);
  }
}

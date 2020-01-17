package com.jonkimbel.catfeeder;

import com.jonkimbel.catfeeder.proto.PreferencesOuterClass.Preferences;
import com.jonkimbel.catfeeder.server.Http;
import com.jonkimbel.catfeeder.server.HttpServer;
import com.jonkimbel.catfeeder.server.HttpServer.RequestHandler;
import com.jonkimbel.catfeeder.server.QueryParser;
import com.jonkimbel.catfeeder.server.Response;
import com.jonkimbel.catfeeder.storage.Storage;
import com.jonkimbel.catfeeder.template.TemplateFiller;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// TODO [CLEANUP]: Add nullability tests.
// TODO [CLEANUP]: Add unit tests.

public class App implements RequestHandler {
  private static final int PORT = 8080;
  private final static String TEMPLATE_PATH = "/com/jonkimbel/catfeeder/template.html";

  private final Storage storage;
  private final int port;

  public static void main(String[] args) throws IOException {
    // TODO [CLEANUP]: take port as an argument.
    new App(PORT).run();
  }

  private App(int port) {
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
  public Response handleRequest(Http.Method method, String requestPath) throws IOException {
    Response.Builder responseBuilder = Response.builder();

    if (method != Http.Method.GET) {
      return responseBuilder.setResponseCode(Http.ResponseCode.NOT_IMPLEMENTED).build();
    }

    if (requestPath.equals("/")) {
      return responseBuilder
          .setResponseCode(Http.ResponseCode.OK)
          .setBody(formatBody(TEMPLATE_PATH))
          .build();
    } else if (requestPath.startsWith("/write?")) {
      Map<String, String> queryKeysAndValues = QueryParser.parseQuery(requestPath);
      updateFeedingSchedule(queryKeysAndValues.get("feed_schedule"));

      return responseBuilder
          .setResponseCode(Http.ResponseCode.OK)
          .setBody(formatBody(TEMPLATE_PATH))
          .build();
    }

    // TODO: handle requests from photon.

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

    if (preferences.hasLastFeedingTimeMsSinceEpoch()) {
      Date dateOfLastFeeding = new Date(preferences.getLastFeedingTimeMsSinceEpoch());
      DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss a (z)");
      templateValues.put("last_feeding", dateFormat.format(dateOfLastFeeding));
    } else {
      templateValues.put("last_feeding", "never");
    }

    // TODO: calculate next_feeding time.

    switch (preferences.getFeedingSchedule()) {
      case AUTO_FEED_IN_MORNINGS:
        templateValues.put("mornings", "checked");
        templateValues.put("next_feeding", "some day");
        break;
      case AUTO_FEED_IN_MORNINGS_AND_EVENINGS:
        templateValues.put("mornings_and_evenings", "checked");
        templateValues.put("next_feeding", "some day");
        break;
      default:
        templateValues.put("next_feeding", "never");
        break;
    }

    return new TemplateFiller(template).fill(templateValues);
  }
}

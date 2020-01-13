package com.jonkimbel.catfeeder;

import com.jonkimbel.catfeeder.proto.PreferencesOuterClass.Preferences;
import com.jonkimbel.catfeeder.server.HttpServer;
import com.jonkimbel.catfeeder.server.HttpServer.BodyWriter;
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

public class App implements BodyWriter {
  private static final int PORT = 8080;
  private final static String TEMPLATE_PATH = "/com/jonkimbel/catfeeder/template.html";
  private int port;

  public static void main(String[] args) throws IOException {
    // TODO [CLEANUP]: take port as an argument.
    new App(PORT).run();
  }

  private App(int port) {
    this.port = port;
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
  public String getBodyForRequest(String requestPath) throws IOException {
    // TODO: handle /write?feed_schedule=(mornings|mornings_and_evenings) requests.
    // TODO: handle requests from photon.
    // TODO [CLEANUP]: reject requests with unknown paths.
    return formatBody(TEMPLATE_PATH);
  }

  private String formatBody(String templatePath) throws IOException {
    String template = new String(getClass().getResourceAsStream(templatePath).readAllBytes(),
        StandardCharsets.UTF_8);
    Map<String, String> templateValues = new HashMap<>();
    Preferences preferences =
        (Preferences) Storage.getStorage().getItemBlocking(Storage.Item.PREFERENCES);

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
        templateValues.put("just_mornings", "checked");
        templateValues.put("next_feeding", "some day");
      case AUTO_FEED_IN_MORNINGS_AND_EVENINGS:
        templateValues.put("mornings_and_evenings", "checked");
        templateValues.put("next_feeding", "some day");
      default:
        templateValues.put("next_feeding", "never");
        break;
    }

    return new TemplateFiller(template).fill(templateValues);
  }
}

package com.jonkimbel.catfeeder;

import com.jonkimbel.catfeeder.storage.Storage;
import com.jonkimbel.catfeeder.template.TemplateFiller;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import com.jonkimbel.catfeeder.proto.PreferencesOuterClass.Preferences;

public class HttpServer {
  private final Socket socket;
  private final static String TEMPLATE_PATH = "/com/jonkimbel/catfeeder/template.html";

  public static Thread threadForConnection(Socket socket) {
    HttpServer server = new HttpServer(socket);
    return new Thread(server::connect);
  }

  private HttpServer(Socket socket) {
    this.socket = socket;
  }

  private void connect() {
    BufferedReader in = null;
    PrintWriter printWriter = null;
    BufferedOutputStream outputStream = null;
    try {
      in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      printWriter = new PrintWriter(socket.getOutputStream());
      outputStream = new BufferedOutputStream(socket.getOutputStream());

      handle(in, printWriter, outputStream);
    } catch (IOException e) {
      System.err.printf("%s - server error: %s\n", new Date(), e);
    } finally {
      try {
        in.close();
        printWriter.close();
        outputStream.close();
        socket.close();
      } catch (Exception e) {
        System.err.printf("%s - couldn't close stream: %s\n", new Date(), e.getMessage());
      }
    }
  }

  private void handle(BufferedReader in, PrintWriter printWriter,
      BufferedOutputStream outputStream) throws IOException {
    StringTokenizer tokenizer = new StringTokenizer(in.readLine());
    String method = tokenizer.nextToken().toUpperCase();

    if ("GET".equals(method)) {
      // TODO: handle /write?feed_schedule=mornings and mornings_and_evenings requests.
      // TODO: reject any request that isn't / or /write.

      System.out.printf("%s - request OK.\n", new Date());
      String body = formatBody(TEMPLATE_PATH);
      writeHeader(printWriter, Http.ResponseCode.OK, /* contentLength = */ body.length());
      printWriter.print(body);
      printWriter.flush();
    } else {
      System.out.printf("%s - %s Not Implemented.\n", new Date(), method);
      writeHeader(printWriter, Http.ResponseCode.NOT_IMPLEMENTED, /* contentLength = */ 0);
    }
  }

  private static void writeHeader(PrintWriter printWriter, Http.ResponseCode responseCode,
      int contentLength) {
    printWriter.printf("HTTP/1.1 %s\n", responseCode);
    printWriter.println("Server: JonKimbel/CatFeeder HttpServer");
    printWriter.printf("Date: %s\n", new Date());
    printWriter.println("Content-type: text/html");
    printWriter.printf("Content-length: %d\n", contentLength);
    printWriter.println();
    printWriter.flush();
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

    // TODO: put real values for next_feeding.

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

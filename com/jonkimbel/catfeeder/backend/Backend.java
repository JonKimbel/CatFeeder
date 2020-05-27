package com.jonkimbel.catfeeder.backend;

import com.jonkimbel.catfeeder.backend.ActionDeterminer.Action;
import com.jonkimbel.catfeeder.backend.server.*;
import com.jonkimbel.catfeeder.backend.template.Template;
import com.jonkimbel.catfeeder.proto.CatFeeder.EmbeddedRequest;
import com.jonkimbel.catfeeder.backend.server.HttpServer.RequestHandler;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;

// TODO [V3]: Add nullability tests.
// TODO [V3]: Add unit tests.

public class Backend implements RequestHandler {
  private static final int PORT = 80;

  // TODO [V2]: find a way to kill the server gracefully so the embedded device isn't stuck trying
  //            to transfer data. OR get the device to be resilient to such cases.

  private final int port;
  private final HttpBodyRenderer httpBodyRenderer = new HttpBodyRenderer();
  private final ProtoBodyRenderer protoBodyRenderer = new ProtoBodyRenderer();
  private final PreferencesUpdater preferencesUpdater = new PreferencesUpdater();
  private final FeedingTimeUpdater feedingTimeUpdater = new FeedingTimeUpdater();

  public static void main(String[] args) throws IOException {
    // TODO [V3]: take port as an argument.
    new Backend(PORT).runBlocking();

    OutageNotifier.INSTANCE.alert(
        "CatFeeder backend restarted!");
  }

  private Backend(int port) {
    this.port = port;
  }

  private void runBlocking() throws IOException {
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
    Action action = new ActionDeterminer(requestHeader).determine();
    HttpResponse.Builder responseBuilder = HttpResponse.builder();

    switch (action) {
      // Photon actions.
      case SERVE_PHOTON:
        boolean wroteLastFeedingTime = feedingTimeUpdater.update(
            EmbeddedRequest.parseFrom(requestBody.getBytes()));
            OutageNotifier.INSTANCE.alertIfNotCalledWithin(
                Time.getTimeToNextCheckinMs() + 60000L,
                "The CatFeeder is now 60s late for check-in!");
        return responseBuilder
            .setProtobufBody(protoBodyRenderer.render(wroteLastFeedingTime))
            .setResponseCode(Http.ResponseCode.OK)
            .build();

      // Logged-in actions.
      case SERVE_HOME:
        return responseBuilder
            .setHtmlBody(httpBodyRenderer.render(Template.INDEX))
            .setResponseCode(Http.ResponseCode.OK)
            .build();
      case FEED_NOW_REDIRECT_TO_HOME:
        preferencesUpdater.feedAsap();
        return responseBuilder
            .setResponseCode(Http.ResponseCode.FOUND)
            .setLocation("/")
            .build();
      case UPDATE_PREFERENCES_REDIRECT_TO_HOME:
        preferencesUpdater.update(MapParser.parsePostBody(requestBody));
        return responseBuilder
            .setResponseCode(Http.ResponseCode.FOUND)
            .setLocation("/")
            .build();
      case REDIRECT_TO_HOME:
        return responseBuilder
            .setResponseCode(Http.ResponseCode.FOUND)
            .setLocation("/")
            .build();

      // Login actions.
      case SERVE_LOGIN:
        return responseBuilder
            .setHtmlBody(httpBodyRenderer.render(Template.LOGIN))
            .setResponseCode(Http.ResponseCode.OK)
            .build();
      case REDIRECT_TO_LOGIN:
        return responseBuilder
            .setResponseCode(Http.ResponseCode.FOUND)
            .setLocation("/login")
            .build();
      case SET_COOKIE_REDIRECT_TO_HOME:
        return responseBuilder
            .setCookie("passcode", MapParser.parsePostBody(requestBody).get("passcode"))
            .setResponseCode(Http.ResponseCode.FOUND)
            .setLocation("/")
            .build();

      // Error actions.
      case NOT_IMPLEMENTED:
        break;
    }

    return responseBuilder.setResponseCode(Http.ResponseCode.NOT_IMPLEMENTED).build();
  }
}

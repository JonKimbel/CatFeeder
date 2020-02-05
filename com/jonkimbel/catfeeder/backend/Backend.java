package com.jonkimbel.catfeeder.backend;

import com.jonkimbel.catfeeder.backend.server.*;
import com.jonkimbel.catfeeder.backend.storage.api.PasswordStorage;
import com.jonkimbel.catfeeder.backend.template.Template;
import com.jonkimbel.catfeeder.proto.CatFeeder.EmbeddedRequest;
import com.jonkimbel.catfeeder.backend.server.HttpServer.RequestHandler;

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
    HttpResponse.Builder responseBuilder = HttpResponse.builder();
    boolean isPreferencesUpdate =
        requestHeader.path.equals("/") && requestHeader.method == Http.Method.POST;
    boolean isLogin =
        requestHeader.path.equals("/login") && requestHeader.method == Http.Method.POST;

    if (requestHeader.method != Http.Method.GET && !isPreferencesUpdate & !isLogin) {
      return responseBuilder.setResponseCode(Http.ResponseCode.NOT_IMPLEMENTED).build();
    }

    // Handle requests from embedded clients.
    if (requestHeader.path.startsWith("/photon")) {
      boolean wroteLastFeedingTime = feedingTimeUpdater.update(
          EmbeddedRequest.parseFrom(requestBody.getBytes()));
      protoBodyRenderer.render(responseBuilder, wroteLastFeedingTime);
      return responseBuilder.setResponseCode(Http.ResponseCode.OK).build();
    }

    // TODO [V1]: check cookie for password, if it's not present redirect to /login.
    // See this info on cookie protocol:
    // https://stackoverflow.com/questions/3467114/how-are-cookies-passed-in-the-http-protocol

    if (isPreferencesUpdate) {
      // Handle requests to update preferences.
      preferencesUpdater.update(MapParser.parsePostBody(requestBody));
      return responseBuilder
          .setResponseCode(Http.ResponseCode.FOUND)
          .setLocation("/")
          .build();
    } else if (isLogin) {
      // Handle requests to log in.
      String pass = PasswordStorage.get();
      if (pass == null || pass.equals(MapParser.parsePostBody(requestBody).get("passcode"))) {
        return responseBuilder
            .setResponseCode(Http.ResponseCode.FOUND)
            .setLocation("/")
            .build();
      } else {
        // TODO [V1]: add template value for password error message.
        httpBodyRenderer.render(responseBuilder, Template.LOGIN);
        return responseBuilder.setResponseCode(Http.ResponseCode.OK).build();
      }
    } else if (requestHeader.path.equals("/")) {
      // Handle requests for the home page.
      httpBodyRenderer.render(responseBuilder, Template.INDEX);
      return responseBuilder.setResponseCode(Http.ResponseCode.OK).build();
    } else if (requestHeader.path.equals("/login")) {
      // Handle requests for the login page.
      httpBodyRenderer.render(responseBuilder, Template.LOGIN);
      return responseBuilder.setResponseCode(Http.ResponseCode.OK).build();
    }

    // Handle all other requests.
    return responseBuilder.setResponseCode(Http.ResponseCode.NOT_FOUND).build();
  }
}

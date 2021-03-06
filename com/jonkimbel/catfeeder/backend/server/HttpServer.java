package com.jonkimbel.catfeeder.backend.server;

import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class HttpServer {
  private static final DateTimeFormatter HTTP_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O");
  private final Socket socket;
  private final RequestHandler requestHandler;

  public interface RequestHandler {
    HttpResponse handleRequest(HttpHeader requestHeader, String requestBody) throws IOException;
  }

  public static Thread threadForConnection(Socket socket, RequestHandler requestHandler) {
    HttpServer server = new HttpServer(socket, requestHandler);
    return new Thread(server::connect);
  }

  private HttpServer(Socket socket, RequestHandler requestHandler) {
    this.socket = socket;
    this.requestHandler = requestHandler;
  }

  private void connect() {
    BufferedReader in = null;
    PrintWriter printOut = null;
    BufferedOutputStream bytesOut = null;

    try {
      in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      printOut = new PrintWriter(socket.getOutputStream());
      bytesOut = new BufferedOutputStream(socket.getOutputStream());

      handle(in, printOut, bytesOut);
    } catch (IOException e) {
      System.err.printf("%s - server error: %s\n", new Date(), e);
    } finally {
      try {
        in.close();
        printOut.close();
        socket.close();
      } catch (Exception e) {
        System.err.printf("%s - couldn't close stream: %s\n", new Date(), e.getMessage());
      }
    }
  }

  private void handle(BufferedReader in, PrintWriter printOut, BufferedOutputStream bytesOut) throws IOException {
    // Read the request header.
    // TODO [V2]: Make this a separate method.
    List<String> headerLines = new ArrayList<>();
    String lastLine = "";
    do {
      lastLine = in.readLine();
      headerLines.add(lastLine);
    } while (!lastLine.equals(""));
    HttpHeader requestHeader = HttpHeader.fromLines(headerLines);

    // TODO [V3]: Handle "Transfer-Encoding: Chunked"?
    // https://greenbytes.de/tech/webdav/rfc7230.html#message.body.length

    // Read the request body.
    // TODO [V2]: Make this a separate method.
    String requestBody = "";
    if (requestHeader.contentLength != null && requestHeader.contentLength > 0) {
      char[] bodyBuffer = new char[requestHeader.contentLength];
      int bytesRead = 0;
      while (bytesRead < requestHeader.contentLength && (!socket.isClosed() || in.ready())) {
        bytesRead += in.read(
            bodyBuffer,
            /* off = */ bytesRead,
            /* len = */ requestHeader.contentLength - bytesRead);
      }
      if (bytesRead == requestHeader.contentLength) {
        requestBody = new String(bodyBuffer);
      } else {
        System.err.printf("%s - content length:%s but received %s bytes\n",
            new Date(), requestHeader.contentLength, bytesRead);
      }
    }

    System.out.printf("%s - request: %s %s\n", new Date(), requestHeader.method, requestHeader.path);

    // Determine the response header & body.
    HttpResponse httpResponse = requestHandler.handleRequest(requestHeader, requestBody);
    System.out.printf("%s - response: %s\n", new Date(), httpResponse.getResponseCode());

    // Write the response header & body.
    if (httpResponse.isBodyHtml()) {
      writeHeader(printOut, httpResponse, Http.ContentType.HTML,
          /* contentLength = */ httpResponse.getHtmlBody().length());
      printOut.print(httpResponse.getHtmlBody());
      printOut.flush();
    } else {
      writeHeader(printOut, httpResponse, Http.ContentType.PROTOCOL_BUFFER,
          /* contentLength = */ httpResponse.getProtobufBody().length);
      bytesOut.write(httpResponse.getProtobufBody(), /* offset = */ 0,
          /* length = */ httpResponse.getProtobufBody().length);
      bytesOut.flush();
    }
  }

  private static void writeHeader(PrintWriter printWriter, HttpResponse response,
      Http.ContentType contentType, int contentLength) {
    // NOTE: we need to use CRLF (\r\n or println) instead of just \n.
    // HTTP/1.1 spec dictates that CRLF be used to end lines in the HTTP response header.
    // We can't use println, since its behavior will change across platforms.
    printWriter.printf("HTTP/1.1 %s\r\n", response.getResponseCode());
    printWriter.printf("Server: JonKimbel/CatFeeder HttpServer\r\n");
    if (response.getLocationUrl() != null) {
      printWriter.printf("Location: %s\r\n", response.getLocationUrl());
    }
    for (String cookieName : response.getCookies().keySet()) {
      printWriter.printf("Set-Cookie: %s=%s; Expires=%s; Max-Age=%d\r\n",
          cookieName, response.getCookies().get(cookieName),
          // Cookies should set Expires=old-date per rfc2109.
          HTTP_FORMATTER.format(ZonedDateTime.now().minusYears(1)),
          // Expire cookies after 30 days.
          TimeUnit.DAYS.toSeconds(30));
    }
    printWriter.printf("Date: %s\r\n", HTTP_FORMATTER.format(ZonedDateTime.now()));
    printWriter.printf("Content-type: %s\r\n", contentType);
    printWriter.printf("Content-length: %d\r\n", contentLength);
    printWriter.print("\r\n");
    printWriter.flush();
  }
}

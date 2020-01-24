package com.jonkimbel.catfeeder.backend.server;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

public class HttpServer {
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
    List<String> headerLines = new ArrayList<>();
    String lastLine = "";
    do {
      lastLine = in.readLine();
      headerLines.add(lastLine);
    } while (!lastLine.equals(""));
    HttpHeader requestHeader = HttpHeader.fromLines(headerLines);

    // Read the request body.
    // TODO: fix hang that happens here.
    // TODO: Handle "Transfer-Encoding: Chunked"?
    // https://greenbytes.de/tech/webdav/rfc7230.html#message.body.length
    String requestBody = "";
    if (requestHeader.contentLength != null && requestHeader.contentLength > 0) {
      char[] bodyBuffer = new char[requestHeader.contentLength];
      while (!socket.isClosed() || in.ready()) {
        in.read(bodyBuffer, /* off = */ 0, /* len = */ requestHeader.contentLength);
      }
      requestBody = new String(bodyBuffer);
    }

    System.out.printf("%s - request: %s %s\n", new Date(), requestHeader.method, requestHeader.path);
    System.out.printf("%s - body: %s\n", new Date(), requestBody); // TODO [CLEANUP]: remove this.

    // Determine the response header & body.
    HttpResponse httpResponse = requestHandler.handleRequest(requestHeader, requestBody);
    System.out.printf("%s - response: %s\n", new Date(), httpResponse.getResponseCode());

    // Write the response header & body.
    if (httpResponse.isBodyHtml()) {
      writeHeader(printOut, httpResponse.getResponseCode(), Http.ContentType.HTML,
          /* contentLength = */ httpResponse.getHtmlBody().length());
      printOut.print(httpResponse.getHtmlBody());
      printOut.flush();
    } else {
      writeHeader(printOut, httpResponse.getResponseCode(), Http.ContentType.PROTOCOL_BUFFER,
          /* contentLength = */ httpResponse.getProtobufBody().length);
      bytesOut.write(httpResponse.getProtobufBody(), /* offset = */ 0,
          /* length = */ httpResponse.getProtobufBody().length);
      bytesOut.flush();
    }
  }

  private static void writeHeader(PrintWriter printWriter, Http.ResponseCode responseCode,
      Http.ContentType contentType, int contentLength) {
    // NOTE: we need to use CRLF (\r\n or println) instead of just \n.
    // HTTP/1.1 spec dictates that CRLF be used to end lines in the HTTP response header.
    printWriter.printf("HTTP/1.1 %s\r\n", responseCode);
    printWriter.println("Server: JonKimbel/CatFeeder HttpServer");
    printWriter.printf("Date: %s\r\n", new Date());
    printWriter.printf("Content-type: %s\r\n", contentType);
    printWriter.printf("Content-length: %d\r\n", contentLength);
    printWriter.println();
    printWriter.flush();
  }
}

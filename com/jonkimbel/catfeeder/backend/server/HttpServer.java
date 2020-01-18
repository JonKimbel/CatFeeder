package com.jonkimbel.catfeeder.backend.server;

import java.io.*;
import java.net.Socket;
import java.util.Date;
import java.util.StringTokenizer;

public class HttpServer {
  private final Socket socket;
  private final RequestHandler requestHandler;

  public interface RequestHandler {
    HttpResponse handleRequest(Http.Method method, String requestPath) throws IOException;
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
    StringTokenizer tokenizer = new StringTokenizer(in.readLine());
    Http.Method method = Http.Method.fromString(tokenizer.nextToken().toUpperCase());
    String requestPath = tokenizer.nextToken();

    HttpResponse httpResponse = requestHandler.handleRequest(method, requestPath);

    if (httpResponse.isBodyPrint()) {
      writeHeader(printOut, httpResponse.getResponseCode(),
          /* contentLength = */ httpResponse.getPrintBody().length());
      printOut.print(httpResponse.getPrintBody());
      printOut.flush();
    } else {
      writeHeader(printOut, httpResponse.getResponseCode(),
          /* contentLength = */ httpResponse.getByteBody().length);
      bytesOut.write(httpResponse.getByteBody(), /* offset = */ 0,
          /* length = */ httpResponse.getByteBody().length);
      bytesOut.flush();
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
}

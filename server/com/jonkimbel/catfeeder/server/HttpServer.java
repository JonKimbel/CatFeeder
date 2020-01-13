package com.jonkimbel.catfeeder.server;

import java.io.*;
import java.net.Socket;
import java.util.Date;
import java.util.StringTokenizer;

public class HttpServer {
  private final Socket socket;
  private final BodyWriter bodyWriter;

  public interface BodyWriter {
    String getBodyForRequest(String requestPath) throws IOException;
  }

  public static Thread threadForConnection(Socket socket, BodyWriter bodyWriter) {
    HttpServer server = new HttpServer(socket, bodyWriter);
    return new Thread(server::connect);
  }

  private HttpServer(Socket socket, BodyWriter bodyWriter) {
    this.socket = socket;
    this.bodyWriter = bodyWriter;
  }

  private void connect() {
    BufferedReader in = null;
    PrintWriter printWriter = null;
    try {
      in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      printWriter = new PrintWriter(socket.getOutputStream());

      handle(in, printWriter);
    } catch (IOException e) {
      System.err.printf("%s - server error: %s\n", new Date(), e);
    } finally {
      try {
        in.close();
        printWriter.close();
        socket.close();
      } catch (Exception e) {
        System.err.printf("%s - couldn't close stream: %s\n", new Date(), e.getMessage());
      }
    }
  }

  private void handle(BufferedReader in, PrintWriter printWriter) throws IOException {
    StringTokenizer tokenizer = new StringTokenizer(in.readLine());
    String method = tokenizer.nextToken().toUpperCase();

    if ("GET".equals(method)) {
      System.out.printf("%s - request OK.\n", new Date());

      // TODO: pass request path
      // TODO [CLEANUP]: method to BodyWriter.
      String body = bodyWriter.getBodyForRequest("/");

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
}

package com.jonkimbel.catfeeder.server;

import java.io.*;
import java.net.Socket;
import java.util.Date;
import java.util.StringTokenizer;

public class HttpServer {
  private final Socket socket;
  private final RequestHandler requestHandler;

  public interface RequestHandler {
    Response handleRequest(Http.Method method, String requestPath) throws IOException;
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
    Http.Method method = Http.Method.fromString(tokenizer.nextToken().toUpperCase());
    String requestPath = tokenizer.nextToken();

    Response response = requestHandler.handleRequest(method, requestPath);

    writeHeader(printWriter, response.getResponseCode(), /* contentLength = */ response.getBody().length());
    printWriter.print(response.getBody());
    printWriter.flush();
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

package com.jonkimbel.catfeeder.backend.server;

import java.util.List;
import java.util.StringTokenizer;

public class HttpHeader {
  public final Http.Method method;
  public final String requestPath;
  public final String host;
  public final String contentLength;
  public final String transferEncoding;

  private HttpHeader(Http.Method method,
      String requestPath,
      String host,
      String contentLength,
      String transferEncoding) {
    this.method = method;
    this.requestPath = requestPath;
    this.host = host;
    this.contentLength = contentLength;
    this.transferEncoding = transferEncoding;
  }

  public static HttpHeader fromLines(List<String> lines) {
    // TODO: finish this.

    // TODO: handle malformed requests.

    String host;
    String contentLength;
    String transferEncoding;

    for (String line : lines) {
      if (line.startsWith("Host")) {
        host = line;
      } else if (line.startsWith("Content Length")) {
        contentLength = line;
      } else if (line.startsWith("Transfer Encoding")) {
        transferEncoding = line;
      }
    }

    Http.Method method = Http.Method.UNKNOWN;
    String requestPath = "";
    if (lines.size() > 0) {
      StringTokenizer tokenizer = new StringTokenizer(lines.get(0));
      method = Http.Method.fromString(tokenizer.nextToken().toUpperCase());
      requestPath = tokenizer.nextToken();
    }

    return new HttpHeader(
        method,
        requestPath,
        host,
        contentLength,
        transferEncoding);
  }
}

package com.jonkimbel.catfeeder.backend.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.checkerframework.checker.nullness.qual.Nullable;

public class HttpHeader {
  public final Http.Method method;
  public final String path;
  public final String host;
  public final String transferEncoding;
  public final Integer contentLength;

  private enum HeaderPart {
    METHOD,
    PATH,
    HTTP_VERSION,
    HOST,
    TRANSFER_ENCODING,
    CONTENT_LENGTH,
  }

  private enum HeaderLine {
    HOST("^Host\\s(.*)", HeaderPart.HOST),
    // TODO: is this standard? are there multiple ways to do this?
    CONTENT_LENGTH("^Content-Length:\\s(.*)", HeaderPart.CONTENT_LENGTH),
    TRANSFER_ENCODING("^Transfer Encoding:\\s(.*)", HeaderPart.TRANSFER_ENCODING),
    METHOD_AND_PATH("^(GET|HEAD|POST|PUT|DELETE|CONNECT|OPTIONS|TRACE)\\s(\\S*)\\s(.*)",
        HeaderPart.METHOD, HeaderPart.PATH, HeaderPart.HTTP_VERSION),
    UNKNOWN(""),
    ;

    private final Pattern regexPattern;
    private final List<HeaderPart> partNames;

    HeaderLine(String regexPattern, HeaderPart... partNames) {
      this.regexPattern = Pattern.compile(regexPattern);
      this.partNames = Arrays.asList(partNames);
    }

    // TODO [CLEANUP]: replace with ImmutableList.
    @Nullable
    public String getPart(String lineInHeader, HeaderPart partName) {
      int partNameGroupNumber = partNames.indexOf(partName) + 1; // Regex groups are 1-based.
      if (partNameGroupNumber == -1) {
        throw new IllegalArgumentException(
            String.format("HeaderLine.%s never contains part %s", this, partName));
      }

      Matcher matcher = regexPattern.matcher(lineInHeader);
      // lineInHeader Content-Length: 0 is not a representation of HeaderLine.CONTENT_LENGTH
      if (!matcher.matches() || partNameGroupNumber >= matcher.groupCount()) {
        throw new IllegalStateException(
            String.format("lineInHeader %s is not a representation of HeaderLine.%s",
             lineInHeader, this));
      }

      return matcher.group(partNameGroupNumber);
    }

    public boolean matches(String lineInHeader) {
      return regexPattern.matcher(lineInHeader).matches();
    }

    public static final HeaderLine fromString(String lineInHeader) {
      for (HeaderLine headerLine : values()) {
        if (headerLine.matches(lineInHeader)) {
          return headerLine;
        }
      }
      return UNKNOWN;
    }
  }

  private HttpHeader(Http.Method method,
      String path,
      String host,
      String transferEncoding,
      Integer contentLength) {
    this.method = method;
    this.path = path;
    this.host = host;
    this.contentLength = contentLength;
    this.transferEncoding = transferEncoding;
  }

  // TODO [CLEANUP]: use AutoValue.
  public static class Builder {
    private Http.Method method;
    private String path;
    private String host;
    private String transferEncoding;
    private Integer contentLength;

    public Builder setMethod(Http.Method method) {
      this.method = method;
      return this;
    }

    public Builder setPath(String path) {
      this.path = path;
      return this;
    }

    public Builder setHost(String host) {
      this.host = host;
      return this;
    }

    public Builder setTransferEncoding(String transferEncoding) {
      this.transferEncoding = transferEncoding;
      return this;
    }

    public Builder setContentLength(Integer contentLength) {
      this.contentLength = contentLength;
      return this;
    }

    public HttpHeader build() {
      return new HttpHeader(method, path, host, transferEncoding, contentLength);
    }
  }


  public static HttpHeader fromLines(List<String> lines) {
    Builder builder = new Builder();

    for (String line : lines) {
      HeaderLine headerLine = HeaderLine.fromString(line);
      switch (headerLine) {
        case HOST:
          builder.setHost(headerLine.getPart(line, HeaderPart.HOST));
          break;

        case CONTENT_LENGTH:
          Integer contentLength = null;
          try {
            Integer.parseInt(headerLine.getPart(line, HeaderPart.CONTENT_LENGTH));
          } catch (NumberFormatException e) { } // TODO [CLEANUP]: log.
          builder.setContentLength(contentLength);
          break;

        case TRANSFER_ENCODING:
          builder.setTransferEncoding(headerLine.getPart(line, HeaderPart.TRANSFER_ENCODING));
          break;

        case METHOD_AND_PATH:
          builder.setMethod(Http.Method.fromString(headerLine.getPart(line, HeaderPart.METHOD)));
          builder.setPath(headerLine.getPart(line, HeaderPart.PATH));
          break;

        case UNKNOWN:
          break;
      }
    }
    return builder.build();
  }
}

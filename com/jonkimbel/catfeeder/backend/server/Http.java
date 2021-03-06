package com.jonkimbel.catfeeder.backend.server;

public interface Http {
  enum ResponseCode {
    OK("200 OK"),
    FOUND("302 Found"),
    NOT_FOUND("404 Not Found"),
    NOT_IMPLEMENTED("501 Not Implemented"),
    ;

    private final String stringRepresentation;

    ResponseCode(String stringRepresentation) {
      this.stringRepresentation = stringRepresentation;
    }

    @Override
    public String toString() {
      return stringRepresentation;
    }
  }

  enum ContentType {
    HTML("text/html"),
    PROTOCOL_BUFFER("application/x-protobuf"),
    ;

    private final String stringRepresentation;

    ContentType(String stringRepresentation) {
      this.stringRepresentation = stringRepresentation;
    }

    @Override
    public String toString() {
      return stringRepresentation;
    }
  }

  enum Method {
    GET,
    POST,
    UNKNOWN,
    ;

    public static Method fromString(String stringMethod) {
      stringMethod = stringMethod.toLowerCase();
      if (stringMethod.equals("get")) {
        return GET;
      } else if (stringMethod.equals("post")) {
        return POST;
      }
      return UNKNOWN;
    }
  }
}

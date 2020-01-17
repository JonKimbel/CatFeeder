package com.jonkimbel.catfeeder.server;

public class Response {
  private String body;
  private Http.ResponseCode responseCode;

  public String getBody() {
    return body;
  }

  public Http.ResponseCode getResponseCode() {
    return responseCode;
  }

  public static Builder builder() {
    return new Builder();
  }

  private Response(String body, Http.ResponseCode responseCode) {
    this.body = body;
    this.responseCode = responseCode;
  }

  public static class Builder {
    private String body = "";
    private Http.ResponseCode responseCode = Http.ResponseCode.NOT_IMPLEMENTED;

    public Builder setBody(String body) {
      this.body = body;
      return this;
    }

    public Builder setResponseCode(Http.ResponseCode responseCode) {
      this.responseCode = responseCode;
      return this;
    }

    public Response build() {
      return new Response(body, responseCode);
    }

    private Builder() {}
  }
}

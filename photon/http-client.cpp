#include "http-client.h"

const char *HEADER_METHOD_FORMAT_STRING = "GET %s HTTP/1.0";
const char *HEADER_HOST_FORMAT_STRING = "Host: %s";
const char *HEADER_CONTENT_LENGTH_FORMAT_STRING = "Content-Length: %d";

HttpClient::HttpClient(const char* domain, const char* path, int port) {
  // Allocate one extra character for the null terminator (\0).
  this->domain = (char *) malloc((1 + strlen(domain)) * sizeof(char));
  this->path = (char *) malloc((1 + strlen(path)) * sizeof(char));
  strcpy(this->domain, domain);
  strcpy(this->path, path);
  this->port = port;
}

HttpClient::~HttpClient() {
  free(domain);
  free(path);
}

bool HttpClient::connect() {
  return _tcpClient.connect(domain, port);
}

void HttpClient::disconnect() {
  _tcpClient.stop();
}

void HttpClient::sendRequest() {
  ArrayList<uint8_t> emptyBody;
  sendRequest(&emptyBody);
}

// TODO [V2]: Add timeouts to TCP write/read calls.

void HttpClient::sendRequest(ArrayList<uint8_t>* body) {
  // Create the formatted strings for the HTTP request header.
  // NOTE: the lengths are "-1" because the format placeholders (%s) will be
  // consumed, but we need to add one space for the null-terminator (\0).
  size_t header_method_length =
      strlen(path) + strlen(HEADER_METHOD_FORMAT_STRING) - 1;
  char *header_method = (char *) malloc(header_method_length * sizeof(char));
  snprintf(header_method, header_method_length,
      HEADER_METHOD_FORMAT_STRING, path);

  size_t header_host_length =
      strlen(domain) + strlen(HEADER_HOST_FORMAT_STRING) - 1;
  char *header_host = (char *) malloc(header_host_length * sizeof(char));
  snprintf(header_host, header_host_length,
      HEADER_HOST_FORMAT_STRING, domain);

  size_t content_length_digit_length = body->length == 0 ? 1 : floor(log10(body->length)) + 1;
  size_t header_content_length_length =
      content_length_digit_length + strlen(HEADER_CONTENT_LENGTH_FORMAT_STRING) - 1;
  char *header_content_length = (char *) malloc(header_content_length_length * sizeof(char));
  snprintf(header_content_length, header_content_length_length,
      HEADER_CONTENT_LENGTH_FORMAT_STRING, body->length);

  // Write the HTTP header to the TCP connection.
  _tcpClient.println(header_method);
  _tcpClient.println(header_host);
  _tcpClient.println(header_content_length);
  _tcpClient.println();
  // Write the body to the TCP connection.
  _tcpClient.write(body->data, body->length);

  free(header_method);
  free(header_host);
  free(header_content_length);
}

bool HttpClient::responseReady() {
  return _tcpClient.connected() && _tcpClient.available();
}

Status HttpClient::getResponse(ArrayList<uint8_t>* body) {
  body->clear();

  // Read HTTP header.
  Status status = _processHeader();
  if (status != HTTP_STATUS_OK) {
    return status;
  }

  // Read body of response.
  while(_tcpClient.connected()) {
    if (_tcpClient.available()) {
      body->add(_tcpClient.read());
    }
  }

  return status;
}

enum HeaderMajorSection {
  STATUS_LINE = 0,
  GENERAL_INFO = 1,
};

enum HeaderSection {
  HTTP_VERSION = 0,
  STATUS_CODE = 1,
  REASON_PHRASE = 2,
};

// Based on these docs: https://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html
Status HttpClient::_processHeader() {
  int headerMajorSection = 0;
  int headerSection = 0;
  uint8_t statusCode[] = "NNN";
  int headerEndSequence = 0;

  while(_tcpClient.connected()) {
    if (!_tcpClient.available()) {
      continue;
    }

    uint8_t c = _tcpClient.read();
    // Look for CRLF to delimit sections of the header, and a double-CRLF to
    // delimit the end of the header.
    if (c == '\r' && headerEndSequence % 2 == 0) {
      headerEndSequence++;
    } else if (c == '\n' && headerEndSequence % 2 == 1) {
      headerEndSequence++;
    } else {
      headerEndSequence = 0;
    }

    // Process sections of the header.
    if (headerEndSequence == 2) {
      if (headerMajorSection == STATUS_LINE) {
        headerMajorSection++;
      }
      headerSection++;
    } else if (headerEndSequence >= 4) {
      // The header was processed successfully.
      return _getStatusFromCode(statusCode);
    }

    // Process sections of the status line, which are space-delimited.
    if (c == ' ' && headerMajorSection == STATUS_LINE) {
      headerSection++;
      // Read status code.
      if (headerSection == STATUS_CODE) {
        _tcpClient.read(statusCode, 3);
      }
    }
  }

  // Unexpectedly disconnected.
  return HTTP_STATUS_CLIENT_ERROR;
}

Status HttpClient::_getStatusFromCode(const uint8_t* statusCode) {
  if (_statusCodeEquals(statusCode, "200")) {
    return HTTP_STATUS_OK;
  } else if (_statusCodeEquals(statusCode, "400")) {
    return HTTP_STATUS_BAD_REQUEST;
  } else if (_statusCodeEquals(statusCode, "500")) {
    return HTTP_STATUS_INTERNAL_SERVER_ERROR;
  } else if (_statusCodeEquals(statusCode, "503")) {
    return HTTP_STATUS_SERVICE_UNAVAILABLE;
  }
  return HTTP_STATUS_UNKNOWN;
}

bool HttpClient::_statusCodeEquals(const uint8_t* actual, const char* expected) {
  for (int i = 0; i < 3; i++) {
    if ((char)actual[i] != expected[i]) {
      return false;
    }
  }
  return true;
}

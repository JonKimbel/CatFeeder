#include "http-client.h"

const char *REQUEST_HEADER_FORMAT_STRING = "GET %s HTTP/1.0";
const char *REQUEST_LINE_FORMAT_STRING = "Host: %s";
const char *ENTITY_HEADER = "Content-Length: 0";

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

void HttpClient::sendRequest() {
  // Create the formatted strings for the HTTP request header.
  // NOTE: the lengths are "-1" because the format placeholders (%s) will be
  // consumed, but we need to add one space for the null-terminator (\0).
  size_t request_header_length =
      strlen(path) + strlen(REQUEST_HEADER_FORMAT_STRING) - 1;
  char *request_header = (char *) malloc(request_header_length * sizeof(char));
  snprintf(request_header, request_header_length,
      REQUEST_HEADER_FORMAT_STRING, path);

  size_t request_line_length =
      strlen(domain) + strlen(REQUEST_LINE_FORMAT_STRING) - 1;
  char *request_line = (char *) malloc(request_line_length * sizeof(char));
  snprintf(request_line, request_line_length,
      REQUEST_LINE_FORMAT_STRING, domain);

  // Write the HTTP header to the TCP connection, with an empty body.
  _tcpClient.println(request_header);
  _tcpClient.println(request_line);
  _tcpClient.println(ENTITY_HEADER);
  _tcpClient.println();

  free(request_header);
  free(request_line);
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
  _tcpClient.stop();

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

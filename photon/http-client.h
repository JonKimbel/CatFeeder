// A wrapper for around TCPClient that makes HTTP requests. Example usage:
//
// HttpClient httpClient("google.com", "/search?q=banana", 80);
// ArrayList<uint8_t> responseBuffer(/* initialLength = */ 20);
//
// if (httpClient.connect()) {
//   httpClient.sendRequest();
//   httpClient.getResponse(&responseBuffer);
//
//   for (int i = 0; i < responseBuffer.length; i++) {
//     print(responseBuffer.data[i]);
//   }
// }

#ifndef HTTP_CLIENT_H
#define HTTP_CLIENT_H

#include <Wire.h>
#include "array-list.h"
#include "spark_wiring_string.h"
#include "spark_wiring_tcpclient.h"
#include "spark_wiring_usbserial.h"

// A mix of HTTP status codes and failure codes specific to this implementation.
enum Status {
  // HttpClient messed up.
  HTTP_STATUS_CLIENT_ERROR = 0,

  // The server returned an error status not represented in this enum.
  HTTP_STATUS_UNKNOWN = 1,

  HTTP_STATUS_OK = 200,

  // The request was formatted incorrectly. Possibly HttpClient's fault.
  HTTP_STATUS_BAD_REQUEST = 400,

  HTTP_STATUS_INTERNAL_SERVER_ERROR = 500,

  HTTP_STATUS_SERVICE_UNAVAILABLE = 503,
};

class HttpClient {
  public:
    char* domain;
    char* path;
    int port;

    HttpClient(const char* domain, const char* path, int port);
    ~HttpClient();

    // Starts a TCP connection with the server. Required for sending requests.
    bool connect();

    // Sends an HTTP request over the TCP connection. connect() must be called
    // first.
    void sendRequest();

    bool responseReady();

    // Reads the response from the server, strips out the header, and writes the
    // body of the response to the given ArrayList. connect() and sendRequest()
    // must be called first.
    // The provided ArrayList must be un-initialized or cleared.
    Status getResponse(ArrayList<uint8_t>* body);

  private:
    TCPClient _tcpClient;

    Status _processHeader();
    Status _getStatusFromCode(const uint8_t* statusCode);
    bool _statusCodeEquals(const uint8_t* actual, const char* expected);

};

#endif // ARRAY_LIST_H

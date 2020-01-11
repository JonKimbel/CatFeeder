#include <Wire.h>
#include <stdlib.h>
#include <pb_decode.h>
#include "array-list.h"
#include "bus-boy.pb.h"
#include "http-client.h"
#include "LiquidCrystal_I2C.h"

// This file should #define BACKEND_DOMAIN. Defining a separate file allows me
// to hide my domain from source control via .gitignore.
#include "backend-info.h"

// Don't auto-connect to the Particle cloud. Speeds up testing.
// TODO: remove before deployment so firmware can be updated in the field.
SYSTEM_MODE(SEMI_AUTOMATIC);

////////////////////////////////////////////////////////////////////////////////
// STRUCTS.

// We define our own version of any message that contains a pb_callback_t so we
// can store the data after parsing-time. Without these, we couldn't save
// repeated fields or strings for later consumption.

typedef struct  {
  char *short_name;
  char *headsign;
} Route;

typedef struct {
  busboy_api_TimeFrame time_frame;
  char *message;
  busboy_api_ColorScheme color_scheme_override;
} TemporaryMessage;

////////////////////////////////////////////////////////////////////////////////
// METHOD DECLARATIONS.

// Utilities.
void format_arrival(busboy_api_Arrival *arrival, char* arrivalString, int arrivalStringLength);
void clear_response_data();

// Field-specific nanopb decoders.
bool decode_route(pb_istream_t *stream, const pb_field_t *field, void **arg);
bool decode_arrival(pb_istream_t *stream, const pb_field_t *field, void **arg);
bool decode_temporaryMessage(pb_istream_t *stream, const pb_field_t *field, void **arg);
bool decode_temporaryStyle(pb_istream_t *stream, const pb_field_t *field, void **arg);

// Generic nanopb decoders.
bool decode_string(pb_istream_t *stream, const pb_field_t *field, void **arg);

////////////////////////////////////////////////////////////////////////////////
// VARIABLES.

// Set the LCD address to 0x27 for a 20 char, 4 line display.
LiquidCrystal_I2C lcd(0x27,20,4);

// Set up the HTTP client for communication with the backend, getting data for
// the Northbound N 34th street stop.
HttpClient httpClient(BACKEND_DOMAIN, "/?stop=1_26860", 80);

// Buffer used to hold raw data from the server.
ArrayList<uint8_t> responseBuffer;

// Data parsed out of the response from the server.
busboy_api_DisplayedTime responseTime;
ArrayList<Route> routes;
ArrayList<busboy_api_Arrival> arrivals;
ArrayList<TemporaryMessage> temporaryMessages;
ArrayList<busboy_api_TemporaryStyle> temporaryStyles;

////////////////////////////////////////////////////////////////////////////////
// MAIN CODE.

void setup() {
  Serial.begin(9600);
  lcd.begin();
  lcd.backlight();

  lcd.clear();
  lcd.print("waiting for network");
  Cellular.on();
  Cellular.connect();
  while (!Cellular.ready()) {
    delay(100);
  }

  lcd.clear();
  lcd.print("connecting...");
}

void loop() {
  if (!httpClient.connect()) {
    lcd.clear();
    lcd.print("connection failed");
    return;
  }

  httpClient.sendRequest();
  Status status = httpClient.getResponse(&responseBuffer);
  if (status != HTTP_STATUS_OK) {
    lcd.clear();
    lcd.print("http error: ");
    lcd.print(status);
    return;
  }

  busboy_api_Response response = busboy_api_Response_init_default;
  response.route.funcs.decode = &decode_route;
  response.arrival.funcs.decode = &decode_arrival;
  response.temporary_message.funcs.decode = &decode_temporaryMessage;
  response.temporary_style.funcs.decode = &decode_temporaryStyle;

  clear_response_data();

  pb_istream_t stream = pb_istream_from_buffer(
      responseBuffer.data, responseBuffer.length);
  if (!pb_decode(&stream, busboy_api_Response_fields, &response)) {
    lcd.clear();
    lcd.print("proto decode error");
    return;
  }

  responseTime = response.time;

  lcd.clear();
  // NOTE: this is using some transitive dependency's "min" declaration,
  // which is pretty jank. Would be better if the app was in its own
  // namespace.
  for (int i = 0; i < min(arrivals.length, 4); i++) {
    int arrivalStringLength = 21; // LCD width + 1 for null char.
    char* arrivalString = (char*) malloc(arrivalStringLength * sizeof(char));
    format_arrival(&arrivals.data[i], arrivalString, arrivalStringLength);

    lcd.setCursor(0, i);
    lcd.print(arrivalString);

    free(arrivalString);
  }

  // Wait 60s before fetching new data.
  delay(60000);
}

////////////////////////////////////////////////////////////////////////////////
// UTILITY METHODS.

void format_arrival(busboy_api_Arrival *arrival, char* arrivalString, int arrivalStringLength) {
  int minutesToArrival = (int)(arrival->ms_to_arrival / 60000);
  Route *route = &routes.data[arrival->route_index];

  // Add minutesToArrival to string.
  snprintf(arrivalString, arrivalStringLength, "%dmin", minutesToArrival);

  // Add short name to string.
  snprintf(arrivalString, arrivalStringLength, "%s %s",
      arrivalString, route->short_name);

  // Add headsign to string.
  snprintf(arrivalString, arrivalStringLength, "%s %s",
      arrivalString, route->headsign);
}

void clear_response_data() {
  for (int i = 0; i < routes.length; i++) {
    free(routes.data[i].short_name);
    free(routes.data[i].headsign);
  }
  for (int i = 0; i < temporaryMessages.length; i++) {
    free(temporaryMessages.data[i].message);
  }

  routes.clear();
  arrivals.clear();
  temporaryMessages.clear();
  temporaryStyles.clear();
}

////////////////////////////////////////////////////////////////////////////////
// NANOPB DECODE CALLBACKS.

bool decode_route(pb_istream_t *stream, const pb_field_t *field, void **arg) {
  Route route;

  busboy_api_Route route_proto = busboy_api_Route_init_default;
  // pb_callback_t short_name;
  route_proto.short_name.funcs.decode = &decode_string;
  route_proto.short_name.arg = &route.short_name;
  // pb_callback_t headsign;
  route_proto.headsign.funcs.decode = &decode_string;
  route_proto.headsign.arg = &route.headsign;

  if (!pb_decode(stream, busboy_api_Route_fields, &route_proto)) {
    return false;
  }

  routes.add(route);
  return true;
}

bool decode_arrival(pb_istream_t *stream, const pb_field_t *field, void **arg) {
  busboy_api_Arrival arrival_proto = busboy_api_Arrival_init_default;
  if (!pb_decode(stream, busboy_api_Arrival_fields, &arrival_proto)) {
    return false;
  }

  arrivals.add(arrival_proto);
  return true;
}

bool decode_temporaryMessage(pb_istream_t *stream, const pb_field_t *field, void **arg) {
  TemporaryMessage temporaryMessage;

  busboy_api_TemporaryMessage temporaryMessage_proto = busboy_api_TemporaryMessage_init_default;
  // pb_callback_t message;
  temporaryMessage_proto.message.funcs.decode = &decode_string;
  temporaryMessage_proto.message.arg = &temporaryMessage.message;

  if (!pb_decode(stream, busboy_api_TemporaryMessage_fields, &temporaryMessage_proto)) {
    return false;
  }

  temporaryMessage.time_frame = temporaryMessage_proto.time_frame;
  temporaryMessage.color_scheme_override = temporaryMessage_proto.color_scheme_override;

  temporaryMessages.add(temporaryMessage);
  return true;
}

bool decode_temporaryStyle(pb_istream_t *stream, const pb_field_t *field, void **arg) {
  busboy_api_TemporaryStyle temporaryStyle_proto = busboy_api_TemporaryStyle_init_default;
  if (!pb_decode(stream, busboy_api_TemporaryStyle_fields, &temporaryStyle_proto)) {
    return false;
  }

  temporaryStyles.add(temporaryStyle_proto);
  return true;
}

// The pb_callback_t.arg should be a pointer to an uninitialized char*.
bool decode_string(pb_istream_t *stream, const pb_field_t *field, void **arg) {
  if (sizeof(char) != sizeof(uint8_t)) {
    return false;
  }
  // Allocate string space, write the null terminator.
  char* buffer = (char*) malloc((stream->bytes_left + 1) * sizeof(char));
  buffer[stream->bytes_left] = '\0';

  if (!pb_read(stream, (uint8_t*) buffer, stream->bytes_left)) {
    return false;
  }

  // Overwrite the char** set as the arg of the pb_callback_t.
  // We cast the 'arg' passed to this method to a char*** because nanopb adds an
  // additional layer of indirection on top of the ones we need...
  **((char***)arg) = buffer;
  return true;
}

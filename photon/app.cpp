#include <Wire.h>
#include <stdlib.h>
#include <pb_decode.h>
#include "array-list.h"
// TODO: figure out incompatibility here - seems the photon already has nanopb.
#include "cat_feeder.pb.h"
#include "http-client.h"

// This file should #define BACKEND_DOMAIN. Defining a separate file allows me
// to hide my domain from source control via .gitignore.
#include "backend-info.h"

////////////////////////////////////////////////////////////////////////////////
// CONSTANTS.

// TODO: add a big cap across the 5V line to prevent brownouts? Or get a power
// supply.

// 400Hz = 2500us wavelength.
// Just needs to exceed the MG996R's 2100us max pulse length.
#define SERVO_PWM_FREQ 400

// 900us min pulse length / 2500us PWM wavelength * 256 possible duty cycles
#define MIN_SERVO_DUTY_CYCLE 92
// 900us min pulse length / 2500us PWM wavelength * 256 possible duty cycles
#define MAX_SERVO_DUTY_CYCLE 215

#define SERVO_PIN D0

#define FAILURE_RETRY_DELAY_MS 1000

#define FOOD_DISPENSE_WAIT_TIME 3000

////////////////////////////////////////////////////////////////////////////////
// VARIABLES.

// Set up the HTTP client for communication with the backend, getting info on
// how and when the cat should be fed.
HttpClient httpClient(BACKEND_DOMAIN, "/photon", 80);

// Buffer used to hold raw data from the server.
ArrayList<uint8_t> responseBuffer;

////////////////////////////////////////////////////////////////////////////////
// MAIN CODE.

void setup() {
  pinMode(SERVO_PIN, OUTPUT);

  RGB.control(true);
}

void loop() {
  // Dim LED while attempting to make connection.
  RGB.brightness(64);

  if (!httpClient.connect()) {
    // Couldn't connect to backend. Red.
    RGB.color(/* red = */ 255, /* green = */ 0, /* blue = */ 0);
    RGB.brightness(255);
    delay(FAILURE_RETRY_DELAY_MS);
    return;
  }

  responseBuffer.clear();
  httpClient.sendRequest();
  Status status = httpClient.getResponse(&responseBuffer);

  if (status != HTTP_STATUS_OK) {
    // Connected to backend but got bad response. Orange.
    RGB.color(/* red = */ 252, /* green = */ 60, /* blue = */ 3);
    RGB.brightness(255);
    delay(FAILURE_RETRY_DELAY_MS);
    return;
  }

  // Got successful response from backend. Green.
  RGB.color(/* red = */ 0, /* green = */ 255, /* blue = */ 0);
  RGB.brightness(255);

  // Decode response.
  catfeeder_api_EmbeddedResponse response = catfeeder_api_EmbeddedResponse_init_default;
  pb_istream_t stream = pb_istream_from_buffer(
      responseBuffer.data, responseBuffer.length);
  if (!pb_decode(&stream, catfeeder_api_EmbeddedResponse_fields, &response)) {
    // Got successful response from the server, but the body was malformed.
    // Purple.
    RGB.color(/* red = */ 255, /* green = */ 0, /* blue = */ 255);
    RGB.brightness(255);
    delay(FAILURE_RETRY_DELAY_MS);
    return;
  }

  if (response.has_delay_until_next_feeding_ms &&
      response.delay_until_next_feeding_ms <
          response.delay_until_next_check_in_ms) {
    delay(response.delay_until_next_feeding_ms);
    // Dispense food.
    // TODO: handle multiple scoops of food.
    analogWrite(/* pin = */ SERVO_PIN, /* value = */ MIN_SERVO_DUTY_CYCLE, /* frequency = */ SERVO_PWM_FREQ);
    delay(FOOD_DISPENSE_WAIT_TIME);
    analogWrite(/* pin = */ SERVO_PIN, /* value = */ MAX_SERVO_DUTY_CYCLE, /* frequency = */ SERVO_PWM_FREQ);

    // TODO: do this math in a systemic way.
    delay(response.delay_until_next_check_in_ms -
        response.delay_until_next_feeding_ms -
            FOOD_DISPENSE_WAIT_TIME);
  } else {
    // TODO: handle cases where the server is unresponsive but we've been told
    // to feed the cat. Feed the cat, don't just wait around for the server to
    // get fixed.

    // Wait however long the server told us to before checking in again.
    delay(response.delay_until_next_check_in_ms);
  }
}

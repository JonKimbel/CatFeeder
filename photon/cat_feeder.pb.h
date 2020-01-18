/* Automatically generated nanopb header */
/* Generated by nanopb-0.3.9.4 at Sat Jan 18 12:42:36 2020. */

#ifndef PB_CATFEEDER_API_CAT_FEEDER_PB_H_INCLUDED
#define PB_CATFEEDER_API_CAT_FEEDER_PB_H_INCLUDED
#include <pb.h>

/* @@protoc_insertion_point(includes) */
#if PB_PROTO_HEADER_VERSION != 30
#error Regenerate this file with the current version of nanopb generator.
#endif

#ifdef __cplusplus
extern "C" {
#endif

/* Struct definitions */
typedef struct _catfeeder_api_EmbeddedRequest {
    bool has_time_since_last_feeding_ms;
    uint64_t time_since_last_feeding_ms;
/* @@protoc_insertion_point(struct:catfeeder_api_EmbeddedRequest) */
} catfeeder_api_EmbeddedRequest;

typedef struct _catfeeder_api_EmbeddedResponse {
    bool has_delay_until_next_check_in_ms;
    uint64_t delay_until_next_check_in_ms;
    bool has_delay_until_next_feeding_ms;
    uint64_t delay_until_next_feeding_ms;
/* @@protoc_insertion_point(struct:catfeeder_api_EmbeddedResponse) */
} catfeeder_api_EmbeddedResponse;

/* Default values for struct fields */
extern const uint64_t catfeeder_api_EmbeddedResponse_delay_until_next_check_in_ms_default;

/* Initializer values for message structs */
#define catfeeder_api_EmbeddedRequest_init_default {false, 0}
#define catfeeder_api_EmbeddedResponse_init_default {false, 60000ull, false, 0}
#define catfeeder_api_EmbeddedRequest_init_zero  {false, 0}
#define catfeeder_api_EmbeddedResponse_init_zero {false, 0, false, 0}

/* Field tags (for use in manual encoding/decoding) */
#define catfeeder_api_EmbeddedRequest_time_since_last_feeding_ms_tag 1
#define catfeeder_api_EmbeddedResponse_delay_until_next_check_in_ms_tag 1
#define catfeeder_api_EmbeddedResponse_delay_until_next_feeding_ms_tag 2

/* Struct field encoding specification for nanopb */
extern const pb_field_t catfeeder_api_EmbeddedRequest_fields[2];
extern const pb_field_t catfeeder_api_EmbeddedResponse_fields[3];

/* Maximum encoded size of messages (where known) */
#define catfeeder_api_EmbeddedRequest_size       11
#define catfeeder_api_EmbeddedResponse_size      22

/* Message IDs (where set with "msgid" option) */
#ifdef PB_MSGID

#define CAT_FEEDER_MESSAGES \


#endif

#ifdef __cplusplus
} /* extern "C" */
#endif
/* @@protoc_insertion_point(eof) */

#endif

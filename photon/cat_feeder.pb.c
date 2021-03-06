/* Automatically generated nanopb constant definitions */
/* Generated by nanopb-0.3.9.4 at Sat Feb 01 15:21:16 2020. */

#include "cat_feeder.pb.h"

/* @@protoc_insertion_point(includes) */
#if PB_PROTO_HEADER_VERSION != 30
#error Regenerate this file with the current version of nanopb generator.
#endif

const uint64_t catfeeder_api_EmbeddedResponse_delay_until_next_check_in_ms_default = 10000ull;


const pb_field_t catfeeder_api_EmbeddedRequest_fields[2] = {
    PB_FIELD(  1, UINT64  , OPTIONAL, STATIC  , FIRST, catfeeder_api_EmbeddedRequest, time_since_last_feeding_ms, time_since_last_feeding_ms, 0),
    PB_LAST_FIELD
};

const pb_field_t catfeeder_api_EmbeddedResponse_fields[5] = {
    PB_FIELD(  1, UINT64  , OPTIONAL, STATIC  , FIRST, catfeeder_api_EmbeddedResponse, delay_until_next_check_in_ms, delay_until_next_check_in_ms, &catfeeder_api_EmbeddedResponse_delay_until_next_check_in_ms_default),
    PB_FIELD(  2, UINT64  , OPTIONAL, STATIC  , OTHER, catfeeder_api_EmbeddedResponse, delay_until_next_feeding_ms, delay_until_next_check_in_ms, 0),
    PB_FIELD(  3, UINT32  , OPTIONAL, STATIC  , OTHER, catfeeder_api_EmbeddedResponse, scoops_to_feed, delay_until_next_feeding_ms, 0),
    PB_FIELD(  4, BOOL    , OPTIONAL, STATIC  , OTHER, catfeeder_api_EmbeddedResponse, last_feeding_time_consumed, scoops_to_feed, 0),
    PB_LAST_FIELD
};


/* @@protoc_insertion_point(eof) */

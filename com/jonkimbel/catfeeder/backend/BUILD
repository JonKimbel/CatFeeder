# This package contains the backend for CatFeeder, a device which automatically feeds a cat.
#
# In addition to communicating with the embedded device via encoded protobuf, it also renders an HTML webpage to allow
# users to change settings.

load("@rules_java//java:defs.bzl", "java_proto_library")
load("@rules_proto//proto:defs.bzl", "proto_library")

java_binary(
    name = "backend",
    srcs = [
        "Backend.java",
        "FeedingTimeUpdater.java",
        "HttpBodyRenderer.java",
        "PreferencesUpdater.java",
        "ProtoBodyRenderer.java",
        "ActionDeterminer.java",
    ],
    main_class = "com.jonkimbel.catfeeder.backend.Backend",
    deps = [
        "//com/jonkimbel/catfeeder/backend/alert",
        "//com/jonkimbel/catfeeder/backend/server",
        "//com/jonkimbel/catfeeder/backend/server:map_parser",
        "//com/jonkimbel/catfeeder/backend/storage/api",
        "//com/jonkimbel/catfeeder/backend/template",
        "//com/jonkimbel/catfeeder/backend/time",
        "//com/jonkimbel/catfeeder/backend/proto:preferences_java_proto",
        "//api:cat_feeder_java_proto",
        "@maven//:org_checkerframework_checker_qual",
    ],
    visibility = ["//visibility:public"],
)

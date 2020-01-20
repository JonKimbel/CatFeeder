# This is a Bazel BUILD file. Reference: https://docs.bazel.build/versions/master/be/java.html
load("@rules_java//java:defs.bzl", "java_proto_library")
load("@rules_proto//proto:defs.bzl", "proto_library")

java_binary(
    name = "backend",
    srcs = ["Backend.java"],
    main_class = "com.jonkimbel.catfeeder.backend.Backend",
    deps = [
        "//com/jonkimbel/catfeeder/backend/server",
        "//com/jonkimbel/catfeeder/backend/server:query_parser",
        "//com/jonkimbel/catfeeder/backend/storage/api",
        "//com/jonkimbel/catfeeder/backend/template",
        "//com/jonkimbel/catfeeder/backend/time",
        "//com/jonkimbel/catfeeder/backend/proto:preferences_java_proto",
        "//api:cat_feeder_java_proto",
        "@maven//:org_checkerframework_checker_qual",
    ],
    resources = [
        "template.html",
    ],
    visibility = ["//visibility:public"],
)
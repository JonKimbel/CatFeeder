# This is a Bazel BUILD file. Reference: https://docs.bazel.build/versions/master/be/java.html
load("@rules_java//java:defs.bzl", "java_proto_library")
load("@rules_proto//proto:defs.bzl", "proto_library")

java_binary(
    name = "catfeeder",
    srcs = ["App.java"],
    main_class = "com.jonkimbel.catfeeder.App",
    deps = [
        ":library",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "library",
    srcs = [
        "Http.java",
        "HttpServer.java",
    ],
    resources = [
        "template.html",
    ],
    deps = [
        "//com/jonkimbel/catfeeder/storage",
        "//com/jonkimbel/catfeeder/template",
        "//com/jonkimbel/catfeeder/proto:preferences_java_proto",
        "@maven//:org_xerial_sqlite_jdbc",
        "@maven//:org_checkerframework_checker_qual",
    ],
)
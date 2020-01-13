# This is a Bazel BUILD file. Reference: https://docs.bazel.build/versions/master/be/java.html
load("@rules_java//java:defs.bzl", "java_proto_library")
load("@rules_proto//proto:defs.bzl", "proto_library")

java_binary(
    name = "catfeeder",
    srcs = ["App.java"],
    main_class = "com.jonkimbel.catfeeder.App",
    deps = [
        "//com/jonkimbel/catfeeder/server",
        "//com/jonkimbel/catfeeder/storage",
        "//com/jonkimbel/catfeeder/template",
        "//com/jonkimbel/catfeeder/proto:preferences_java_proto",
    ],
    resources = [
        "template.html",
    ],
    visibility = ["//visibility:public"],
)
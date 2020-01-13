# This is a Bazel BUILD file. Reference: https://docs.bazel.build/versions/master/be/java.html

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
        "Database.java",
        "Http.java",
        "HttpServer.java",
    ],
    resources = [
        "template.html",
    ],
    deps = [
        "@maven//:org_xerial_sqlite_jdbc",
    ],
)
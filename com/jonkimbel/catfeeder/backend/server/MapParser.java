package com.jonkimbel.catfeeder.backend.server;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MapParser {
  public static Map<String, String> parsePostBody(String body) {
    return parse(body, /* pairDelimiterRegex = */ "\r?\n",
        /* keyValueDelimiterRegex = */ "=");
  }

  public static Map<String, String> parseQueryString(String queryString) {
    return parse(queryString, /* pairDelimiterRegex = */ "&",
        /* keyValueDelimiterRegex = */ "=");
  }

  private static Map<String, String> parse(String query, String pairDelimiterRegex,
      String keyValueDelimiterRegex) {
    // TODO [V3]: Make immutable map.
    Map<String, String> keyValueMap = new HashMap<>();

    // Lowercase everything.
    query = query.toLowerCase();

    // Cut off everything before the question mark, including the question mark.
    if (query.contains("?")) {
      query = query.substring(query.indexOf("?") + 1);
    }

    for (String keyValuePair : query.split(pairDelimiterRegex)) {
      String[] keyAndValue = keyValuePair.split(keyValueDelimiterRegex);
      if (keyAndValue.length != 2) {
        System.err.printf("%s - unparsed query string argument:%s\n", new Date(), keyValuePair);
        continue;
      }
      keyValueMap.put(keyAndValue[0], keyAndValue[1]);
    }

    return keyValueMap;
  }
}

package com.jonkimbel.catfeeder.backend.server;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class QueryParser {
  public static Map<String, String> parseQuery(String query) {
    // TODO [V3]: Make immutable map
    Map<String, String> keyValueMap = new HashMap<>();

    // Lowercase everything.
    query = query.toLowerCase();

    // Cut off everything before the question mark, including the question mark.
    if (query.contains("?")) {
      query = query.substring(query.indexOf("?") + 1);
    }

    for (String keyValuePair : query.split("&")) {
      String[] keyAndValue = keyValuePair.split("=");
      if (keyAndValue.length != 2) {
        System.err.printf("%s - unparsed query string argument:%s\n", new Date(), keyValuePair);
        continue;
      }
      keyValueMap.put(keyAndValue[0], keyAndValue[1]);
    }

    return keyValueMap;
  }
}

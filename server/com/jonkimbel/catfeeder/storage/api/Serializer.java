package com.jonkimbel.catfeeder.storage.api;

import java.io.IOException;

public interface Serializer {
  Object deserialize(String path);

  void serialize(String filename, Object value);
}

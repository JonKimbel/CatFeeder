package com.jonkimbel.catfeeder.backend.storage.serializer;

public interface Serializer {
  Object deserialize(String path);

  void serialize(String filename, Object value);
}

package com.jonkimbel.catfeeder.backend.storage.serializer;

import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Date;

public class StringSerializer implements Serializer {
  @Override
  public Object deserialize(String path) {
    String passFromFile = null;

    try {
      passFromFile = Files.readString(LibraryDirectory.get().resolve(path));
    } catch (IOException | URISyntaxException e) {
      System.err.printf("%s - Could not read String from %s: %s\n", new Date(), path, e);
    }

    return passFromFile;
  }

  @Override
  public void serialize(String path, Object value) {
    try {
      Files.writeString(Paths.get(path), (String) value,
          StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException e) {
      System.err.printf("%s - Could not write String to %s: %s\n", new Date(), path, e);
    }
  }
}

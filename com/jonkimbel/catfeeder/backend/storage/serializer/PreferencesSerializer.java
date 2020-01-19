package com.jonkimbel.catfeeder.backend.storage.serializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.Preferences;

public class PreferencesSerializer implements Serializer {

  @Override
  public Object deserialize(String path) {
    Preferences prefsFromFile = null;
    try {
      prefsFromFile = Preferences.parseFrom(Files.readAllBytes(Paths.get(path)));
    } catch (IOException e) {
      // TODO [CLEANUP]: Log error.
    }

    if (prefsFromFile != null) {
      return prefsFromFile;
    }
    return Preferences.getDefaultInstance();
  }

  @Override
  public void serialize(String path, Object value) {
    if (!(value instanceof Preferences)) {
      return;
    }

    Preferences prefs = (Preferences) value;

    try {
    Files.write(Paths.get(path), prefs.toByteArray());
    } catch (IOException e) {
      // TODO [CLEANUP]: Log error.
    }
  }
}

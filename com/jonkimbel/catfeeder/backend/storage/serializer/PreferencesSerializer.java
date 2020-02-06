package com.jonkimbel.catfeeder.backend.storage.serializer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.Preferences;

public class PreferencesSerializer implements Serializer {

  @Override
  public Object deserialize(String path) {
    Preferences prefsFromFile = null;
    try {
      Path pathToPreferencesFile = LibraryDirectory.get().resolve(path);
      prefsFromFile = Preferences.parseFrom(Files.readAllBytes(pathToPreferencesFile));
    } catch (IOException | URISyntaxException e) {
      System.err.printf("%s - Could not read Preferences from %s:%s\n", new Date(), path, e);
    }

    if (prefsFromFile != null) {
      return prefsFromFile;
    }
    return Preferences.getDefaultInstance();
  }

  @Override
  public void serialize(String path, Object value) {
    Preferences prefs = (Preferences) value;

    try {
      Path pathToPreferencesFile = LibraryDirectory.get().resolve(path);
      Files.write(pathToPreferencesFile, prefs.toByteArray());
    } catch (IOException | URISyntaxException e) {
      System.err.printf("%s - Could not write Preferences from %s:%s\n", new Date(), path, e);
    }
  }
}

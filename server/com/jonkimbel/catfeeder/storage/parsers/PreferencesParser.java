package com.jonkimbel.catfeeder.storage.parsers;

import com.jonkimbel.catfeeder.storage.api.Parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.jonkimbel.catfeeder.proto.PreferencesOuterClass.Preferences;

public class PreferencesParser implements Parser {

  @Override
  public Object parse(String path) {
    Preferences prefsFromFile = null;
    try {
      prefsFromFile = Preferences.parseFrom(Files.readAllBytes(Paths.get(path)));
    } catch (IOException e) {
    }

    if (prefsFromFile != null) {
      return prefsFromFile;
    }

    return Preferences.getDefaultInstance();
  }
}

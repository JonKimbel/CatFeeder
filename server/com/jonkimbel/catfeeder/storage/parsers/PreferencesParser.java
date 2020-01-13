package com.jonkimbel.catfeeder.storage.parsers;

import com.jonkimbel.catfeeder.storage.api.Parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.jonkimbel.catfeeder.proto.PreferencesOuterClass.Preferences;

public class PreferencesParser implements Parser {

  @Override
  public Object parse(File file) throws IOException {
    return Preferences.parseFrom(Files.readAllBytes(file.toPath()));
  }
}

package com.jonkimbel.catfeeder.backend.storage.serializer;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

public class LibraryDirectory {
  public static Path get() throws URISyntaxException {
    URI classUri =
        StringSerializer.class.getProtectionDomain().getCodeSource().getLocation().toURI();
    return new File(classUri).getParentFile().toPath();
  }
}

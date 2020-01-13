package com.jonkimbel.catfeeder.storage.api;

import java.io.File;
import java.io.IOException;

public interface Parser {
  Object parse(File file) throws IOException;
}

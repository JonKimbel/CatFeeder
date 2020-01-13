package com.jonkimbel.catfeeder.storage.api;

import java.io.IOException;

public interface Parser {
  Object parse(String path);
}

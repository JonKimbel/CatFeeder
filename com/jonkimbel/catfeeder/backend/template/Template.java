package com.jonkimbel.catfeeder.backend.template;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public enum Template {
  INDEX("/com/jonkimbel/catfeeder/backend/template/data/index.html")
  ;

  private final String filePath;

  private Template(String filePath) {
    this.filePath = filePath;
  }

  @Override
  public String toString() {
    try {
      return new String(getClass().getResourceAsStream(filePath).readAllBytes(),
          StandardCharsets.UTF_8);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }
}

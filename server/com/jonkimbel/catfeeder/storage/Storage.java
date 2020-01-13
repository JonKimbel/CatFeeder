package com.jonkimbel.catfeeder.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import com.jonkimbel.catfeeder.storage.api.Parser;
import com.jonkimbel.catfeeder.storage.parsers.PreferencesParser;
import org.checkerframework.checker.nullness.qual.Nullable;

public class Storage {
  public enum Item {
    PREFERENCES("preferences.textpb", new PreferencesParser()),
    ;

    public final String filename;
    private final Parser parser;

    private Item(String filename, Parser parser) {
      this.filename = filename;
      this.parser = parser;
    }
  }

  private static Storage storage = new Storage();

  private final Map<Item, Object> cache = new HashMap<>();

  private Storage() {
  }

  public static synchronized Storage getStorage() {
    if (storage == null) {
      storage = new Storage();
    }
    return storage;
  }

  @Nullable
  public Object getItemBlocking(Item item) {
    if (cache.containsKey(item)) {
      return cache.get(item);
    }

    Object itemFromDisk = null;
    try {
      itemFromDisk = item.parser.parse(new File(item.filename));
    } catch (IOException e) {
    }

    if (itemFromDisk != null) {
      cache.put(item, itemFromDisk);
    }

    return itemFromDisk;
  }

  public void setItem(Item item, Object value) {
    cache.put(item, value);

  }
}

package com.jonkimbel.catfeeder.storage;

import java.util.HashMap;
import java.util.Map;

import com.jonkimbel.catfeeder.storage.api.Serializer;
import com.jonkimbel.catfeeder.storage.parsers.PreferencesSerializer;
import org.checkerframework.checker.nullness.qual.Nullable;

public class Storage {
  public enum Item {
    PREFERENCES("preferences.textpb", new PreferencesSerializer()),
    ;

    public final String filename;
    private final Serializer serializer;

    private Item(String filename, Serializer serializer) {
      this.filename = filename;
      this.serializer = serializer;
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

    Object itemFromDisk = item.serializer.deserialize(item.filename);

    if (itemFromDisk != null) {
      cache.put(item, itemFromDisk);
    }

    return itemFromDisk;
  }

  // TODO [CLEANUP]: synchronize to avoid race conditions
  // TODO [CLEANUP]: write to disk on background thread

  public void setItemBlocking(Item item, Object value) {
    cache.put(item, value);
    item.serializer.serialize(item.filename, value);
  }
}

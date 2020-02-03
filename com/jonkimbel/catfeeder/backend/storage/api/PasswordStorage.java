package com.jonkimbel.catfeeder.backend.storage.api;

import com.jonkimbel.catfeeder.backend.storage.Storage;
import org.checkerframework.checker.nullness.qual.Nullable;

public class PasswordStorage {
  private PasswordStorage() {}

  public static @Nullable String get() {
    Object password = Storage.getStorage().getItemBlocking(Storage.Item.PASSWORD);
    if (password == null) {
      return null;
    }

    return (String) password;
  }
}

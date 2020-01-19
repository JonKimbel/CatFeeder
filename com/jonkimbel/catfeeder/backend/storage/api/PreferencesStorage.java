package com.jonkimbel.catfeeder.backend.storage.api;

import com.jonkimbel.catfeeder.backend.storage.Storage;

import com.jonkimbel.catfeeder.backend.proto.PreferencesOuterClass.Preferences;

public class PreferencesStorage {
  private PreferencesStorage() {}

  public static Preferences get() {
    return (Preferences) Storage.getStorage().getItemBlocking(Storage.Item.PREFERENCES);
  }

  public static void set(Preferences newPreferences) {
    Storage.getStorage().setItemBlocking(
        Storage.Item.PREFERENCES,
        newPreferences);
  }
}

package com.jonkimbel.catfeeder.backend;

import com.jonkimbel.catfeeder.backend.server.Http;
import com.jonkimbel.catfeeder.backend.server.HttpHeader;
import com.jonkimbel.catfeeder.backend.storage.api.PasswordStorage;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ActionDeterminer {
  private final HttpHeader requestHeader;

  public enum Action {
    // Photon actions.
    SERVE_PHOTON,

    // Logged-in actions.
    SERVE_HOME,
    UPDATE_PREFERENCES_REDIRECT_TO_HOME,
    REDIRECT_TO_HOME,

    // Login actions.
    SERVE_LOGIN,
    REDIRECT_TO_LOGIN,
    SET_COOKIE_REDIRECT_TO_HOME,

    // Error actions.
    NOT_IMPLEMENTED,
  }

  public ActionDeterminer(HttpHeader requestHeader) {
    this.requestHeader = requestHeader;
  }

  public Action determine() {
    if (requestHeader.path.startsWith("/photon")) {
      return Action.SERVE_PHOTON;
    }

    @Nullable String passcode = PasswordStorage.get();
    boolean isLoggedIn = passcode == null || passcode.equals(requestHeader.getCookie("passcode"));
    if (!isLoggedIn) {
      return determineNotLoggedInAction();
    }

    return determineLoggedInAction();
  }

  private Action determineNotLoggedInAction() {
    if (requestHeader.path.equals("/login")) {
      switch (requestHeader.method) {
        case POST:
          return Action.SET_COOKIE_REDIRECT_TO_HOME;
        case GET:
          return Action.SERVE_LOGIN;
        default:
          break;
      }
    }

    if (requestHeader.method == Http.Method.GET) {
      return Action.REDIRECT_TO_LOGIN;
    } else {
      return Action.NOT_IMPLEMENTED;
    }
  }

  private Action determineLoggedInAction() {
    if (requestHeader.path.equals("/")) {
      switch (requestHeader.method) {
        case POST:
          return Action.UPDATE_PREFERENCES_REDIRECT_TO_HOME;
        case GET:
          return Action.SERVE_HOME;
        default:
          break;
      }
    }

    if (requestHeader.method == Http.Method.GET) {
      return Action.REDIRECT_TO_HOME;
    } else {
      return Action.NOT_IMPLEMENTED;
    }
  }
}

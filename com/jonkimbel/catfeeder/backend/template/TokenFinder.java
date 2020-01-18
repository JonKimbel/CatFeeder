package com.jonkimbel.catfeeder.backend.template;

import java.nio.CharBuffer;

public class TokenFinder {
  private final String string;
  private final String startDelimiter;
  private final String endDelimiter;

  private ProcessResult lastResult = ProcessResult.DONE;
  private int start;
  private int end;

  public enum ProcessResult {
    READY_TO_READ_NON_MATCH,
    READY_TO_READ_MATCH,
    DONE,
  }

  public TokenFinder(String string, String startDelimiter, String endDelimiter) {
    this.string = string;
    this.startDelimiter = startDelimiter;
    this.endDelimiter = endDelimiter;
  }

  public ProcessResult process() {
    lastResult = processInner();
    return lastResult;
  }

  private ProcessResult processInner(){
    if (end == string.length()) {
      start = end;
      return ProcessResult.DONE;
    }

    // Skip over any end delimiter we just matched so it doesn't get included in the next string
    // we output.
    if (lastResult == ProcessResult.READY_TO_READ_MATCH) {
      end += endDelimiter.length();
    }

    final int findStart = string.indexOf(startDelimiter, end);
    final int findEnd = string.indexOf(endDelimiter, findStart);

    if (findStart == -1 || findEnd == -1) {
      start = end;
      end = string.length();
      return ProcessResult.READY_TO_READ_NON_MATCH;
    }

    if (findStart > end) {
      start = end;
      end = findStart;
      return ProcessResult.READY_TO_READ_NON_MATCH;
    }

    start = findStart + startDelimiter.length();
    end = findEnd;

    return ProcessResult.READY_TO_READ_MATCH;
  }

  public CharSequence read() {
    return CharBuffer.wrap(string, start, end);
  }
}

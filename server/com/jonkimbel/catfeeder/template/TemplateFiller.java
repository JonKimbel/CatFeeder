package com.jonkimbel.catfeeder.template;

import java.util.Date;
import java.util.Map;

import static com.jonkimbel.catfeeder.template.TokenFinder.ProcessResult.READY_TO_READ_MATCH;

public class TemplateFiller {
  private static final String TEMPLATE_START = "{{";
  private static final String TEMPLATE_END = "}}";

  private final TokenFinder tokenFinder;

  public TemplateFiller(String template) {
    this.tokenFinder = new TokenFinder(template, TEMPLATE_START, TEMPLATE_END);
  }

  public String fill(Map<String, String> values) {
    StringBuilder filledTemplate = new StringBuilder();

    TokenFinder.ProcessResult result;
    while ((result = tokenFinder.process()) != TokenFinder.ProcessResult.DONE) {
      if (result == READY_TO_READ_MATCH) {
        String key = tokenFinder.read().toString();

        System.err.printf("%s\n", values.keySet());

        if (!values.containsKey(key)) {
          System.err.printf("%s - TemplateFiller wasn't given input for key: '%s'\n", new Date(),
           key);
          continue;
        }

        filledTemplate.append(values.get(key));
      } else {
        filledTemplate.append(tokenFinder.read());
      }
    }

    return filledTemplate.toString();
  }

}

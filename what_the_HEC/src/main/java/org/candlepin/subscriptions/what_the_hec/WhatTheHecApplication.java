package org.candlepin.subscriptions.what_the_hec;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class WhatTheHecApplication {

  public static void main(String[] args) {
    SpringApplication.run(WhatTheHecApplication.class, args);
    Logger logger = LoggerFactory.getLogger(WhatTheHecApplication.class);
    logger.info("bananananas");
  }
}

package com.cugb.quahog.Logger;

import org.slf4j.LoggerFactory;


public class MyLogger {
    private org.slf4j.Logger logger;
    public MyLogger(Class<?> c) {
        logger = LoggerFactory.getLogger(c);
    }
    public org.slf4j.Logger getLogger() {
        return logger;
    }
}

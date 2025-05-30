package com.cugb.quahog.Configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppConfigurationProperties {
    private String pull_url;

    public String getPull_url() {
        return pull_url;
    }
    public void setPull_url(String pull_url) {
        this.pull_url = pull_url;
    }
}

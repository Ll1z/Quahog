package com.cugb.quahog.Configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfigurationProperties {
    private String pull_url;
    private String model_path;
    private String push_url;

    public AppConfigurationProperties() {

    }

    public String getPush_url() {
        return push_url;
    }
    public void setPush_url(String push_url) {
        this.push_url = push_url;
    }



    public String getPull_url() {
        return pull_url;
    }
    public void setPull_url(String pull_url) {
        this.pull_url = pull_url;
    }

    public String getModel_path() {
        return model_path;
    }
    public void setModel_path(String model_path) {
        this.model_path = model_path;
    }




}

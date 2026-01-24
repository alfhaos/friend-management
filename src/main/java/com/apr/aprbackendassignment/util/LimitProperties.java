package com.apr.aprbackendassignment.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "limit")
public class LimitProperties {

    private int maxFriend;
    private int maxRequests;
    private int windowMilliSeconds;
    private List<String> paths;
    private List<String> excludePaths;
}

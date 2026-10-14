package com.cdss.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for follow-ups ({@code cdss.follow-ups.*} in application.yml).
 *
 * @param upcomingWindow a scheduled follow-up due within this time from now is "upcoming"
 */
@ConfigurationProperties("cdss.follow-ups")
public record FollowUpProperties(@DefaultValue("7d") Duration upcomingWindow) {
}

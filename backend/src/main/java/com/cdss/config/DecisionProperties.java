package com.cdss.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for clinical decisions ({@code cdss.decision.*} in application.yml).
 * Observations older than these limits are ignored, so a rule never relies on
 * stale data; it reports the data as missing instead.
 *
 * @param vitalSignsMaxAge how old a vital sign may be and still be used
 * @param labResultsMaxAge how old a lab result may be and still be used
 */
@ConfigurationProperties("cdss.decision")
public record DecisionProperties(@DefaultValue("24h") Duration vitalSignsMaxAge,
		@DefaultValue("72h") Duration labResultsMaxAge) {
}

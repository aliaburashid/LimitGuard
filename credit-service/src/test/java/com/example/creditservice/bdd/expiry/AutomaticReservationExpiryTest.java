package com.example.creditservice.bdd.expiry;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/automatic-reservation-expiry.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.creditservice.bdd.expiry")
public class AutomaticReservationExpiryTest {
}

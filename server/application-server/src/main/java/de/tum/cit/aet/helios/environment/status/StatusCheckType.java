package de.tum.cit.aet.helios.environment.status;

public enum StatusCheckType {
  HTTP_STATUS, // Simple HTTP status check
  ARTEMIS_INFO, // Checks the /management/info endpoint of Artemis
}

package de.tum.cit.aet.helios.notification;

import jakarta.validation.Valid;
import java.util.List;

public record NotificationPreferencesWrapper(@Valid List<NotificationPreferenceDto> preferences) {}

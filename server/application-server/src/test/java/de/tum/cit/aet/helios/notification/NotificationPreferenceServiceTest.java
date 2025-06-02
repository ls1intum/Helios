package de.tum.cit.aet.helios.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.user.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private NotificationPreferenceRepository repository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private NotificationPreferenceService service;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setLogin("testUser");
    }

    @Test
    void initializeDefaultsForUser_ShouldCreatePreferencesForAllTypes() {
        // Arrange
        when(repository.findByUserAndType(any(), any())).thenReturn(Optional.empty());
        
        // Act
        service.initializeDefaultsForUser(testUser);

        // Assert
        verify(repository, times(NotificationPreference.Type.values().length)).save(any());
    }

    @Test
    void initializeDefaultsForUser_ShouldNotCreateExistingPreferences() {
        // Arrange
        NotificationPreference existingPref = new NotificationPreference(testUser, NotificationPreference.Type.DEPLOYMENT_FAILED, true);
        when(repository.findByUserAndType(testUser, NotificationPreference.Type.DEPLOYMENT_FAILED))
            .thenReturn(Optional.of(existingPref));
        when(repository.findByUserAndType(testUser, NotificationPreference.Type.LOCK_EXPIRED))
            .thenReturn(Optional.empty());
        when(repository.findByUserAndType(testUser, NotificationPreference.Type.LOCK_UNLOCKED))
            .thenReturn(Optional.empty());

        // Act
        service.initializeDefaultsForUser(testUser);

        // Assert
        verify(repository, times(2)).save(any()); // Only for non-existing preferences
    }

    @Test
    void getCurrentUserPreferences_ShouldReturnAllPreferences() {
        // Arrange
        when(authService.getUserFromGithubId()).thenReturn(testUser);
        List<NotificationPreference> preferences = List.of(
            new NotificationPreference(testUser, NotificationPreference.Type.DEPLOYMENT_FAILED, true),
            new NotificationPreference(testUser, NotificationPreference.Type.LOCK_EXPIRED, false)
        );
        when(repository.findByUser(testUser)).thenReturn(preferences);

        // Act
        List<NotificationPreferenceDto> result = service.getCurrentUserPreferences();

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(dto -> 
            dto.type() == NotificationPreference.Type.DEPLOYMENT_FAILED && dto.enabled()));
        assertTrue(result.stream().anyMatch(dto -> 
            dto.type() == NotificationPreference.Type.LOCK_EXPIRED && !dto.enabled()));
    }

    @Test
    void updatePreferencesForCurrentUser_ShouldUpdateExistingPreferences() {
        // Arrange
        when(authService.getUserFromGithubId()).thenReturn(testUser);
        NotificationPreference existingPref = new NotificationPreference(testUser, NotificationPreference.Type.DEPLOYMENT_FAILED, true);
        when(repository.findByUserAndType(testUser, NotificationPreference.Type.DEPLOYMENT_FAILED))
            .thenReturn(Optional.of(existingPref));

        List<NotificationPreferenceDto> updateDtos = List.of(
            new NotificationPreferenceDto(NotificationPreference.Type.DEPLOYMENT_FAILED, false)
        );

        // Act
        service.updatePreferencesForCurrentUser(updateDtos);

        // Assert
        verify(repository).save(argThat(pref -> 
            pref.getType() == NotificationPreference.Type.DEPLOYMENT_FAILED && !pref.isEnabled()));
    }

    @Test
    void updatePreferencesForCurrentUser_ShouldCreateNewPreferencesIfNotExist() {
        // Arrange
        when(authService.getUserFromGithubId()).thenReturn(testUser);
        when(repository.findByUserAndType(any(), any())).thenReturn(Optional.empty());

        List<NotificationPreferenceDto> updateDtos = List.of(
            new NotificationPreferenceDto(NotificationPreference.Type.DEPLOYMENT_FAILED, false)
        );

        // Act
        service.updatePreferencesForCurrentUser(updateDtos);

        // Assert
        verify(repository).save(argThat(pref -> 
            pref.getType() == NotificationPreference.Type.DEPLOYMENT_FAILED && 
            !pref.isEnabled() && 
            pref.getUser() == testUser));
    }
}
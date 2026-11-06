package com.aeroflow.hub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aeroflow.hub.config.BootstrapAdminProperties;
import com.aeroflow.hub.model.Role;
import com.aeroflow.hub.model.User;
import com.aeroflow.hub.repository.RoleRepository;
import com.aeroflow.hub.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class InitialAdminProvisionerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private BootstrapAdminProperties properties;
    private InitialAdminProvisioner provisioner;

    @BeforeEach
    void setUp() {
        properties = new BootstrapAdminProperties();
        properties.setEnabled(true);
        properties.setUsername("first.admin");
        properties.setEmail("First.Admin@example.com");
        properties.setPassword("StrongTestPass123");
        provisioner = new InitialAdminProvisioner(properties, userRepository, roleRepository, passwordEncoder);
    }

    @Test
    void provisionsAdminAndSupervisorWithHashedPasswordWhenAccountDoesNotExist() throws Exception {
        when(userRepository.findByEmailIgnoreCase("first.admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("first.admin", "first.admin"))
                .thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_SUPERVISOR")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("StrongTestPass123")).thenReturn("bcrypt-hash");

        provisioner.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User user = userCaptor.getValue();
        assertEquals("first.admin", user.getUsername());
        assertEquals("first.admin@example.com", user.getEmail());
        assertEquals("bcrypt-hash", user.getPassword());
        assertEquals(Set.of("ROLE_ADMIN", "ROLE_SUPERVISOR"),
                user.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet()));
        assertFalse(user.getPassword().equals(properties.getPassword()));
    }

    @Test
    void promotesOnlyTheConfiguredExistingAccountWithoutChangingItsPassword() throws Exception {
        properties.setPassword("unused");
        User existing = new User();
        existing.setUsername("first.admin");
        existing.setEmail("first.admin@example.com");
        existing.setPassword("existing-password-hash");
        Role adminRole = new Role();
        adminRole.setName("ROLE_ADMIN");
        Role supervisorRole = new Role();
        supervisorRole.setName("ROLE_SUPERVISOR");
        when(userRepository.findByEmailIgnoreCase("first.admin@example.com")).thenReturn(Optional.of(existing));
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByName("ROLE_SUPERVISOR")).thenReturn(Optional.of(supervisorRole));

        provisioner.run(new DefaultApplicationArguments());

        verify(userRepository).save(existing);
        assertEquals("existing-password-hash", existing.getPassword());
        assertTrue(existing.isEnabled());
        assertTrue(existing.isEmailVerified());
        assertEquals(Set.of("ROLE_ADMIN", "ROLE_SUPERVISOR"),
                existing.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet()));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void disabledProvisioningDoesNotReadOrWriteUsers() throws Exception {
        properties.setEnabled(false);

        provisioner.run(new DefaultApplicationArguments());

        verifyNoInteractions(userRepository, roleRepository, passwordEncoder);
    }

    @Test
    void rejectsShortBootstrapPasswordsBeforePersisting() {
        properties.setPassword("too-short");

        assertThrows(IllegalStateException.class, () -> provisioner.run(new DefaultApplicationArguments()));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void refusesToPromoteAnAccountWhenConfiguredUsernameDoesNotMatchItsEmail() {
        User existing = new User();
        existing.setUsername("someone-else");
        when(userRepository.findByEmailIgnoreCase("first.admin@example.com")).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () -> provisioner.run(new DefaultApplicationArguments()));

        verify(userRepository, never()).save(any(User.class));
        verify(roleRepository, never()).save(any(Role.class));
    }
}

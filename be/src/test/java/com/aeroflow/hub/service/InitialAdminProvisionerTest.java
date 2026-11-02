package com.aeroflow.hub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void provisionsHashedAdminOnlyWhenUserTableIsEmpty() throws Exception {
        Role adminRole = new Role();
        adminRole.setName("ROLE_ADMIN");
        when(userRepository.count()).thenReturn(0L);
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenReturn(adminRole);
        when(passwordEncoder.encode("StrongTestPass123")).thenReturn("bcrypt-hash");

        provisioner.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User user = userCaptor.getValue();
        assertEquals("first.admin", user.getUsername());
        assertEquals("first.admin@example.com", user.getEmail());
        assertEquals("bcrypt-hash", user.getPassword());
        assertEquals(1, user.getRoles().size());
        assertEquals("ROLE_ADMIN", user.getRoles().iterator().next().getName());
        assertFalse(user.getPassword().equals(properties.getPassword()));
    }

    @Test
    void doesNotCreateAnAdminWhenAnyUserAlreadyExists() throws Exception {
        when(userRepository.count()).thenReturn(1L);

        provisioner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(roleRepository, passwordEncoder);
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
        when(userRepository.count()).thenReturn(0L);

        assertThrows(IllegalStateException.class, () -> provisioner.run(new DefaultApplicationArguments()));

        verify(userRepository, never()).save(any(User.class));
    }
}

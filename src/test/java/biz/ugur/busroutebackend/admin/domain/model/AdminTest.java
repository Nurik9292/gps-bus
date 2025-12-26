package biz.ugur.busroutebackend.admin.domain.model;

import biz.ugur.busroutebackend.admin.domain.events.AdminCreatedEvent;
import biz.ugur.busroutebackend.admin.domain.events.AdminPasswordChangedEvent;
import biz.ugur.busroutebackend.admin.domain.events.AdminProfileUpdatedEvent;
import biz.ugur.busroutebackend.shared.domain.services.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


class AdminTest {

    private Admin admin;
    private PasswordEncoder passwordEncoder;
    private static final String USERNAME = "testadmin";
    private static final String PASSWORD = "password123";
    private static final String FULL_NAME = "Test Admin";

    @BeforeEach
    void setUp() {
        // Simple test password encoder that prefixes with "encoded:"
        passwordEncoder = new PasswordEncoder() {
            @Override
            public String encode(String rawPassword) {
                return "encoded:" + rawPassword;
            }

            @Override
            public boolean matches(String rawPassword, String encodedPassword) {
                return encodedPassword != null && encodedPassword.equals("encoded:" + rawPassword);
            }
        };
        String encodedPassword = passwordEncoder.encode(PASSWORD);
        admin = Admin.create(USERNAME, encodedPassword, FULL_NAME, null, false, true);
    }

    @Test
    void create_ShouldCreateNewAdminWithCorrectProperties() {
        assertNotNull(admin);
        assertNotNull(admin.getId());
        assertEquals(USERNAME, admin.getUsername());
        assertEquals(FULL_NAME, admin.getFullName());
        assertFalse(admin.getIsSuperAdmin());
        assertTrue(admin.getIsActive());
        assertNotNull(admin.getPasswordHash());
        assertNotNull(admin.getLastLoginAt());

        assertEquals(1, admin.getDomainEvents().size());
        assertInstanceOf(AdminCreatedEvent.class, admin.getDomainEvents().getFirst());
    }

    @Test
    void changePassword_ShouldReturnNewInstanceWithUpdatedPassword() {
        String newPassword = "newPassword456";
        String originalPasswordHash = admin.getPasswordHash();

        Admin updatedAdmin = admin.changePassword(passwordEncoder.encode(newPassword));

        assertNotNull(updatedAdmin);
        assertNotSame(admin, updatedAdmin);
        assertEquals(admin.getId(), updatedAdmin.getId());
        assertNotEquals(originalPasswordHash, updatedAdmin.getPasswordHash());
        assertTrue(updatedAdmin.checkPassword(newPassword, passwordEncoder));

        assertEquals(1, updatedAdmin.getDomainEvents().size());
        assertInstanceOf(AdminPasswordChangedEvent.class, updatedAdmin.getDomainEvents().getFirst());
    }

    @Test
    void checkPassword_ShouldReturnTrueForCorrectPassword() {
        assertTrue(admin.checkPassword(PASSWORD, passwordEncoder));
        assertFalse(admin.checkPassword("wrongPassword", passwordEncoder));
    }

    @Test
    void updateLastLogin_ShouldReturnNewInstanceWithUpdatedTimestamp() {
        var originalLastLogin = admin.getLastLoginAt();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Admin updatedAdmin = admin.updateLastLogin();

        assertNotNull(updatedAdmin);
        assertNotSame(admin, updatedAdmin);
        assertTrue(updatedAdmin.getLastLoginAt().isAfter(originalLastLogin));
    }

    @Test
    void deactivate_ShouldReturnNewInstanceWithIsActiveFalse() {
        Admin deactivatedAdmin = admin.deactivate();

        assertNotNull(deactivatedAdmin);
        assertNotSame(admin, deactivatedAdmin);
        assertTrue(admin.getIsActive());
        assertFalse(deactivatedAdmin.getIsActive());
    }

    @Test
    void deactivate_ShouldReturnSameInstance_WhenAlreadyInactive() {
        Admin inactiveAdmin = admin.deactivate();

        Admin stillInactive = inactiveAdmin.deactivate();

        assertSame(inactiveAdmin, stillInactive);
    }

    @Test
    void activate_ShouldReturnNewInstanceWithIsActiveTrue() {
        Admin inactiveAdmin = admin.deactivate();

        Admin activatedAdmin = inactiveAdmin.activate();

        assertNotNull(activatedAdmin);
        assertNotSame(inactiveAdmin, activatedAdmin);
        assertFalse(inactiveAdmin.getIsActive());
        assertTrue(activatedAdmin.getIsActive());
    }

    @Test
    void activate_ShouldReturnSameInstance_WhenAlreadyActive() {
        Admin stillActive = admin.activate();

        assertSame(admin, stillActive);
    }

    @Test
    void updateAvatar_ShouldReturnNewInstanceWithUpdatedAvatar() {
        String newAvatar = "avatar.jpg";

        Admin updatedAdmin = admin.updateAvatar(newAvatar);

        assertNotNull(updatedAdmin);
        assertNotSame(admin, updatedAdmin);
        assertNull(admin.getAvatar());
        assertEquals(newAvatar, updatedAdmin.getAvatar());

        assertEquals(1, updatedAdmin.getDomainEvents().size());
        AdminProfileUpdatedEvent event = (AdminProfileUpdatedEvent) updatedAdmin.getDomainEvents().get(0);
        assertTrue(event.isAvatarChanged());
    }

    @Test
    void updateAvatar_ShouldReturnSameInstance_WhenAvatarUnchanged() {
        Admin adminWithAvatar = admin.updateAvatar("avatar.jpg");

        Admin sameAvatar = adminWithAvatar.updateAvatar("avatar.jpg");

        assertSame(adminWithAvatar, sameAvatar);
    }

    @Test
    void removeAvatar_ShouldReturnNewInstanceWithNullAvatar() {
        Admin adminWithAvatar = admin.updateAvatar("avatar.jpg");

        Admin updatedAdmin = adminWithAvatar.removeAvatar();

        assertNotNull(updatedAdmin);
        assertNotSame(adminWithAvatar, updatedAdmin);
        assertEquals("avatar.jpg", adminWithAvatar.getAvatar());  // Original unchanged
        assertNull(updatedAdmin.getAvatar());

        assertEquals(1, updatedAdmin.getDomainEvents().size());
        AdminProfileUpdatedEvent event = (AdminProfileUpdatedEvent) updatedAdmin.getDomainEvents().get(0);
        assertTrue(event.isAvatarChanged());
    }

    @Test
    void removeAvatar_ShouldReturnSameInstance_WhenAvatarAlreadyNull() {
        Admin stillNoAvatar = admin.removeAvatar();

        assertSame(admin, stillNoAvatar);
    }

    @Test
    void updateProfile_WithUsernameAndFullName_ShouldReturnNewInstance() {
        String newUsername = "newusername";
        String newFullName = "New Full Name";

        Admin updatedAdmin = admin.updateProfile(newUsername, newFullName);

        assertNotNull(updatedAdmin);
        assertNotSame(admin, updatedAdmin);
        assertEquals(USERNAME, admin.getUsername());
        assertEquals(FULL_NAME, admin.getFullName());
        assertEquals(newUsername, updatedAdmin.getUsername());
        assertEquals(newFullName, updatedAdmin.getFullName());

        assertEquals(1, updatedAdmin.getDomainEvents().size());
        AdminProfileUpdatedEvent event = (AdminProfileUpdatedEvent) updatedAdmin.getDomainEvents().get(0);
        assertFalse(event.isAvatarChanged());
    }

    @Test
    void updateProfile_ShouldReturnSameInstance_WhenNoChanges() {
        Admin unchanged = admin.updateProfile(null, null);

        assertSame(admin, unchanged);
    }

    @Test
    void updateProfile_WithAvatar_ShouldReturnNewInstance() {
        String newUsername = "newuser";
        String newFullName = "New Name";
        String newAvatar = "newavatar.jpg";

        Admin updatedAdmin = admin.updateProfile(newUsername, newFullName, newAvatar);

        assertNotNull(updatedAdmin);
        assertNotSame(admin, updatedAdmin);
        assertEquals(newUsername, updatedAdmin.getUsername());
        assertEquals(newFullName, updatedAdmin.getFullName());
        assertEquals(newAvatar, updatedAdmin.getAvatar());

        assertEquals(1, updatedAdmin.getDomainEvents().size());
        AdminProfileUpdatedEvent event = (AdminProfileUpdatedEvent) updatedAdmin.getDomainEvents().get(0);
        assertTrue(event.isAvatarChanged());
    }

    @Test
    void fromDatabase_ShouldRestoreAdminWithoutEvents() {
        var id = admin.getId();
        var username = admin.getUsername();
        var passwordHash = admin.getPasswordHash();
        var fullName = admin.getFullName();
        var avatar = "avatar.jpg";
        var isActive = true;
        var isSuperAdmin = false;
        var lastLoginAt = admin.getLastLoginAt();
        var createdAt = admin.getCreatedAt();
        var updatedAt = admin.getUpdatedAt();
        var version = 1L;

        Admin restoredAdmin = Admin.fromDatabase(
                id, username, passwordHash, fullName, avatar,
                isActive, isSuperAdmin, lastLoginAt,
                createdAt, updatedAt, version
        );

        assertNotNull(restoredAdmin);
        assertEquals(id, restoredAdmin.getId());
        assertEquals(username, restoredAdmin.getUsername());
        assertEquals(passwordHash, restoredAdmin.getPasswordHash());
        assertEquals(fullName, restoredAdmin.getFullName());
        assertEquals(avatar, restoredAdmin.getAvatar());
        assertEquals(isActive, restoredAdmin.getIsActive());
        assertEquals(isSuperAdmin, restoredAdmin.getIsSuperAdmin());
        assertEquals(lastLoginAt, restoredAdmin.getLastLoginAt());
        assertEquals(createdAt, restoredAdmin.getCreatedAt());
        assertEquals(updatedAt, restoredAdmin.getUpdatedAt());
        assertEquals(version, restoredAdmin.getVersion());

        assertTrue(restoredAdmin.getDomainEvents().isEmpty());
    }

    @Test
    void immutabilityTest_OriginalShouldRemainUnchanged() {
        Admin original = Admin.create("original", "password", "Original Name", null, false, true);
        var originalUsername = original.getUsername();
        var originalFullName = original.getFullName();
        var originalIsActive = original.getIsActive();

        Admin afterPassword = original.changePassword("newPassword");
        Admin afterProfile = original.updateProfile("newUsername", "New Name");
        Admin afterAvatar = original.updateAvatar("avatar.jpg");
        Admin afterDeactivate = original.deactivate();

        assertEquals(originalUsername, original.getUsername());
        assertEquals(originalFullName, original.getFullName());
        assertNull(original.getAvatar());
        assertEquals(originalIsActive, original.getIsActive());

        assertNotSame(original, afterPassword);
        assertNotSame(original, afterProfile);
        assertNotSame(original, afterAvatar);
        assertNotSame(original, afterDeactivate);
    }

    @Test
    void chainingOperations_ShouldWork() {
        Admin result = admin
                .updateProfile("newuser", "New Name")
                .updateAvatar("avatar.jpg")
                .deactivate();

        assertEquals("newuser", result.getUsername());
        assertEquals("New Name", result.getFullName());
        assertEquals("avatar.jpg", result.getAvatar());
        assertFalse(result.getIsActive());

        assertEquals(USERNAME, admin.getUsername());
        assertEquals(FULL_NAME, admin.getFullName());
        assertNull(admin.getAvatar());
        assertTrue(admin.getIsActive());
    }
}

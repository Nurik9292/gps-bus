package biz.ugur.busroutebackend.admin.domain.services;

import biz.ugur.busroutebackend.admin.domain.events.AdminCreatedEvent;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.shared.domain.services.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminFactoryTest {

    private static final PasswordEncoder ENCODER = new PasswordEncoder() {
        @Override
        public String encode(String rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return ("encoded:" + rawPassword).equals(encodedPassword);
        }
    };

    private AdminFactory factory;

    @BeforeEach
    void setUp() {
        factory = new AdminFactory(ENCODER);
    }

    @Test
    void createProducesAdminWithEncodedPasswordAndEvent() {
        Admin admin = factory.create("ivan", "secret", "Ivan Ivanov", null, false, true);

        assertNotNull(admin.getId());
        assertEquals("ivan", admin.getUsername());
        assertEquals("Ivan Ivanov", admin.getFullName());
        assertEquals("encoded:secret", admin.getPasswordHash());
        assertFalse(admin.getIsSuperAdmin());
        assertTrue(admin.getIsActive());
        assertNotNull(admin.getCreatedAt());
        assertNotNull(admin.getUpdatedAt());
        assertEquals(0L, admin.getVersion());
        assertEquals(1, admin.getDomainEvents().size());
        assertInstanceOf(AdminCreatedEvent.class, admin.getDomainEvents().get(0));
    }

    @Test
    void createAcceptsAvatarAndSuperAdminFlag() {
        Admin admin = factory.create("root", "rootpass", "Root", "avatar.png", true, true);
        assertEquals("avatar.png", admin.getAvatar());
        assertTrue(admin.getIsSuperAdmin());
    }

    @Test
    void encodePasswordDelegatesToEncoder() {
        assertEquals("encoded:hello", factory.encodePassword("hello"));
    }

    @Test
    void matchesPasswordDelegatesToEncoder() {
        String encoded = factory.encodePassword("secret");
        assertTrue(factory.matchesPassword("secret", encoded));
        assertFalse(factory.matchesPassword("wrong", encoded));
    }
}

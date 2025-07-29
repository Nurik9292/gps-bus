INSERT INTO cities (id, name, name_tm, display_order) VALUES
                                                          ('city-001', 'Ашхабад', 'Aşgabat', 1),
                                                          ('city-002', 'Туркменабад', 'Türkmenabat', 2),
                                                          ('city-003', 'Дашогуз', 'Daşoguz', 3),
                                                          ('city-004', 'Туркменбаши', 'Türkmenbaşy', 4),
                                                          ('city-005', 'Мары', 'Mary', 5)
    ON CONFLICT (id) DO NOTHING;

INSERT INTO admins (id, username, password_hash, full_name, is_super_admin, is_active)
VALUES (
           'admin-00001',
           'admin',
           '$2a$10$eImiTXuWVxfM37uY4JANjOhSzm/yQLdpb5bLGFpwT3Zq8TGtmPGqy', -- password: admin123
           'Super Administrator',
           true,
           true
       ) ON CONFLICT (username) DO NOTHING;
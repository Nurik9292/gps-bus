package biz.ugur.busroutebackend.client.domain.specification;

import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;

import java.time.LocalDateTime;

public final class ClientSpecifications {

    private ClientSpecifications() {
    }


    public static Specification<Client> isActive() {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return ClientStatus.ACTIVE.equals(client.getStatus());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("status = :status", "status", ClientStatus.ACTIVE.name());
            }
        };
    }

    public static Specification<Client> isInactive() {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return ClientStatus.INACTIVE.equals(client.getStatus());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("status = :status", "status", ClientStatus.INACTIVE.name());
            }
        };
    }

    public static Specification<Client> isSuspended() {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return ClientStatus.SUSPENDED.equals(client.getStatus());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("status = :status", "status", ClientStatus.SUSPENDED.name());
            }
        };
    }

    public static Specification<Client> isBlocked() {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return ClientStatus.BLOCKED.equals(client.getStatus());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("status = :status", "status", ClientStatus.BLOCKED.name());
            }
        };
    }

    public static Specification<Client> hasStatus(ClientStatus status) {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return status.equals(client.getStatus());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("status = :status", "status", status.name());
            }
        };
    }


    public static Specification<Client> isAndroid() {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return Platform.ANDROID.equals(client.getPlatform());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("platform = :platform", "platform", Platform.ANDROID.name());
            }
        };
    }

    public static Specification<Client> isIOS() {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return Platform.IOS.equals(client.getPlatform());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("platform = :platform", "platform", Platform.IOS.name());
            }
        };
    }

    public static Specification<Client> isWeb() {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return Platform.WEB.equals(client.getPlatform());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("platform = :platform", "platform", Platform.WEB.name());
            }
        };
    }

    public static Specification<Client> hasPlatform(Platform platform) {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return platform.equals(client.getPlatform());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("platform = :platform", "platform", platform.name());
            }
        };
    }


    public static Specification<Client> hasPhone(String phone) {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return phone != null && phone.equals(client.getPhoneNumber());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("phone = :phone", "phone", phone);
            }
        };
    }

    public static Specification<Client> phoneContains(String phoneFragment) {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return client.getPhoneNumber() != null &&
                       client.getPhoneNumber().contains(phoneFragment);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("phone LIKE :phonePattern", "phonePattern", "%" + phoneFragment + "%");
            }
        };
    }

    public static Specification<Client> nameContains(String nameFragment) {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return client.getName() != null &&
                       client.getName().toLowerCase().contains(nameFragment.toLowerCase());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("LOWER(name) LIKE LOWER(:namePattern)", "namePattern", "%" + nameFragment + "%");
            }
        };
    }


    public static Specification<Client> hasVerifiedOtp() {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return Boolean.TRUE.equals(client.getOtpVerify());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("otp_verify = :otpVerify", "otpVerify", true);
            }
        };
    }

    public static Specification<Client> hasUnverifiedOtp() {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return Boolean.FALSE.equals(client.getOtpVerify());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("otp_verify = :otpVerify", "otpVerify", false);
            }
        };
    }


    public static Specification<Client> hasValidTokens() {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return client.hasValidTokens();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria("access_token IS NOT NULL AND refresh_token IS NOT NULL", java.util.Collections.emptyMap());
            }
        };
    }

    public static Specification<Client> hasNoTokens() {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return !client.hasValidTokens();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria("access_token IS NULL OR refresh_token IS NULL", java.util.Collections.emptyMap());
            }
        };
    }


    public static Specification<Client> hasActivityAfter(LocalDateTime since) {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return client.getLastActivity() != null && client.getLastActivity().isAfter(since);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("last_activity > :since", "since", since);
            }
        };
    }

    public static Specification<Client> hasActivityBefore(LocalDateTime until) {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return client.getLastActivity() != null && client.getLastActivity().isBefore(until);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("last_activity < :until", "until", until);
            }
        };
    }

    public static Specification<Client> hasRecentActivity(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return hasActivityAfter(since);
    }

    public static Specification<Client> hasNoRecentActivity(int days) {
        LocalDateTime until = LocalDateTime.now().minusDays(days);
        return hasActivityBefore(until);
    }


    public static Specification<Client> createdAfter(LocalDateTime since) {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return client.getCreatedAt() != null && client.getCreatedAt().isAfter(since);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("created_at > :createdAfter", "createdAfter", since);
            }
        };
    }

    public static Specification<Client> createdBefore(LocalDateTime until) {
        return new Specification<Client>() {
            @Override
            public boolean isSatisfiedBy(Client client) {
                return client.getCreatedAt() != null && client.getCreatedAt().isBefore(until);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("created_at < :createdBefore", "createdBefore", until);
            }
        };
    }


    public static Specification<Client> isActiveAndVerified() {
        return isActive().and(hasVerifiedOtp());
    }

    public static Specification<Client> canAuthenticate() {
        return isActive().and(hasVerifiedOtp()).and(hasValidTokens());
    }

    public static Specification<Client> needsOtpVerification() {
        return isInactive().and(hasUnverifiedOtp());
    }

    public static Specification<Client> hasProblemStatus() {
        return isSuspended().or(isBlocked());
    }

    public static Specification<Client> hasRecentActivityOn(Platform platform, int days) {
        return hasPlatform(platform).and(hasRecentActivity(days));
    }

    public static Specification<Client> searchByText(String searchText) {
        return phoneContains(searchText).or(nameContains(searchText));
    }
}

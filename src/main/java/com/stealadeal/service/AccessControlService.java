package com.stealadeal.service;

import com.stealadeal.domain.ParticipantType;
import com.stealadeal.domain.UserRole;
import com.stealadeal.repository.DealTaskRepository;
import com.stealadeal.repository.DealRepository;
import com.stealadeal.repository.NotificationRepository;
import com.stealadeal.repository.VehicleRepository;
import com.stealadeal.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;

@Service("accessControl")
public class AccessControlService {

    private final DealRepository dealRepository;
    private final DealTaskRepository dealTaskRepository;
    private final NotificationRepository notificationRepository;
    private final VehicleRepository vehicleRepository;

    public AccessControlService(
            DealRepository dealRepository,
            DealTaskRepository dealTaskRepository,
            NotificationRepository notificationRepository,
            VehicleRepository vehicleRepository
    ) {
        this.dealRepository = dealRepository;
        this.dealTaskRepository = dealTaskRepository;
        this.notificationRepository = notificationRepository;
        this.vehicleRepository = vehicleRepository;
    }

    public boolean isAuthenticated(Authentication authentication) {
        return requireUser(authentication) != null;
    }

    public boolean isAdmin(Authentication authentication) {
        return requireUser(authentication).role() == UserRole.ADMIN;
    }

    public boolean canAccessDealer(Authentication authentication, Long dealerId) {
        AuthenticatedUser user = requireUser(authentication);
        return user.role() == UserRole.ADMIN || (user.role() == UserRole.DEALER && dealerId.equals(user.dealerId()));
    }

    public boolean canAccessBuyer(Authentication authentication, String buyerEmail) {
        AuthenticatedUser user = requireUser(authentication);
        return user.role() == UserRole.ADMIN || (user.role() == UserRole.BUYER && buyerEmail.equalsIgnoreCase(user.email()));
    }

    public boolean canAccessAssignee(Authentication authentication, ParticipantType participantType, String participantReference) {
        AuthenticatedUser user = requireUser(authentication);
        if (user.role() == UserRole.ADMIN) {
            return true;
        }
        if (participantType == ParticipantType.BUYER) {
            return user.role() == UserRole.BUYER && participantReference.equalsIgnoreCase(user.email());
        }
        if (participantType == ParticipantType.DEALER) {
            return user.role() == UserRole.DEALER && user.dealerId() != null && participantReference.equals(String.valueOf(user.dealerId()));
        }
        return false;
    }

    public boolean canAccessDeal(Authentication authentication, Long dealId) {
        AuthenticatedUser user = requireUser(authentication);
        if (user.role() == UserRole.ADMIN) {
            return true;
        }
        return dealRepository.findById(dealId)
                .map(deal -> (user.role() == UserRole.BUYER && deal.getBuyerEmail().equalsIgnoreCase(user.email()))
                        || (user.role() == UserRole.DEALER && user.dealerId() != null && deal.getVehicle().getDealer().getId().equals(user.dealerId())))
                .orElse(false);
    }

    public boolean canAccessTask(Authentication authentication, Long taskId) {
        AuthenticatedUser user = requireUser(authentication);
        if (user.role() == UserRole.ADMIN) {
            return true;
        }
        return dealTaskRepository.findById(taskId)
                .map(task -> canAccessAssignee(user, task.getAssigneeType(), task.getAssigneeReference()))
                .orElse(false);
    }

    public boolean canAccessNotification(Authentication authentication, Long notificationId) {
        AuthenticatedUser user = requireUser(authentication);
        if (user.role() == UserRole.ADMIN) {
            return true;
        }
        return notificationRepository.findById(notificationId)
                .map(notification -> canAccessAssignee(user, notification.getRecipientType(), notification.getRecipientReference()))
                .orElse(false);
    }

    public boolean canCreateDeal(Authentication authentication, Long vehicleId, String buyerEmail) {
        AuthenticatedUser user = requireUser(authentication);
        if (user.role() == UserRole.ADMIN) {
            return true;
        }
        if (user.role() == UserRole.BUYER) {
            return buyerEmail != null && buyerEmail.equalsIgnoreCase(user.email());
        }
        if (user.role() == UserRole.DEALER && user.dealerId() != null) {
            return vehicleRepository.findById(vehicleId)
                    .map(vehicle -> vehicle.getDealer().getId().equals(user.dealerId()))
                    .orElse(false);
        }
        return false;
    }

    private AuthenticatedUser currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return user;
    }

    private AuthenticatedUser requireUser(Authentication authentication) {
        AuthenticatedUser user = currentUser(authentication);
        if (user == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return user;
    }

    private boolean canAccessAssignee(AuthenticatedUser user, ParticipantType participantType, String participantReference) {
        if (participantType == ParticipantType.BUYER) {
            return user.role() == UserRole.BUYER && participantReference.equalsIgnoreCase(user.email());
        }
        if (participantType == ParticipantType.DEALER) {
            return user.role() == UserRole.DEALER && user.dealerId() != null && participantReference.equals(String.valueOf(user.dealerId()));
        }
        return false;
    }
}

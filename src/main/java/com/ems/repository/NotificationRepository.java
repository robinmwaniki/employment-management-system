package com.ems.repository;

import com.ems.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    /** Staff feed: every notification. */
    List<Notification> findTop10ByOrderByCreatedAtDesc();

    long countByIsReadFalse();

    /** Employee feed: only notifications addressed to that employee. */
    List<Notification> findTop10ByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    long countByEmployeeIdAndIsReadFalse(Long employeeId);

    Optional<Notification> findByIdAndEmployeeId(Long id, Long employeeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.isRead = false")
    int markAllAsRead();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.isRead = true "
            + "where n.isRead = false and n.employee.id = :employeeId")
    int markAllAsReadForEmployee(@Param("employeeId") Long employeeId);

}

package com.devscribe.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devscribe.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}

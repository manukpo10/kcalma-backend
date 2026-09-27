package com.kcalma.push;

import org.springframework.data.jpa.repository.JpaRepository;

interface PushConfigRepository extends JpaRepository<PushConfig, Short> {
}

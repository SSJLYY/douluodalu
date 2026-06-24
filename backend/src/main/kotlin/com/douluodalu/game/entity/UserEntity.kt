package com.douluodalu.game.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "user")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(unique = true, nullable = false, length = 32)
    var username: String = "",

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String = "",

    @Column(nullable = false, length = 32)
    var nickname: String = "",

    @Column(name = "avatar_url")
    var avatarUrl: String? = null,

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var player: PlayerProfileEntity? = null,

    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "last_login_at")
    var lastLoginAt: LocalDateTime? = null
)

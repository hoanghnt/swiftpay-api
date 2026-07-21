package com.hoanghnt.swiftpay.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_ref")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRef {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column
    private String email;
}

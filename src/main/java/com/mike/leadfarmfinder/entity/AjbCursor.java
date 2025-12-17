package com.mike.leadfarmfinder.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ajb_cursor")
@Getter @Setter
public class AjbCursor {

    @Id
    private Integer id;

    @Column(name = "next_page", nullable = false)
    private int nextPage;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}


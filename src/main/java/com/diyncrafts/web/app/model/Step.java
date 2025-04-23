package com.diyncrafts.web.app.model;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;

@Data
@Entity
public class Step implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int stepNumber;
    private String title;
    private String description;
    private String videoTimestamp;

    @ManyToOne
    @JoinColumn(name = "guide_id")
    private Guide guide;
}
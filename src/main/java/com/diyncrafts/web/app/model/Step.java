package com.diyncrafts.web.app.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
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

    @Override
    public String toString() {
        return "Step[id=" + id + ", stepNumber=" + stepNumber + "]";
    }
}

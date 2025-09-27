package com.example.dungeon.model;

import java.io.Serial;
import java.io.Serializable;

public abstract class Entity implements Serializable{
    private static final long serialVersionUID = 1L;
    private String name;
    private int hp;

    public Entity(String name, int hp) {
        this.name = name;
        this.hp = hp;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }
}

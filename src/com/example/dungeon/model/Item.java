package com.example.dungeon.model;

import java.io.Serializable;

public abstract class Item implements Serializable{
    private static final long serialVersionUID = 1L;
    private final String name;

    protected Item(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract void apply(GameState ctx);
}

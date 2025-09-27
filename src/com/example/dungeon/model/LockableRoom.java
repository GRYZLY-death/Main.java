package com.example.dungeon.model;

import java.io.Serializable;
import java.lang.reflect.Field;

public class LockableRoom extends Room implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean locked;
    private final String lockId;
    private final String lockedDescription;
    private final String unlockedDescription;

    public LockableRoom(String name, String lockedDesc, String unlockedDesc, String lockId) {
        super(name, lockedDesc);
        this.locked = true;
        this.lockId = lockId;
        this.lockedDescription = lockedDesc;
        this.unlockedDescription = unlockedDesc;
    }

    public boolean isLocked() {
        return locked;
    }

    public String getLockId() {
        return lockId;
    }

    public void unlock() {
        this.locked = false;
        // Меняем описание на открытое
        try {
            Field field = Room.class.getDeclaredField("description");
            field.setAccessible(true);
            field.set(this, unlockedDescription);
        } catch (Exception e) {
            System.err.println("Ошибка при разблокировке комнаты: " + e.getMessage());
        }
    }

    @Override
    public String describe() {
        String baseDescription = super.describe();
        if (locked) {
            return baseDescription + "\n🚪 Дверь заперта. Нужен ключ.";
        }
        return baseDescription;
    }
}
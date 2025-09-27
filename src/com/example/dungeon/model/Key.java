package com.example.dungeon.model;

public class Key extends Item {
    private final String doorId;

    public Key(String name) {
        this(name, "default_door");
    }

    public Key(String name, String doorId) {
        super(name);
        this.doorId = doorId;
    }

    public String getDoorId() {
        return doorId;
    }

    @Override
    public void apply(GameState ctx) {
        Room current = ctx.getCurrent();
        Player player = ctx.getPlayer();

        // Проверяем, есть ли запертая дверь в текущей комнате
        if (current instanceof LockableRoom) {
            LockableRoom lockableRoom = (LockableRoom) current;
            if (lockableRoom.isLocked() && lockableRoom.getLockId().equals(doorId)) {
                lockableRoom.unlock();
                System.out.println("✅ Дверь открыта ключом: " + getName());
                player.getInventory().remove(this);
                return;
            }
        }

        // Если нет подходящей двери, обычное сообщение
        System.out.println("Ключ " + getName() + " звенит. Возможно, где-то есть подходящая дверь...");
    }
}

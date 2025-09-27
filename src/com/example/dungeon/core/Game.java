package com.example.dungeon.core;

import com.example.dungeon.model.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Scanner;

public class Game {
    private final GameState state = new GameState();
    private final Map<String, Command> commands = new LinkedHashMap<>();

    static {
        WorldInfo.touch("Game");
    }

    public Game() {
        registerCommands();
        bootstrapWorld();
    }

    private void registerCommands() {
        commands.put("help", (ctx, a) -> System.out.println("Команды: " + String.join(", ", commands.keySet())));

        commands.put("gc-stats", (ctx, a) -> {
            Runtime rt = Runtime.getRuntime();
            long max = rt.maxMemory();
            long total = rt.totalMemory();
            long free = rt.freeMemory();
            long used = total - free;

            System.out.println("=== Статистика памяти ===");
            System.out.printf("Максимум:   %,d bytes (%.1f MB)%n", max, max / (1024.0 * 1024.0));
            System.out.printf("Всего:      %,d bytes (%.1f MB)%n", total, total / (1024.0 * 1024.0));
            System.out.printf("Использовано: %,d bytes (%.1f MB)%n", used, used / (1024.0 * 1024.0));
            System.out.printf("Свободно:   %,d bytes (%.1f MB)%n", free, free / (1024.0 * 1024.0));
            System.out.printf("Загрузка:   %.1f%%%n", (used * 100.0 / total));

            // Демонстрация работы GC
            long beforeGC = used;
            System.gc();
            try { Thread.sleep(50); } catch (InterruptedException e) { }

            long afterGC = rt.totalMemory() - rt.freeMemory();
            System.out.printf("После GC:   %,d bytes (освобождено %,d)%n",
                    afterGC, beforeGC - afterGC);
        });

        commands.put("alloc", (ctx, a) -> {
            System.out.println("Демонстрация работы с памятью и GC:");

            // Создаем много объектов для демонстрации
            List<Item> tempItems = new ArrayList<>();
            for (int i = 0; i < 10000; i++) {
                tempItems.add(new Potion("Тестовое зелье " + i, i % 10));
            }

            System.out.println("Создано 10000 временных объектов");

            // Выводим статистику памяти ДО сборки мусора
            Runtime rt = Runtime.getRuntime();
            rt.gc(); // Запускаем GC для чистоты эксперимента
            long memoryBefore = rt.totalMemory() - rt.freeMemory();

            System.out.println("Память до очистки: " + memoryBefore + " bytes");

            // Освобождаем объекты
            tempItems.clear();
            tempItems = null;

            // Принудительная сборка мусора
            System.gc();

            try {
                Thread.sleep(100); // Даем время GC
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Статистика ПОСЛЕ сборки мусора
            long memoryAfter = rt.totalMemory() - rt.freeMemory();
            long freedMemory = memoryBefore - memoryAfter;

            System.out.println("Память после очистки: " + memoryAfter + " bytes");
            System.out.println("Освобождено памяти: " + freedMemory + " bytes");
            System.out.println("Эффективность GC: " + (freedMemory * 100 / Math.max(1, memoryBefore)) + "%");
        });

        commands.put("look", (ctx, a) -> System.out.println(ctx.getCurrent().describe()));

        commands.put("move", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите направление: north, south, east, west");
            }

            String direction = a.getFirst().toLowerCase(Locale.ROOT);
            Room current = ctx.getCurrent();
            Room next = current.getNeighbors().get(direction);

            if (next == null) {
                throw new InvalidCommandException("Нет пути в направлении: " + direction);
            }

            // Проверка запертой двери
            if (next instanceof LockableRoom) {
                LockableRoom nextLockable = (LockableRoom) next;
                if (nextLockable.isLocked()) {
                    throw new InvalidCommandException("Дверь заперта! Нужен ключ.");
                }
            }

            ctx.setCurrent(next);
            System.out.println("Переместились в: " + next.getName());
            System.out.println(next.describe());
        });

        commands.put("take", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите название предмета");
            }

            String itemName = String.join(" ", a);
            Room current = ctx.getCurrent();
            Player player = ctx.getPlayer();

            // Находим предмет в комнате
            Item foundItem = current.getItems().stream()
                    .filter(item -> item.getName().equalsIgnoreCase(itemName))
                    .findFirst()
                    .orElseThrow(() -> new InvalidCommandException("Предмет не найден: " + itemName));

            // Перемещаем предмет в инвентарь
            current.getItems().remove(foundItem);
            player.getInventory().add(foundItem);

            System.out.println("Взят предмет: " + foundItem.getName());
        });

        commands.put("inventory", (ctx, a) -> {
            Player player = ctx.getPlayer();
            List<Item> inventory = player.getInventory();

            if (inventory.isEmpty()) {
                System.out.println("Инвентарь пуст");
                return;
            }

            // Группировка по типу предмета с использованием Stream API
            Map<String, List<Item>> groupedItems = inventory.stream()
                    .collect(Collectors.groupingBy(item -> item.getClass().getSimpleName()));

            // Сортировка по названию типа и вывод
            groupedItems.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        String type = entry.getKey();
                        List<String> itemNames = entry.getValue().stream()
                                .map(Item::getName)
                                .sorted()
                                .collect(Collectors.toList());
                        System.out.println(type + ": " + String.join(", ", itemNames));
                    });

            // Альтернативный вариант: простой вывод с сортировкой
            System.out.println("\nВсе предметы (отсортировано):");
            inventory.stream()
                    .map(Item::getName)
                    .sorted()
                    .forEach(System.out::println);
        });

        commands.put("use", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите название предмета");
            }

            String itemName = String.join(" ", a);
            Player player = ctx.getPlayer();

            // Находим предмет в инвентаре
            Item foundItem = player.getInventory().stream()
                    .filter(item -> item.getName().equalsIgnoreCase(itemName))
                    .findFirst()
                    .orElseThrow(() -> new InvalidCommandException("Предмет не найден в инвентаре: " + itemName));

            // Применяем предмет (полиморфизм через Item.apply())
            foundItem.apply(ctx);
        });

        commands.put("fight", (ctx, a) -> {
            Room current = ctx.getCurrent();
            Monster monster = current.getMonster();
            Player player = ctx.getPlayer();

            if (monster == null) {
                throw new InvalidCommandException("В этой комнате нет монстров");
            }

            if (player.getHp() <= 0) {
                throw new InvalidCommandException("Вы не можете сражаться - у вас 0 HP!");
            }

            System.out.println("╔══════════════════════════════════════╗");
            System.out.println("║            НАЧАЛО БОЯ!              ║");
            System.out.println("║         " + monster.getName() + " vs " + player.getName() + "         ║");
            System.out.println("╚══════════════════════════════════════╝");
            System.out.println("Ваше HP: " + player.getHp() + " ❤️, атака: " + player.getAttack() + " ⚔️");
            System.out.println("Монстр HP: " + monster.getHp() + " ❤️, уровень: " + monster.getLevel() + " ⭐");

            Scanner scanner = new Scanner(System.in);
            int turn = 1;

            while (player.getHp() > 0 && monster.getHp() > 0) {
                System.out.println("\n--- Ход " + turn + " ---");
                System.out.print("Выберите действие (атака/бегство/инвентарь): ");
                String action = scanner.nextLine().trim().toLowerCase(Locale.ROOT);

                // Обработка бегства
                if (action.contains("бег") || action.equals("бежать") || action.equals("run")) {
                    System.out.println("Вы успешно сбежали из боя!");
                    return;
                }

                // Просмотр инвентаря во время боя
                if (action.contains("инв") || action.equals("инвентарь") || action.equals("inventory")) {
                    System.out.println("--- ИНВЕНТАРЬ В БОЮ ---");
                    player.getInventory().stream()
                            .map(Item::getName)
                            .sorted()
                            .forEach(System.out::println);
                    continue;
                }

                // Атака (по умолчанию)
                if (action.isEmpty() || action.contains("атак") || action.equals("attack")) {
                    // Атака игрока
                    int playerDamage = player.getAttack();
                    monster.setHp(monster.getHp() - playerDamage);
                    System.out.println("⚔️ Вы нанесли " + playerDamage + " урона!");

                    // Проверка смерти монстра
                    if (monster.getHp() <= 0) {
                        handleMonsterDefeat(ctx, current, player, monster);
                        System.out.println("\n✅ Бой завершен! Вы остаетесь в комнате.");
                        return;
                    }

                    // Атака монстра
                    int monsterDamage = monster.getLevel() * 2;
                    player.setHp(player.getHp() - monsterDamage);
                    System.out.println("😠 " + monster.getName() + " нанес вам " + monsterDamage + " урона!");

                    // Проверка смерти игрока
                    if (player.getHp() <= 0) {
                        handlePlayerDeath(ctx, player);
                        return;
                    }

                    // Статус после хода
                    System.out.println("❤️ Ваше HP: " + player.getHp() + ", HP монстра: " + monster.getHp());
                    turn++;
                } else {
                    System.out.println("Неизвестное действие, используйте: атака, бегство, инвентарь");
                }
            }
        });

        commands.put("save", (ctx, a) -> SaveLoad.save(ctx));
        commands.put("load", (ctx, a) -> SaveLoad.load(ctx));
        commands.put("scores", (ctx, a) -> SaveLoad.printScores());

        commands.put("debug-world", (ctx, a) -> {
            System.out.println("=== ДЕБАГ МИРА ===");
            System.out.println("Текущая комната: " + ctx.getCurrent().getName());

            // Собираем все комнаты
            Map<String, Room> allRooms = new HashMap<>();
            collectAllRooms(ctx.getCurrent(), allRooms);

            System.out.println("Всего комнат: " + allRooms.size());
            allRooms.values().forEach(room -> {
                System.out.println(" - " + room.getName() +
                        (room instanceof LockableRoom ? " (ЗАПЕРТА)" : "") +
                        (room == ctx.getCurrent() ? " ← ТЕКУЩАЯ" : ""));
            });
        });

        commands.put("exit", (ctx, a) -> {
            System.out.println("Сохранение результата...");
            SaveLoad.writeScore(ctx.getPlayer().getName(), ctx.getScore());
            System.out.println("Ваш счет: " + ctx.getScore() + " очков сохранен!");
            System.out.println("Пока!");
            System.exit(0);
        });
    }

    private void handleMonsterDefeat(GameState ctx, Room room, Player player, Monster monster) {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║           ПОБЕДА! 🎉                 ║");
        System.out.println("║      Вы победили " + monster.getName() + "!      ║");
        System.out.println("╚══════════════════════════════════════╝");

        // Награда за победу
        int xpReward = monster.getLevel() * 5;
        int healReward = monster.getLevel() * 2;

        ctx.addScore(xpReward);
        player.setHp(Math.min(player.getHp() + healReward, 50)); // Максимум 50 HP

        System.out.println("🎁 Награда за победу:");
        System.out.println("   +" + xpReward + " очков опыта");
        System.out.println("   +" + healReward + " HP");
        System.out.println("❤️ Теперь у вас " + player.getHp() + " HP");
        System.out.println("⭐ Общий счет: " + ctx.getScore());

        // Случайный выпадение лута (30% шанс)
        Random random = new Random();
        if (random.nextDouble() < 0.3) {
            Item randomLoot = generateRandomLoot();
            player.getInventory().add(randomLoot);
            System.out.println("📦 Получен трофей: " + randomLoot.getName());
        }

        // Автоподбор предметов из комнаты
        if (!room.getItems().isEmpty()) {
            System.out.println("📦 Подбираем предметы из комнаты:");
            List<Item> loot = new ArrayList<>(room.getItems());
            for (Item item : loot) {
                player.getInventory().add(item);
                room.getItems().remove(item);
                System.out.println("  ✅ " + item.getName());
            }
        }

        // Убираем побежденного монстра
        room.setMonster(null);
    }

    private void handlePlayerDeath(GameState ctx, Player player) {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║           ГAME OVER 💀               ║");
        System.out.println("║          ВЫ ПОГИБЛИ!                 ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println("💀 " + player.getName() + " пал в бою...");
        System.out.println("⭐ Итоговый счет: " + ctx.getScore());

        // Сохраняем результат
        SaveLoad.writeScore(player.getName(), ctx.getScore());
        System.out.println("💾 Результат сохранен");

        // Завершаем игру
        System.exit(0);
    }

    private Item generateRandomLoot() {
        Random random = new Random();
        int lootType = random.nextInt(3);

        switch (lootType) {
            case 0:
                return new Potion("Случайное зелье", 5 + random.nextInt(10));
            case 1:
                return new Weapon("Случайное оружие", 1 + random.nextInt(3));
            case 2:
                return new Key("Секретный ключ");
            default:
                return new Potion("Малое зелье", 5);
        }
    }

    private void collectAllRooms(Room start, Map<String, Room> collected) {
        if (start == null || collected.containsKey(start.getName())) {
            return;
        }
        collected.put(start.getName(), start);
        for (Room neighbor : start.getNeighbors().values()) {
            collectAllRooms(neighbor, collected);
        }
    }

    private void bootstrapWorld() {
        Player hero = new Player("Герой", 20, 5);
        state.setPlayer(hero);

        // Обычные комнаты
        Room square = new Room("Площадь", "Каменная площадь с фонтаном.");
        Room forest = new Room("Лес", "Шелест листвы и птичий щебет.");
        Room cave = new Room("Пещера", "Темно и сыро. Здесь что-то блестит...");

        // Новая комната с запертой дверью
        LockableRoom treasureRoom = new LockableRoom(
                "Сокровищница",
                "Комната с массивной железной дверью. Дверь надежно заперта.",
                "Великолепная комната, заполненная золотом и драгоценностями!",
                "treasure_door"
        );

        // Настройка связей между комнатами
        square.getNeighbors().put("north", forest);
        forest.getNeighbors().put("south", square);
        forest.getNeighbors().put("east", cave);
        cave.getNeighbors().put("west", forest);
        cave.getNeighbors().put("north", treasureRoom); // Запертая дверь

        // Добавляем предметы в комнаты
        forest.getItems().add(new Potion("Малое зелье", 5));
        forest.getItems().add(new Weapon("Ржавый меч", 2));
        cave.getItems().add(new Key("Золотой ключ", "treasure_door"));
        cave.getItems().add(new Potion("Большое зелье", 10));
        treasureRoom.getItems().add(new Weapon("Легендарный меч", 10));
        treasureRoom.getItems().add(new Potion("Эликсир жизни", 50));

        // Добавляем монстров
        forest.setMonster(new Monster("Волк", 1, 8));
        cave.setMonster(new Monster("Гоблин", 2, 12));
        treasureRoom.setMonster(new Monster("Дракон", 5, 30));

        state.setCurrent(square);

        // Сохраняем начальное состояние мира
        SaveLoad.save(state);
    }

    public void run() {
        System.out.println("DungeonMini (ПОЛНАЯ ВЕРСИЯ). 'help' — команды.");

        // Демонстрация различия ошибок компиляции и выполнения:

        // ОШИБКА КОМПИЛЯЦИИ (пример - раскомментируйте для проверки):
        // String s = 123; // Ошибка компиляции: несовместимые типы

        // ОШИБКА ВЫПОЛНЕНИЯ (пример):
        try {
            // Этот код скомпилируется, но вызовет ошибку при выполнении
            int zero = 0;
            int result = 10 / zero; // ArithmeticException: / by zero
        } catch (ArithmeticException e) {
            System.out.println("Поймана ошибка выполнения: " + e.getMessage());
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("> ");
                String line = in.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                List<String> parts = Arrays.asList(line.split("\\s+"));
                String cmd = parts.getFirst().toLowerCase(Locale.ROOT);
                List<String> args = parts.subList(1, parts.size());
                Command c = commands.get(cmd);
                try {
                    if (c == null) throw new InvalidCommandException("Неизвестная команда: " + cmd);
                    c.execute(state, args);
                    state.addScore(1);
                } catch (InvalidCommandException e) {
                    System.out.println("Ошибка: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Непредвиденная ошибка: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    e.printStackTrace(); // Для отладки
                }
            }
        } catch (IOException e) {
            System.out.println("Ошибка ввода/вывода: " + e.getMessage());
        }
    }
}
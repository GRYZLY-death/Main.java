package com.example.dungeon.core;

import com.example.dungeon.model.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class SaveLoad {
    private static final Path SAVE = Paths.get("save.txt");
    private static final Path SCORES = Paths.get("scores.csv");
    private static final Path WORLD = Paths.get("world.ser");

    public static void save(GameState s) {
        saveGameState(s);
        saveWorld(s);
    }

    private static void saveGameState(GameState s) {
        try (BufferedWriter w = Files.newBufferedWriter(SAVE)) {
            Player p = s.getPlayer();

            // Отладочный вывод
            System.out.println("Сохранение игрока: " + p.getName() + ", HP: " + p.getHp() + ", атака: " + p.getAttack());

            // Сохраняем данные игрока
            w.write("player:" + p.getName() + ":" + p.getHp() + ":" + p.getAttack());
            w.newLine();

            // Сохраняем инвентарь
            if (!p.getInventory().isEmpty()) {
                String inv = p.getInventory().stream()
                        .map(i -> i.getClass().getSimpleName() + "=" + i.getName())
                        .collect(Collectors.joining(","));
                w.write("inventory:" + inv);
                System.out.println("Сохранение инвентаря: " + inv);
            } else {
                w.write("inventory:");
                System.out.println("Инвентарь пуст");
            }
            w.newLine();

            // Сохраняем текущую комнату
            w.write("room:" + s.getCurrent().getName());
            w.newLine();

            // Сохраняем счет
            w.write("score:" + s.getScore());
            w.newLine();

            System.out.println("Игра сохранена в " + SAVE.toAbsolutePath());
            writeScore(p.getName(), s.getScore());

        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сохранить игру", e);
        }
    }

    private static void saveWorld(GameState s) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(WORLD.toFile()))) {
            // Сохраняем карту всех комнат
            Map<String, Room> worldMap = collectAllRooms(s.getCurrent());
            oos.writeObject(worldMap);
            System.out.println("Мир сохранен в " + WORLD.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Ошибка сохранения мира: " + e.getMessage());
        }
    }

    private static Map<String, Room> collectAllRooms(Room start) {
        Map<String, Room> allRooms = new HashMap<>();
        collectRoomsRecursive(start, allRooms);
        return allRooms;
    }

    private static void collectRoomsRecursive(Room room, Map<String, Room> collected) {
        if (room == null || collected.containsKey(room.getName())) {
            return;
        }

        collected.put(room.getName(), room);
        for (Room neighbor : room.getNeighbors().values()) {
            collectRoomsRecursive(neighbor, collected);
        }
    }

    public static void load(GameState s) {
        // Сначала загружаем игровое состояние (чтобы знать имя текущей комнаты)
        loadGameState(s);
        // Затем загружаем мир (чтобы восстановить комнаты)
        loadWorld(s);
    }

    private static void loadWorld(GameState s) {
        if (!Files.exists(WORLD)) {
            System.out.println("Сохранение мира не найдено, создается новый мир.");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(WORLD.toFile()))) {
            @SuppressWarnings("unchecked")
            Map<String, Room> worldMap = (Map<String, Room>) ois.readObject();

            // Восстанавливаем связи между комнатами
            restoreRoomConnections(worldMap);

            // Устанавливаем текущую комнату из загруженного мира
            String currentRoomName = s.getCurrent() != null ? s.getCurrent().getName() : "Площадь";
            if (worldMap.containsKey(currentRoomName)) {
                s.setCurrent(worldMap.get(currentRoomName));
                System.out.println("Текущая комната установлена: " + currentRoomName);
            } else {
                // Если комнаты нет в сохранении, берем первую доступную
                Room firstRoom = worldMap.values().iterator().next();
                s.setCurrent(firstRoom);
                System.out.println("Комната " + currentRoomName + " не найдена, установлена: " + firstRoom.getName());
            }

            System.out.println("Мир загружен: " + worldMap.size() + " комнат");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Ошибка загрузки мира: " + e.getMessage());
        }
    }

    private static void restoreRoomConnections(Map<String, Room> worldMap) {
        // Связи уже сохранены в объектах Room, ничего дополнительно делать не нужно
    }

    private static void loadGameState(GameState s) {
        if (!Files.exists(SAVE)) {
            System.out.println("Сохранение игры не найдено.");
            return;
        }

        try (BufferedReader r = Files.newBufferedReader(SAVE)) {
            Map<String, String> map = new HashMap<>();
            for (String line; (line = r.readLine()) != null; ) {
                String[] parts = line.split(":", 2); // Используем : вместо ;
                if (parts.length == 2) {
                    map.put(parts[0], parts[1]);
                    System.out.println("Загружено: " + parts[0] + " = " + parts[1]); // Отладочный вывод
                }
            }

            Player p = s.getPlayer();

// Загрузка данных игрока - универсальный подход
            String playerData = map.get("player");
            if (playerData != null) {
                // Пробуем разные разделители
                String[] pp = playerData.split("[:;]");
                System.out.println("Разобраны данные игрока: " + Arrays.toString(pp));

                if (pp.length >= 4) {
                    // Формат: player:name:hp:attack
                    p.setName(pp[1]);
                    p.setHp(Integer.parseInt(pp[2]));
                    p.setAttack(Integer.parseInt(pp[3]));
                } else if (pp.length >= 3) {
                    // Формат: name:hp:attack
                    p.setName(pp[0]);
                    p.setHp(Integer.parseInt(pp[1]));
                    p.setAttack(Integer.parseInt(pp[2]));
                } else {
                    System.out.println("Неизвестный формат данных игрока");
                    p.setName("Герой");
                    p.setHp(20);
                    p.setAttack(5);
                }
            }

            // Загрузка инвентаря
            p.getInventory().clear();
            String invData = map.get("inventory");
            if (invData != null && !invData.isEmpty()) {
                System.out.println("Загрузка инвентаря: " + invData);
                for (String tok : invData.split(",")) {
                    String[] t = tok.split("=", 2);
                    if (t.length == 2) {
                        switch (t[0]) {
                            case "Potion" -> {
                                p.getInventory().add(new Potion(t[1], 5));
                                System.out.println("Добавлено зелье: " + t[1]);
                            }
                            case "Key" -> {
                                p.getInventory().add(new Key(t[1]));
                                System.out.println("Добавлен ключ: " + t[1]);
                            }
                            case "Weapon" -> {
                                p.getInventory().add(new Weapon(t[1], 3));
                                System.out.println("Добавлено оружие: " + t[1]);
                            }
                            default -> System.out.println("Неизвестный тип предмета: " + t[0]);
                        }
                    }
                }
            }

            // Загрузка счета
            String scoreStr = map.get("score");
            if (scoreStr != null && !scoreStr.isEmpty()) {
                try {
                    int savedScore = Integer.parseInt(scoreStr);
                    s.setScore(savedScore);
                    System.out.println("Счет загружен: " + savedScore);
                } catch (NumberFormatException e) {
                    System.out.println("Ошибка формата счета: " + scoreStr);
                    s.setScore(0);
                }
            } else {
                s.setScore(0);
            }

            System.out.println("✅ Игра успешно загружена");

        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось загрузить игру", e);
        }
    }

    public static void printScores() {
        if (!Files.exists(SCORES)) {
            System.out.println("Пока нет результатов.");
            return;
        }

        try (BufferedReader r = Files.newBufferedReader(SCORES)) {
            System.out.println("Таблица лидеров (топ-10):");
            System.out.println("=========================");

            r.lines()
                    .skip(1)
                    .map(l -> l.split(","))
                    .filter(a -> a.length >= 3)
                    .map(a -> {
                        try {
                            return new Score(a[1], Integer.parseInt(a[2]), LocalDateTime.parse(a[0]));
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(Score::score).reversed())
                    .limit(10)
                    .forEach(s -> System.out.printf("%-12s — %4d очков (%s)%n",
                            s.player(), s.score(), s.date().toLocalDate()));

        } catch (IOException e) {
            System.err.println("Ошибка чтения результатов: " + e.getMessage());
        }
    }

    public static void writeScore(String player, int score) {
        try {
            boolean header = !Files.exists(SCORES);
            try (BufferedWriter w = Files.newBufferedWriter(SCORES,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                if (header) {
                    w.write("timestamp,player,score");
                    w.newLine();
                }
                w.write(LocalDateTime.now() + "," + player + "," + score);
                w.newLine();
            }
        } catch (IOException e) {
            System.err.println("Не удалось записать очки: " + e.getMessage());
        }
    }

    private record Score(String player, int score, LocalDateTime date) {}
}
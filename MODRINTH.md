# SoundManager

Client mod that lets a server play music and sound effects with precise control: start and end points, fades, pause, resume and seeking.

## Features

- Sounds that start from any moment and end at any moment
- Smooth fade in and fade out
- Pause, resume and jump to a position
- Smooth volume and pitch changes while a sound is playing
- Sounds that play everywhere, at a place in the world, or follow an entity
- Respects your volume sliders and your own volume cap
- Emergency switch that stops everything at once

## Requirements

- Minecraft 1.21.11
- Fabric Loader and Fabric API
- The server needs the SoundManagerPlugin plugin for these features; without it the mod does nothing

## Installation

1. Install Fabric Loader for Minecraft 1.21.11.
2. Put `SoundManager-<version>.jar` and Fabric API into the `mods` folder.
3. Join a server that uses SoundManagerPlugin.

## Commands

| Command | Description |
|---|---|
| `/sm off` | Stop all managed sounds and ignore the server until you turn the mod on again |
| `/sm on` | Enable the mod again |
| `/sm status` | Show whether the mod is enabled, how many sounds are playing and the volume cap |

## Configuration

File: `config/soundmanager.json`

| Key | Type | Default | Range | Description |
|---|---|---|---|---|
| `enabled` | boolean | `true` | `true`, `false` | Turn the mod on or off (changed by `/sm off` and `/sm on`) |
| `maxVolume` | number | `10.0` | `0.0..10.0` | Maximum volume the server may use for managed sounds |

```json
{
  "enabled": true,
  "maxVolume": 1.0
}
```

## Controls

None.

## Privacy and safety

- The mod does not download anything and sends nothing to the server.
- It only plays sounds that already exist in the game or in your resource packs.
- Sound categories follow your Minecraft volume sliders.
- A server cannot override `enabled` or `maxVolume`. Use `/sm off` at any time to stop everything.

## Troubleshooting

- **No sound.** Check `/sm status`; if the mod is disabled, use `/sm on`. Check the volume slider of the sound category and `maxVolume` in the config.
- **A sound that starts in the middle does not play.** Seeking may be unavailable after a game update; the game log contains a message from the mod. Update the mod.
- **The mod does not react on a server.** The server must run SoundManagerPlugin of a matching version.

## License

All Rights Reserved.

## Русский

Клиентский мод, который позволяет серверу проигрывать музыку и звуки с точным управлением: начало и конец, плавное нарастание и затухание, пауза, продолжение и перемотка.

### Возможности

- Звуки, которые начинаются с любого момента и заканчиваются в любой момент
- Плавное нарастание и затухание
- Пауза, продолжение и переход к нужной позиции
- Плавное изменение громкости и высоты во время звучания
- Звуки везде, в точке мира или привязанные к сущности
- Учитываются ползунки громкости и ваш собственный предел громкости
- Аварийный выключатель, который сразу останавливает всё

### Требования

- Minecraft 1.21.11
- Fabric Loader и Fabric API
- Для этих возможностей на сервере нужен плагин SoundManagerPlugin; без него мод ничего не делает

### Установка

1. Установите Fabric Loader для Minecraft 1.21.11.
2. Положите `SoundManager-<version>.jar` и Fabric API в папку `mods`.
3. Зайдите на сервер с SoundManagerPlugin.

### Команды

| Команда | Описание |
|---|---|
| `/sm off` | Остановить все управляемые звуки и игнорировать сервер, пока мод не будет включён снова |
| `/sm on` | Снова включить мод |
| `/sm status` | Показать, включён ли мод, сколько звуков играет и предел громкости |

### Настройка

Файл: `config/soundmanager.json`

| Ключ | Тип | По умолчанию | Диапазон | Описание |
|---|---|---|---|---|
| `enabled` | boolean | `true` | `true`, `false` | Включить или выключить мод (меняется командами `/sm off` и `/sm on`) |
| `maxVolume` | число | `10.0` | `0.0..10.0` | Максимальная громкость, которую сервер может использовать для управляемых звуков |

### Приватность и безопасность

- Мод ничего не скачивает и ничего не отправляет на сервер.
- Он проигрывает только звуки, которые уже есть в игре или в ваших ресурспаках.
- Категории звуков подчиняются ползункам громкости Minecraft.
- Сервер не может изменить `enabled` и `maxVolume`. Команда `/sm off` в любой момент останавливает всё.

### Решение проблем

- **Нет звука.** Проверьте `/sm status`; если мод выключен, выполните `/sm on`. Проверьте ползунок категории звука и `maxVolume` в конфиге.
- **Звук, который начинается с середины, не играет.** После обновления игры перемотка может быть недоступна; в логе игры есть сообщение мода. Обновите мод.
- **Мод не реагирует на сервере.** На сервере должен работать SoundManagerPlugin подходящей версии.

### Лицензия

All Rights Reserved.

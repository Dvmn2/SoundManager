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
- A window where you set every option of a sound, listen to it yourself and then send it to other players
- Playlists: build a list of your presets on the server and play it for players one track after another
- While you preview, all other sounds are silenced so that they do not disturb you

## Requirements

- Minecraft 1.21.11
- Fabric Loader and Fabric API
- The server needs the SoundManagerPlugin plugin for these features; without it the mod does nothing, except that the window can still preview sounds for you

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
| `/sm gui` | Open the window for setting up and previewing a sound |

## Configuration

File: `config/soundmanager.json`

| Key | Type | Default | Range | Description |
|---|---|---|---|---|
| `enabled` | boolean | `true` | `true`, `false` | Turn the mod on or off (changed by `/sm off` and `/sm on`) |
| `maxVolume` | number | `10.0` | `0.0..10.0` | Maximum volume the server may use for managed sounds (also applied to your preview) |
| `previewMuteOthers` | boolean | `true` | `true`, `false` | Silence every other sound of the game while a preview plays (button clicks stay audible) |

```json
{
  "enabled": true,
  "maxVolume": 1.0,
  "previewMuteOthers": true
}
```

## Controls

| Control | Description |
|---|---|
| Key "Open SoundManager" | Opens the window. It has no default key: assign one in Options > Controls |

## The SoundManager window

Open it with `/sm gui` or the key above. It has five tabs:

- **Sound**: search all sounds of the game and your resource packs and pick one. The length of the sound is shown. The sort button cycles name A-Z, name Z-A, namespace A-Z and namespace Z-A; "Exclude Minecraft" hides the sounds of the game itself.
- **Parameters**: category, volume, pitch, start, end (or "play to the end"), fade in and fade out. Every value has a slider and a field for exact input.
- **Target**: choose who hears the sound (players or a selector such as `@a`) and where: everywhere, at a position, or on an entity near you. The Distance slider limits a selector such as `@a` to players within that many blocks of you (`@a[distance=..10]`); 0 means no limit. It is unavailable for player names and `@s`, and when the selector already has its own `distance`. The distance is always measured from you, also for a sound at a position.
- **Presets**: save the current settings under a name and load them later.
- **Playlists**: the playlists that live on the server. Create one, add presets to it (Add, then pick a preset), reorder or remove tracks, then choose the players in the field and press Start. Loop and Shuffle switch repeating and random order; Pause, Resume, Stop and Next control the running playlist. Playback is done by the server for the chosen players and is independent of your preview. A playing playlist cannot be edited. A track is a copy of the preset at the time you add it. Presets that play on an entity cannot be added, and a preset that plays to the end needs a sound whose length the window can read; otherwise set an end in the preset. You need the server's permission for playlist commands.

Below the tabs a timeline shows the start, the end, the fades and the position of your preview. **Preview** plays the sound for you only, exactly as it is set, with Pause and Stop. **Send** runs the matching command on the server; it is available only if the server lets you use that command. **Copy** puts the command in your clipboard.

Presets are stored in `config/soundmanager_presets.json` (up to 200, names up to 40 characters). The file is created when you save the first preset.

## Privacy and safety

- The mod does not download anything. It sends something to the server only when you press Send in the window: the chat command you see on screen.
- It only plays sounds that already exist in the game or in your resource packs.
- Sound categories follow your Minecraft volume sliders.
- A server cannot override `enabled` or `maxVolume`. Use `/sm off` at any time to stop everything.

## Troubleshooting

- **No sound.** Check `/sm status`; if the mod is disabled, use `/sm on`. Check the volume slider of the sound category and `maxVolume` in the config.
- **A sound that starts in the middle does not play.** Seeking may be unavailable after a game update; the game log contains a message from the mod. Update the mod.
- **The Send button is grey.** The server does not offer you the command shown in the status line. Ask the server administrator for permission.
- **Preview does not play.** The mod may be turned off (`/sm on`), or the sound category or your volume cap is set to zero.
- **The Playlists tab is empty or grey.** The server must run SoundManagerPlugin 2.1.0 or newer and give you permission for `/soundmanager playlist`. Command results (for example "stop the playlist first") appear in the chat.
- **The length shows "unknown".** The sound file is not in the Ogg Vorbis format or comes from a source the window cannot read; preview and sending still work.
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
- Окно, где можно задать все параметры звука, прослушать его самому и затем отправить другим игрокам
- Плейлисты: список ваших пресетов на сервере, который играет игрокам трек за треком
- Во время прослушивания все остальные звуки заглушаются, чтобы не мешать

### Требования

- Minecraft 1.21.11
- Fabric Loader и Fabric API
- Для этих возможностей на сервере нужен плагин SoundManagerPlugin; без него мод ничего не делает, кроме того, что окно всё равно позволяет прослушивать звуки

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
| `/sm gui` | Открыть окно настройки и прослушивания звука |

### Настройка

Файл: `config/soundmanager.json`

| Ключ | Тип | По умолчанию | Диапазон | Описание |
|---|---|---|---|---|
| `enabled` | boolean | `true` | `true`, `false` | Включить или выключить мод (меняется командами `/sm off` и `/sm on`) |
| `maxVolume` | число | `10.0` | `0.0..10.0` | Максимальная громкость, которую сервер может использовать для управляемых звуков (действует и на ваше прослушивание) |
| `previewMuteOthers` | boolean | `true` | `true`, `false` | Заглушать все остальные звуки игры, пока играет прослушивание (клики кнопок слышны) |

### Окно SoundManager

Открывается командой `/sm gui` или клавишей «Открыть SoundManager» (по умолчанию не назначена, назначьте её в «Настройки > Управление»). Пять вкладок:

- **Звук**: поиск по всем звукам игры и ресурспаков, выбор звука. Показывается длительность. Кнопка сортировки переключает: имя А-Я, имя Я-А, namespace А-Я, namespace Я-А; «Исключить Minecraft» скрывает звуки самой игры.
- **Параметры**: категория, громкость, высота, начало, конец (или «до конца»), нарастание и затухание. У каждого значения есть ползунок и поле для точного ввода.
- **Цель**: кому играть (игроки или селектор, например `@a`) и где: везде, в точке или на сущности рядом с вами. Ползунок «Дистанция» ограничивает селектор вроде `@a` игроками в пределах заданного числа блоков от вас (`@a[distance=..10]`); 0 — без ограничения. Для ников и `@s`, а также если у селектора уже есть свой `distance`, ползунок недоступен. Дистанция всегда считается от вас, в том числе для звука в точке.
- **Пресеты**: сохранение текущих настроек под именем и загрузка позже.
- **Плейлисты**: плейлисты, которые хранятся на сервере. Создайте плейлист, добавьте в него пресеты («Добавить», затем выберите пресет), переставляйте и убирайте треки, затем укажите игроков в поле и нажмите «Запустить». «Повтор» и «Перемешать» включают цикл и случайный порядок; «Пауза», «Продолжить», «Стоп» и «Далее» управляют играющим плейлистом. Воспроизведение выполняет сервер для выбранных игроков, оно не зависит от вашего прослушивания. Играющий плейлист изменить нельзя. Трек — это копия пресета на момент добавления. Пресеты с воспроизведением на сущности добавить нельзя, а пресету «до конца» нужен звук, длину которого окно может прочитать; иначе задайте конец в пресете. Нужно право сервера на команды плейлистов.

Под вкладками таймлайн показывает начало, конец, затухания и позицию вашего прослушивания. **Прослушать** проигрывает звук только вам, ровно с заданными настройками, есть пауза и стоп. **Отправить** выполняет на сервере соответствующую команду; кнопка доступна, только если сервер разрешает вам эту команду. **Копировать** кладёт команду в буфер обмена.

Пресеты хранятся в `config/soundmanager_presets.json` (до 200, имя до 40 символов). Файл создаётся при сохранении первого пресета.

### Приватность и безопасность

- Мод ничего не скачивает. Серверу он отправляет только одно: команду из чата, которую вы видите на экране, когда нажимаете «Отправить» в окне.
- Он проигрывает только звуки, которые уже есть в игре или в ваших ресурспаках.
- Категории звуков подчиняются ползункам громкости Minecraft.
- Сервер не может изменить `enabled` и `maxVolume`. Команда `/sm off` в любой момент останавливает всё.

### Решение проблем

- **Нет звука.** Проверьте `/sm status`; если мод выключен, выполните `/sm on`. Проверьте ползунок категории звука и `maxVolume` в конфиге.
- **Звук, который начинается с середины, не играет.** После обновления игры перемотка может быть недоступна; в логе игры есть сообщение мода. Обновите мод.
- **Кнопка «Отправить» серая.** Сервер не предлагает вам команду, показанную в строке состояния. Попросите администратора выдать право.
- **Прослушивание не играет.** Возможно, мод выключен (`/sm on`), либо громкость категории или ваш предел громкости равны нулю.
- **Вкладка «Плейлисты» пустая или серая.** На сервере должен работать SoundManagerPlugin 2.1.0 или новее, и у вас должно быть право на `/soundmanager playlist`. Результаты команд (например, «сначала остановите плейлист») появляются в чате.
- **Длительность «неизвестна».** Файл звука не в формате Ogg Vorbis или окно не может его прочитать; прослушивание и отправка всё равно работают.
- **Мод не реагирует на сервере.** На сервере должен работать SoundManagerPlugin подходящей версии.

### Лицензия

All Rights Reserved.

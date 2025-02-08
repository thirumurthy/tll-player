# TLL Player

TV network video player software, supports playing web videos.

[TLL Player](https://github.com/thirumurthy/tll-player)

## Usage

- Use the left button of the remote control/touch screen to open the program list

- Use the right button of the remote control/touch screen to double-click to open the configuration

- Use the return button of the remote control to close the program list/configuration

- After opening the configuration page, configure the address and confirm, and update the program list

- After configuring the video source address and turning on "Automatically update the video source after startup", the software will automatically update the video source after startup

- When the program list is displayed, right-click to add/cancel favorites

Note:

- The video source can be set to a local file, such as: file:///mnt/sdcard/tmp/channels.json

- If a local file is set, the software will not automatically update after restarting. Manual confirmation is required.
- Higher versions may require authorization

Currently supported configuration formats:

- json

```json
[
  {
    "group": "Group name",
    "logo": "Icon",
    "name": "Standard title",
    "title": "Title",
    "uris": ["Video address"],
    "headers": {
      "user-agent": ""
    }
  }
]
```

Download and install [releases](https://github.com/thirumurthy/tll-player/releases/)

## Changelog

[Changelog](./HISTORY.md)

## Others

Xiaomi TV can be installed using Xiaomi TV Assistant

If the TV can enable ADB, it can also be installed through ADB:

```shell
adb install tll-player.apk
```

## TODO

- Added program preview
- Compatible with 4.0
- Plugin Store
- UI

# Dynamic Transport — NeoForge 1.21.1

A port of the original Slime Void moving elevator mod to **Minecraft 1.21.1**, **NeoForge 21.1.259+** (21.1 series), and **JDK 21**. SlimevoidLibrary is no longer required. Install the mod on both the server and its clients.

## Build and run

```sh
./gradlew build                 # mod and sources jars in build/libs
./gradlew runGameTestServer     # headless in-game regression tests
./gradlew runClient             # development client
./gradlew runServer             # development dedicated server
```

Use a full Java 21 JDK, not a JRE. The wrapper downloads Gradle 9.2.1. No publishing credentials are needed. The Windows equivalents are `gradlew.bat` and `build.bat`.

## Make an elevator

1. Place an **Elevator Computer** below the home platform. Its home floor is one block above the computer.
2. Sneak-right-click the computer with an **Elevator Tool** to enter maintenance mode and select it. The selected computer highlights green while you hold the tool. An existing platform returns home before maintenance begins; binding stays locked until it arrives.
3. Place **Elevator** blocks above/beside the computer and right-click each with the selected tool to bind them. Each platform must be within the configured horizontal binding range of the computer or another platform. Multiple blocks can form a platform; their vertical offsets are preserved.
4. Place **Floor Markers** alongside the shaft at the desired stops. Bind each with the tool; markers must be within range of a bound platform column. Keep markers outside the platform and rider clearance columns.
5. Right-click a bound marker again with the tool to open **Floor details**. It starts with its stop's Y level, such as **Y 64**. Choose **Rename** to enter a name such as **Lobby**, **Workshop**, or **Rooftop**, then **Save changes**. **Use Y level** removes a custom name and restores the automatic label. The stop height can still be adjusted (marker Y ±2; default Y−2). Right-click a bound elevator face with the tool to enable/disable its floor-selection button. Right-click the computer with the tool to edit its name.
6. Sneak-right-click the computer again to finish maintenance. The green highlights disappear. Right-click a marker or enabled platform face to select a floor. Operational markers also open floor selection if you are still holding the tool. A rising redstone signal beside a marker calls its floor. Calls are queued in order, with duplicate destinations merged.

Sneak-use the selected tool on a bound platform or marker during maintenance to unbind it. One technician controls maintenance at a time. Configuration changes are checked by the server for maintenance ownership, interaction distance, and the active menu.

In **1.0.2 and later**, bound platforms and markers highlight green alongside the selected computer during maintenance. Green means the blocks belong to the selected elevator and maintenance is active; finish maintenance before making floor calls. Messages distinguish active maintenance, a pending return home, missing/unloaded computers, and unbound markers.

In **1.0.3 and later**, floor controls use a phone-inspired interface with rounded light cards, blue actions, and a current-floor indicator. Named floors show their Y level as secondary information. Long lists support **Back/Next**, the mouse wheel, and Page Up/Page Down. The layout adapts to Minecraft's GUI scale. Custom names survive world saves; automatic labels follow changes to stop height.

Interface previews: [floor selector](docs/screenshots/floor-selector.png) · [floor details](docs/screenshots/floor-details.png).

If sneak-right-clicking the computer does nothing, update both the client and server to **1.0.1 or later**. Version 1.0.0 did not handle Minecraft's sneak-use item path, which prevented selecting computers and unbinding components. Replace the old mod jar rather than keeping both versions installed.

During maintenance, use a solid block on a component to camouflage it (consumes one block outside creative mode). Use an empty hand to recover its camouflage. Camouflage survives travel and saving, and stationary components inherit its default light emission. Breaking a component drops its camouflage separately.

The shaft must be loaded and clear, with two air blocks of rider clearance above each platform. Obstructed departures remain queued. An obstruction introduced during travel pauses the platform group until cleared; it never overwrites the obstruction. Riders are carried as passengers and may dismount using the normal sneak key.

Server configuration is generated as `dynamictransport-server.toml` in the world's `serverconfig` directory: `maxElevatorSpeed` defaults to 0.15 blocks/tick, and `bindingRange` defaults to 3 blocks. Each computer supports up to 128 platform blocks and 128 markers.

## Compatibility

This is a source port with new registry IDs and modern saved data. **It does not convert 1.7.10 worlds or old SlimevoidLibrary data.** Back up old worlds and build elevators in a new 1.21.1 world. The original textures and crafting patterns are retained. Obsolete launch configurations and the old unpublished dependency were removed.

Moving camouflage also leaves a temporary light/redstone trail, retaining half of its default weak redstone signal. A graphical client is needed to assess rendering and the feel of riding elevators.

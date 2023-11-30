# MCSDNotifier

Lets your Minecraft Server make [sd_notify(3)](https://www.freedesktop.org/software/systemd/man/latest/sd_notify.html)
calls to support some systemd features:

- Proper detection of when the server is fully started (so dependant units start at the right time)
- Killing the server if startup takes longer
  than [TimeoutStartSec=](https://www.freedesktop.org/software/systemd/man/latest/systemd.service.html#TimeoutStartSec=)
- Killing the server if it hangs (no ticks finished) for longer
  than [WatchdogSec=](https://www.freedesktop.org/software/systemd/man/latest/systemd.service.html#WatchdogSec=). This
  is more reliable than the watchdog integrated in Spigot, because it is implemented outside the server process
- Killing the server if stop takes longer
  than [TimeoutStopSec=](https://www.freedesktop.org/software/systemd/man/latest/systemd.service.html#TimeoutStopSec=)
- Sending a status string back to the service manager that is displayed in `systemctl status`:

        $ systemctl status minecraft
        ● minecraft.service - Minecraft Server
        [...]
        Status: "Running Paper git-Paper-280 (MC: 1.20.2) with 0/20 players, TPS avg: 20.00 20.00 20.00"
        Memory: 1.3G
        CPU: 1min 1.049s

  This string can be overwritten by other plugins by
  registering a custom [StatusProvider](src/main/java/me/agentoak/mcsdnotifier/StatusProvider.java) service

### Requirements

- Java 8+
- Bukkit/Spigot API 1.8+
- `libsystemd` (if you are running systemd you most likely have this already)
- (Only for Servers 1.16 and older) JNA
    - **Only if the plugin tells you that JNA is missing when it starts**, download a copy of the
      latest [JNA](https://mvnrepository.com/artifact/net.java.dev.jna/jna) 5.x jar and put it in the classpath of the
      Server. For [technical reasons](https://github.com/java-native-access/jna/issues/679) this dependency cannot be
      included in the plugin

Paper (or another server based on it) is highly recommended:

- [Better handling of SIGINT (Ctrl+C)](https://github.com/PaperMC/Paper/pull/728) as long as you don't disable the JLine
  console
- Availability of [Server#getTPS()](https://jd.papermc.io/paper/1.14/org/bukkit/Server.html#getTPS--) for a more useful
  status string
- Paper 1.15.2+: Availability of
  [Server#isStopping()](https://jd.papermc.io/paper/1.15/org/bukkit/Server.html#isStopping--)
- Paper 1.19.2+: [Fixed plugin loggers breaking](https://github.com/PaperMC/Paper/pull/5592) when server is shut down by
  signals

Future versions of this plugin will require Paper API 1.16+, but server updates should be unlikely to break older plugin
versions.

### Usage

Copy the `MCSDNotifier-<VERSION>.jar` file of the latest release into your `plugins/` directory. This plugin has no
configuration and saves no data.

Run your Minecraft Server with a systemd service unit with `Type=notify`. For a full example, see
[minecraft.service](minecraft.service). Remember to adjust the user/group, working directory, `Xms/Xmx` and the filename
of the server jar. The server console can be accessed by running
`sudo -u minecraft -- script -c "screen -x minecraft" /dev/null`.

#### Spigot watchdog

Since the systemd watchdog will forcefully kill the server, it is recommended to leave the Spigot watchdog enabled to
try and stop the server gracefully first. In `spigot.yml` configure:

```yaml
settings:
  timeout-time: 60
  restart-on-crash: true
  restart-script: ./should-not-exist-lksdbgqp390t2q33t3w4uj
```

Since MCSDNotifier only notifies the watchdog every 10 seconds, the service manager watchdog can trigger early, so make
sure that `WatchdogSec=` is at least 15 seconds higher than `spigot.yml` `timeout-time`. Leave `restart-on-crash`
enabled, but set `restart-script` to a filename that does not exist. This will make the server stop if Spigot watchdog
detects a hang. The systemd service will then restart the server automatically; there is no need to fiddle around with
shell scripts.

#### If not using Paper 1.15.2+

Bukkit/Spigot and Paper before 1.15.2 lack the `Server#isStopping()` method, so the plugin cannot reliably detect a stop
by command. The service manager will not be informed and continue to expect watchdog updates. Therefore, you should make
sure that `WatchdogSec >= TimeoutStopSec` so server will not be killed too early during shutdown.

#### Testing

To test if the watchdog works as expected, the plugin includes two commands that deliberately hang the server. Both
commands can only be run from the console. Since they will lead to your server being killed, you may experience data
loss.

- `hang-main-and-accept-data-loss`: Permanently hang the server by sleeping indefinitely on the main thread.
- `hang-stop-and-accept-data-loss`: Permanently hang the server by initiating shutdown and sleeping indefinitely.

### Building

Needs JDK 8+ installed. To build the plugin, run:

```
./mvnw
```

Plugin jar file is placed in `target/mcsdnotifier-<VERSION>.jar`. This command will download a copy of Maven and use it
to build the plugin. If you prefer to use a locally installed Maven, just use `mvn` instead.

### Backlog

- Java 9+: Use `ProcessHandle` to get the PID, get rid of `LibC`, use `release` instead of `source/target`
- Bukkit/Spigot API 1.15+: Upgrade JNA dependency to 5.8.0
- Paper API 1.15+: Call `Server#isStopping()` and `Server#getTPS()` directly, use `ServerTickBeginEvent` for
  `NotifyListener#onTick`
- Spigot API 1.16+: Try `plugin.yml` `libraries` key for JNA dependency

### License

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
details.

You should have received a copy of the GNU Lesser General Public License along with this program. If not,
see <https://www.gnu.org/licenses/>.

# libcni-java

A minimal but functional Java port of
[libcni](https://github.com/containernetworking/cni/tree/main/libcni) — the Go
library used by container runtimes to invoke
[CNI](https://github.com/containernetworking/cni/blob/main/SPEC.md) plugins.

The public API mirrors libcni's names one-to-one so that anyone familiar with
the Go library can map it directly.

## Requirements

- JDK 21+ to run against (the build uses a 25 toolchain and targets 21 bytecode)

## Building and testing

```bash
./gradlew build                 # compiles and runs all tests
./gradlew publishToMavenLocal   # installs io.libcni:libcni-java for local consumers
```

The build was a `build.sh` driving `javac` directly, which kept the project
free of a build tool but also meant it could not be depended on: there was no
artifact to resolve. Gradle replaces it — same sources, same 105 tests — so
that a runtime like [containerd-java](https://github.com/miciav/containerd-java)
can consume this as an ordinary dependency.

## Usage

```java
import io.libcni.CNIConfig;
import io.libcni.ConfigLoader;
import io.libcni.NetworkConfigList;
import io.libcni.RuntimeConf;
import io.libcni.invoke.DefaultExec;
import io.libcni.types.Result;

// Search plugins in /opt/cni/bin, cache results under /var/lib/cni.
CNIConfig cni = new CNIConfig(List.of("/opt/cni/bin"), new DefaultExec());

NetworkConfigList list = ConfigLoader.loadNetworkConf("/etc/cni/net.d", "mynet");

RuntimeConf rt = new RuntimeConf();
rt.containerID = "container-1";
rt.netNS = "/var/run/netns/test";
rt.ifName = "eth0";

Result result = cni.addNetworkList(list, rt);   // CNI ADD across all plugins
// ... use result (ips, routes, interfaces, dns) ...

cni.checkNetworkList(list, rt);                 // CNI CHECK
cni.delNetworkList(list, rt);                   // CNI DEL (reverse order)
```

## What's implemented

| Area | Notes |
|------|-------|
| `CNI` / `CNIConfig` | `addNetwork(List)`, `delNetwork(List)`, `checkNetwork(List)`, `validateNetwork(List)`, `getVersionInfo`, result caching |
| Config loading | `.conflist` and legacy `.conf` parsing, `loadNetworkConf`, `injectConf`, `confListFromConf` |
| Plugin invocation | `Exec`/`DefaultExec`/`RawExec`, `CNI_*` env, `prevResult` chaining, result version fixup, `VERSION` detection, configurable per-invocation timeout |
| Result model | `Result`/`CurrentResult` (spec 0.3.0–1.1.0), `Interface`, `IPConfig`, `Route`, `DNS`, family-aware version conversion |
| Types | `PluginConf`, `CniError` + error codes, `PluginInfo` |
| Validation | container ID, network name, interface name |

## Out of scope (intentionally minimal)

- `GC` / `STATUS` commands (CNI 1.1.0)
- Legacy result versions 0.1.0 / 0.2.0 (parsing and conversion)
- The `cniVersions` multi-version config negotiation
- `GetCachedAttachments` and the full upstream `cachedInfo` cache format

## Notes

**Timeouts** — `RawExec` (and `DefaultExec`) accept a per-invocation timeout in
milliseconds; `0` (the default) means no limit. On timeout or thread interrupt
the plugin process and its discoverable descendants are terminated, and the
caller's interrupt status is preserved:

```java
DefaultExec exec = new DefaultExec(30_000); // 30s per plugin invocation
```

**Result conversion** — 0.3.x/0.4.0 and 1.0.0/1.1.0 are handled as two result
families. Downgrading 1.x → 0.4.0 derives `ips[].version` from the address
(rejecting invalid IPs, without DNS); upgrading drops `version` and keeps
`mtu`/`socketPath`/`pciID` only for the 1.x family.

**Cache** — results are cached under `results-v2/<sha256>` where the key is the
SHA-256 of a JSON array of `(network, container, ifname)`. Entries store their
identity and verify it on read, and are written via a temp file + atomic rename.
Legacy `results/` files from older versions are neither read nor deleted; after
upgrading, drain and recreate attachments so DEL/CHECK can find their cached
results.

**Errors** — public JSON-decoding boundaries throw `CniError` (with the original
parse exception preserved via `getCause()`) rather than leaking `JsonSyntaxException`.

## Package layout

```
io.libcni            CNI, CNIConfig, PluginConfig, NetworkConfigList,
                     RuntimeConf, ConfigLoader
io.libcni.types      PluginConf, CniError, Result/CurrentResult, DNS/Route/...
io.libcni.invoke     Exec, DefaultExec, RawExec, Args, FindInPath, Invoke
io.libcni.version    Version, PluginInfo, PluginDecoder
io.libcni.utils      Validation
```

## GraalVM native image

The library ships reachability metadata under
`META-INF/native-image/io.libcni/libcni-java/`, so a consumer's native build works without their
running the tracing agent.

It is needed because Gson populates this library's types by reflection, which a native image
strips by default — and the failure is not a crash. Fields come back null, and a perfectly valid
CNI config is rejected as `missing 'type'`, which reads like the user's mistake. The whole test
suite runs as a native image in CI (`./gradlew nativeTest`) so that stays fixed.

## License

Apache-2.0, following the upstream CNI project. This is an independent port.

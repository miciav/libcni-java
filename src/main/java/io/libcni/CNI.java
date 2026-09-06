package io.libcni;

import io.libcni.types.Result;
import io.libcni.version.PluginInfo;
import java.util.List;

/**
 * The CNI client interface, mirroring {@code libcni.CNI} in the Go library. A
 * minimal subset covering ADD/DEL/CHECK, validation and version queries.
 */
public interface CNI {

    Result addNetworkList(NetworkConfigList list, RuntimeConf rt);

    void checkNetworkList(NetworkConfigList list, RuntimeConf rt);

    void delNetworkList(NetworkConfigList list, RuntimeConf rt);

    Result getNetworkListCachedResult(NetworkConfigList list, RuntimeConf rt);

    Result addNetwork(PluginConfig net, RuntimeConf rt);

    void checkNetwork(PluginConfig net, RuntimeConf rt);

    void delNetwork(PluginConfig net, RuntimeConf rt);

    Result getNetworkCachedResult(PluginConfig net, RuntimeConf rt);

    List<String> validateNetworkList(NetworkConfigList list);

    List<String> validateNetwork(PluginConfig net);

    PluginInfo getVersionInfo(String pluginType);
}

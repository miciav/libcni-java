package io.libcni.types;

/**
 * A network interface created by a plugin, mirroring {@code types100.Interface}.
 * The {@code mtu}/{@code socketPath}/{@code pciID} fields are only present in CNI
 * 1.0.0/1.1.0 results and are omitted when serializing 0.4.0 results.
 */
public class Interface {
    public String name;
    public String mac;
    public Integer mtu;
    public String sandbox;
    public String socketPath;
    public String pciID;

    public Interface() {
    }

    public Interface copy() {
        Interface i = new Interface();
        i.name = name;
        i.mac = mac;
        i.mtu = mtu;
        i.sandbox = sandbox;
        i.socketPath = socketPath;
        i.pciID = pciID;
        return i;
    }
}

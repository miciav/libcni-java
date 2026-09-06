package io.libcni.types;

/** A network interface created by a plugin, mirroring {@code types040.Interface}. */
public class Interface {
    public String name;
    public String mac;
    public String sandbox;

    public Interface() {
    }

    public Interface copy() {
        Interface i = new Interface();
        i.name = name;
        i.mac = mac;
        i.sandbox = sandbox;
        return i;
    }
}

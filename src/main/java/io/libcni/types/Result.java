package io.libcni.types;

/**
 * The result of a CNI plugin execution, mirroring {@code types.Result} in libcni.
 */
public interface Result {

    /** The highest CNI spec result version this result supports without conversion. */
    String version();

    /** Returns this result converted into the requested CNI spec result version. */
    Result getAsVersion(String version);

    /** Serializes this result to its JSON representation. */
    String toJsonString();

    /** Prints the result as JSON to stdout. */
    default void print() {
        System.out.println(toJsonString());
    }
}

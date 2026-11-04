package io.github.igoryok95.structurecontrol;

public final class ControlFailure extends RuntimeException {
    private final String key;
    private final Object[] arguments;

    public ControlFailure(String key, Object... arguments) {
        super(key);
        this.key = key;
        this.arguments = arguments.clone();
    }

    public String key() { return key; }
    public Object[] arguments() { return arguments.clone(); }
}

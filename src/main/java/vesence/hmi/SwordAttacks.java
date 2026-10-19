package vesence.hmi;

/*
 * Exception performing whole class analysis ignored.
 */
public enum SwordAttacks {
    RTL,
    LTR,
    FWD;

    private static final SwordAttacks[] vals;

    public SwordAttacks next() {
        return vals[(this.ordinal() + 1) % vals.length];
    }

    static {
        vals = SwordAttacks.values();
    }
}


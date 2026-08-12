package io.mczju.maggoteers.effect;

/** Detects deprecated amp-255 "fake clear/immunity" patterns in rewards/items YAML. */
public final class Fake255Rules {

    private Fake255Rules() {}

    /**
     * @param immunityTrue when {@code immunity: true} is set, amp/duration are ignored (not fake-clear)
     */
    public static boolean isFakeClear(int amp, int durationTicks, boolean immunityTrue) {
        if (immunityTrue) {
            return false;
        }
        return amp == 255 && (durationTicks == 0 || durationTicks == 1);
    }
}

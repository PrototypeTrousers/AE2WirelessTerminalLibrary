package de.mari_023.fabric.ae2wtlib;

import appeng.core.AEConfig;

public class Config {

    public static double getPowerMultiplier(double range, boolean isOutOfRange) {
        if(!isOutOfRange) return AEConfig.instance().wireless_getDrainRate(range);
        return AEConfig.instance().wireless_getDrainRate(528 * getOutOfRangePowerMultiplier());
    }

    public static double getChargeRate() {
        return 8000;
    }

    private static int getOutOfRangePowerMultiplier() {
        return 2;
    }
}
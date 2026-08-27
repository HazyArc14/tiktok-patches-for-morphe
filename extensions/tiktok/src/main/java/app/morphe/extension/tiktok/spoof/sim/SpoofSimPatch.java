/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/spoof/sim/SpoofSimPatch.java
 */

package app.morphe.extension.tiktok.spoof.sim;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;

@SuppressWarnings("unused")
public class SpoofSimPatch {
    // Values are replaced by the patch-time "Bypass regional restrictions" option.
    public static boolean DEFAULT_REGION_SPOOF_ENABLED = false;
    public static String DEFAULT_REGION_ISO = "ae";
    public static String DEFAULT_REGION_MCC_MNC = "42403";
    public static String DEFAULT_REGION_OPERATOR = "du";

    private static boolean isContextNotSet(String fieldSpoofed) {
        if (Utils.getContext() != null) {
            return false;
        }

        Logger.printException(() -> "Context is not yet set, cannot spoof: " + fieldSpoofed, null);
        return true;
    }

    public static String getInitialCountryIso() {
        return DEFAULT_REGION_SPOOF_ENABLED ? DEFAULT_REGION_ISO : "us";
    }

    public static String getInitialMccMnc() {
        return DEFAULT_REGION_SPOOF_ENABLED ? DEFAULT_REGION_MCC_MNC : "310260";
    }

    public static String getInitialOperatorName() {
        return DEFAULT_REGION_SPOOF_ENABLED ? DEFAULT_REGION_OPERATOR : "T-Mobile";
    }

    private static boolean useInAppRegion() {
        return SettingsStatus.simSpoofEnabled && Settings.SIM_SPOOF.get();
    }

    private static String selectedCountryIso() {
        if (useInAppRegion()) return Settings.SIM_SPOOF_ISO.get();
        return DEFAULT_REGION_SPOOF_ENABLED ? DEFAULT_REGION_ISO : null;
    }

    private static String selectedMccMnc() {
        if (useInAppRegion()) return Settings.SIMSPOOF_MCCMNC.get();
        return DEFAULT_REGION_SPOOF_ENABLED ? DEFAULT_REGION_MCC_MNC : null;
    }

    private static String selectedOperatorName() {
        if (useInAppRegion()) return Settings.SIMSPOOF_OP_NAME.get();
        return DEFAULT_REGION_SPOOF_ENABLED ? DEFAULT_REGION_OPERATOR : null;
    }

    public static String getCountryIso(String value) {
        if (isContextNotSet("countryIso")) return value;

        String iso = selectedCountryIso();
        if (iso == null || !iso.matches("(?i)[a-z]{2}")) return value;

        Logger.printDebug(() -> "Spoofing countryIso from: " + value + " to: " + iso);
        return iso;
    }

    public static String getOperator(String value) {
        if (isContextNotSet("MCC-MNC")) return value;

        String mccMnc = selectedMccMnc();
        if (mccMnc == null || !mccMnc.matches("[0-9]{5,6}")) return value;

        Logger.printDebug(() -> "Spoofing sim MCC-MNC from: " + value + " to: " + mccMnc);
        return mccMnc;
    }

    public static String getOperatorName(String value) {
        if (isContextNotSet("operatorName")) return value;

        String operator = selectedOperatorName();
        if (operator == null || operator.trim().isEmpty()) return value;

        Logger.printDebug(() -> "Spoofing sim operatorName from: " + value + " to: " + operator);
        return operator;
    }

    public static String getMcc(String value) {
        return getMccMncPart(value, true);
    }

    public static String getMnc(String value) {
        return getMccMncPart(value, false);
    }

    private static String getMccMncPart(String value, boolean mcc) {
        if (isContextNotSet(mcc ? "cellMcc" : "cellMnc")) return value;
        String combined = selectedMccMnc();
        if (combined == null || !combined.matches("[0-9]{5,6}")) return value;

        String replacement = mcc ? combined.substring(0, 3) : combined.substring(3);
        Logger.printDebug(() -> "Spoofing " + (mcc ? "cell MCC" : "cell MNC")
                + " from: " + value + " to: " + replacement);
        return replacement;
    }
}

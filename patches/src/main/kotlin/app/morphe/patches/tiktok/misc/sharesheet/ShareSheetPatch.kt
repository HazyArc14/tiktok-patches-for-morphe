package app.morphe.patches.tiktok.misc.sharesheet

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstStringInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val FILTER_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/sharesheet/ShareSheetFilter;"

@Suppress("unused")
val shareSheetPatch = bytecodePatch(
    name = "Share sheet modification",
    description = "Adds toggles and allow-lists for the video share sheet's \"Send to\", " +
        "\"Share via app\", and \"Video Actions\" sections.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableShareSheet()V",
        )

        SharePanelSnapshotConstructorFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1}, $FILTER_CLASS_DESCRIPTOR->filterChannels(Ljava/lang/Object;)V
                invoke-static {p1}, $FILTER_CLASS_DESCRIPTOR->filterActions(Ljava/lang/Object;)V
                invoke-static {p1}, $FILTER_CLASS_DESCRIPTOR->applySendToVisibility(Ljava/lang/Object;)V
            """,
        )

        AutoScrollFeatureGateFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $FILTER_CLASS_DESCRIPTOR->shouldForceAutoScroll()Z
                    move-result v0
                    if-eqz v0, :morphe_stock_auto_scroll_feature_gate
                    return v0
                """,
                ExternalLabel("morphe_stock_auto_scroll_feature_gate", getInstruction(0)),
            )
        }

        AutoScrollActionFactoryFingerprint.method.apply {
            val panelGateStringIndex = indexOfFirstStringInstructionOrThrow("panel_auto_scroll")
            val panelGateResultIndex = indexOfFirstInstructionOrThrow(panelGateStringIndex + 1, Opcode.MOVE_RESULT)
            val panelGateResultRegister =
                getInstruction<OneRegisterInstruction>(panelGateResultIndex).registerA

            addInstructions(
                panelGateResultIndex + 1,
                """
                    invoke-static {v$panelGateResultRegister}, $FILTER_CLASS_DESCRIPTOR->forceAutoScrollPanelAvailability(Z)Z
                    move-result v$panelGateResultRegister
                """,
            )
        }
    }
}

/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/misc/spoof/sim/SpoofSimPatch.kt
 */

package app.morphe.patches.tiktok.misc.spoof.sim

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.proxy.mutableTypes.encodedValue.MutableBooleanEncodedValue.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.encodedValue.MutableStringEncodedValue
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.value.ImmutableBooleanEncodedValue
import com.android.tools.smali.dexlib2.immutable.value.ImmutableStringEncodedValue

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/spoof/sim/SpoofSimPatch;"

private val regionSpoofHooksPatch = bytecodePatch(
    description = "Injects the shared TikTok region identity hooks.",
) {
    dependsOn(
        sharedExtensionPatch,
    )

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        val replacements = mapOf(
            "getSimCountryIso" to "getCountryIso",
            "getNetworkCountryIso" to "getCountryIso",
            "getSimOperator" to "getOperator",
            "getNetworkOperator" to "getOperator",
            "getSimOperatorName" to "getOperatorName",
            "getNetworkOperatorName" to "getOperatorName",
        )

        val patchesByMethod = linkedMapOf<Method, ArrayDeque<Pair<Int, String>>>()
        classDefForEach { classDef ->
            for (method in classDef.methods) {
                val implementation = method.implementation ?: continue
                implementation.instructions.forEachIndexed { index, instruction ->
                    if (
                        instruction.opcode != Opcode.INVOKE_VIRTUAL &&
                        instruction.opcode != Opcode.INVOKE_VIRTUAL_RANGE
                    ) return@forEachIndexed

                    val methodReference = instruction.getReference<MethodReference>()
                        ?: return@forEachIndexed
                    val replacement = when {
                        methodReference.definingClass == "Landroid/telephony/TelephonyManager;" ->
                            replacements[methodReference.name]
                        methodReference.definingClass.startsWith("Landroid/telephony/CellIdentity") &&
                            methodReference.name == "getMccString" -> "getMcc"
                        methodReference.definingClass.startsWith("Landroid/telephony/CellIdentity") &&
                            methodReference.name == "getMncString" -> "getMnc"
                        else -> null
                    }
                    if (replacement == null || methodReference.returnType != "Ljava/lang/String;") {
                        return@forEachIndexed
                    }
                    patchesByMethod.getOrPut(method) { ArrayDeque() }.add(index to replacement)
                }
            }
        }

        patchesByMethod.forEach { (method, patches) ->
            val mutableMethod = mutableClassDefBy(method.definingClass).findMutableMethodOf(method)
            while (patches.isNotEmpty()) {
                val (index, replacement) = patches.removeLast()
                val resultRegister = mutableMethod.getInstruction<OneRegisterInstruction>(index + 1).registerA

                mutableMethod.addInstructions(
                    index + 2,
                    """
                        invoke-static { v$resultRegister }, $EXTENSION_CLASS_DESCRIPTOR->$replacement(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v$resultRegister
                    """,
                )
            }
        }

    }
}

@Suppress("unused")
val simSpoofPatch = bytecodePatch(
    name = "Region spoof",
    description = "Adds in-app controls for changing the region TikTok reads.",
    default = true,
) {
    dependsOn(
        regionSpoofHooksPatch,
        settingsPatch,
    )

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableSimSpoof()V",
        )
    }
}

@Suppress("unused")
val bypassRegionalRestrictionsPatch = bytecodePatch(
    name = "Bypass regional restrictions",
    description = "Uses a selected default region to help bypass regional restrictions.",
    default = false,
) {
    dependsOn(regionSpoofHooksPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    val defaultRegion by stringOption(
        key = "defaultRegion",
        title = "Default region",
        description = "Region to use when applying this patch.",
        default = DEFAULT_REGION_PRESET_ID,
        values = regionPresetOptionValues,
        required = true,
    ) { value ->
        value != null && regionPresetOptions.any { it.id == value }
    }

    execute {
        val selectedPreset = regionPresetOption(defaultRegion!!)
        val extensionClass = mutableClassDefBy(classDefBy(EXTENSION_CLASS_DESCRIPTOR))

        extensionClass.staticFields.first { it.name == "DEFAULT_REGION_SPOOF_ENABLED" }.initialValue =
            ImmutableBooleanEncodedValue.forBoolean(true).toMutable()
        extensionClass.staticFields.first { it.name == "DEFAULT_REGION_ISO" }.initialValue =
            MutableStringEncodedValue(ImmutableStringEncodedValue(selectedPreset.iso))
        extensionClass.staticFields.first { it.name == "DEFAULT_REGION_MCC_MNC" }.initialValue =
            MutableStringEncodedValue(ImmutableStringEncodedValue(selectedPreset.mccMnc))
        extensionClass.staticFields.first { it.name == "DEFAULT_REGION_OPERATOR" }.initialValue =
            MutableStringEncodedValue(ImmutableStringEncodedValue(selectedPreset.operatorName))
    }
}

package org.telegram.plugin

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Bytecode transformer that routes Android vibration, haptic feedback, and notification
 * vibration calls across project and dependency classes through MirrlyHaptics gatekeepers.
 */
abstract class MirrlyVibrationAsmFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {

    override fun isInstrumentable(classData: ClassData): Boolean {
        return !classData.className.startsWith(MIRRLY_HAPTICS_CLASS_PREFIX)
    }

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        val api = instrumentationContext.apiVersion.get()
        val viewOwnerCache = HashMap<String, Boolean>()
        return VibrationClassVisitor(api, nextClassVisitor, classContext, viewOwnerCache)
    }

    private class VibrationClassVisitor(
        api: Int,
        classVisitor: ClassVisitor,
        private val classContext: ClassContext,
        private val viewOwnerCache: MutableMap<String, Boolean>
    ) : ClassVisitor(api, classVisitor) {

        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor? {
            val delegate = super.visitMethod(access, name, descriptor, signature, exceptions) ?: return null
            return VibrationMethodVisitor(api, delegate, classContext, viewOwnerCache)
        }
    }

    private class VibrationMethodVisitor(
        api: Int,
        methodVisitor: MethodVisitor,
        private val classContext: ClassContext,
        private val viewOwnerCache: MutableMap<String, Boolean>
    ) : MethodVisitor(api, methodVisitor) {

        override fun visitMethodInsn(
            opcode: Int,
            owner: String?,
            name: String?,
            descriptor: String?,
            isInterface: Boolean
        ) {
            if (opcode == Opcodes.INVOKEVIRTUAL && !isInterface && owner != null && name != null && descriptor != null) {
                val replacement = findReplacement(classContext, viewOwnerCache, owner, name, descriptor)
                if (replacement != null) {
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        replacement.owner,
                        name,
                        replacement.descriptor,
                        false
                    )
                    return
                }
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }
    }

    private data class MethodReplacement(val owner: String, val descriptor: String)

    private companion object {
        private const val MIRRLY_HAPTICS_CLASS_PREFIX = "org.telegram.messenger.mirrly.MirrlyHaptics"
        private const val MIRRLY_HAPTICS = "org/telegram/messenger/mirrly/MirrlyHaptics"
        private const val MIRRLY_HAPTICS_26 = "org/telegram/messenger/mirrly/MirrlyHapticsApi26"
        private const val MIRRLY_HAPTICS_31 = "org/telegram/messenger/mirrly/MirrlyHapticsApi31"
        private const val MIRRLY_HAPTICS_33 = "org/telegram/messenger/mirrly/MirrlyHapticsApi33"
        private const val VIEW_INTERNAL_NAME = "android/view/View"
        private const val VIEW_DOT_NAME = "android.view.View"

        private fun findReplacement(
            classContext: ClassContext,
            viewOwnerCache: MutableMap<String, Boolean>,
            owner: String,
            name: String,
            descriptor: String
        ): MethodReplacement? {
            when (owner) {
                "android/os/Vibrator" -> {
                    if (name == "vibrate") {
                        return when (descriptor) {
                            "(J)V" -> MethodReplacement(MIRRLY_HAPTICS, "(Landroid/os/Vibrator;J)V")
                            "(JLandroid/media/AudioAttributes;)V" -> MethodReplacement(MIRRLY_HAPTICS, "(Landroid/os/Vibrator;JLandroid/media/AudioAttributes;)V")
                            "([JI)V" -> MethodReplacement(MIRRLY_HAPTICS, "(Landroid/os/Vibrator;[JI)V")
                            "([JILandroid/media/AudioAttributes;)V" -> MethodReplacement(MIRRLY_HAPTICS, "(Landroid/os/Vibrator;[JILandroid/media/AudioAttributes;)V")
                            "(Landroid/os/VibrationEffect;)V" -> MethodReplacement(MIRRLY_HAPTICS_26, "(Landroid/os/Vibrator;Landroid/os/VibrationEffect;)V")
                            "(Landroid/os/VibrationEffect;Landroid/media/AudioAttributes;)V" -> MethodReplacement(MIRRLY_HAPTICS_26, "(Landroid/os/Vibrator;Landroid/os/VibrationEffect;Landroid/media/AudioAttributes;)V")
                            "(Landroid/os/VibrationEffect;Landroid/os/VibrationAttributes;)V" -> MethodReplacement(MIRRLY_HAPTICS_33, "(Landroid/os/Vibrator;Landroid/os/VibrationEffect;Landroid/os/VibrationAttributes;)V")
                            else -> null
                        }
                    }
                    return null
                }

                "android/os/VibratorManager" -> {
                    if (name == "vibrate") {
                        return when (descriptor) {
                            "(Landroid/os/CombinedVibration;)V" -> MethodReplacement(MIRRLY_HAPTICS_31, "(Landroid/os/VibratorManager;Landroid/os/CombinedVibration;)V")
                            "(Landroid/os/CombinedVibration;Landroid/os/VibrationAttributes;)V" -> MethodReplacement(MIRRLY_HAPTICS_33, "(Landroid/os/VibratorManager;Landroid/os/CombinedVibration;Landroid/os/VibrationAttributes;)V")
                            else -> null
                        }
                    }
                    return null
                }

                "android/app/NotificationChannel" -> {
                    return when {
                        name == "enableVibration" && descriptor == "(Z)V" ->
                            MethodReplacement(MIRRLY_HAPTICS_26, "(Landroid/app/NotificationChannel;Z)V")
                        name == "setVibrationPattern" && descriptor == "([J)V" ->
                            MethodReplacement(MIRRLY_HAPTICS_26, "(Landroid/app/NotificationChannel;[J)V")
                        else -> null
                    }
                }

                "android/app/Notification\$Builder" -> {
                    return when {
                        name == "setVibrate" && descriptor == "([J)Landroid/app/Notification\$Builder;" ->
                            MethodReplacement(MIRRLY_HAPTICS, "(Landroid/app/Notification\$Builder;[J)Landroid/app/Notification\$Builder;")
                        name == "setDefaults" && descriptor == "(I)Landroid/app/Notification\$Builder;" ->
                            MethodReplacement(MIRRLY_HAPTICS, "(Landroid/app/Notification\$Builder;I)Landroid/app/Notification\$Builder;")
                        else -> null
                    }
                }

                "androidx/core/app/NotificationCompat\$Builder" -> {
                    return when {
                        name == "setVibrate" && descriptor == "([J)Landroidx/core/app/NotificationCompat\$Builder;" ->
                            MethodReplacement(MIRRLY_HAPTICS, "(Landroidx/core/app/NotificationCompat\$Builder;[J)Landroidx/core/app/NotificationCompat\$Builder;")
                        name == "setDefaults" && descriptor == "(I)Landroidx/core/app/NotificationCompat\$Builder;" ->
                            MethodReplacement(MIRRLY_HAPTICS, "(Landroidx/core/app/NotificationCompat\$Builder;I)Landroidx/core/app/NotificationCompat\$Builder;")
                        else -> null
                    }
                }
            }

            if (name == "performHapticFeedback") {
                if (descriptor == "(I)Z" && isViewOwner(classContext, owner, viewOwnerCache)) {
                    return MethodReplacement(MIRRLY_HAPTICS, "(Landroid/view/View;I)Z")
                }
                if (descriptor == "(II)Z" && isViewOwner(classContext, owner, viewOwnerCache)) {
                    return MethodReplacement(MIRRLY_HAPTICS, "(Landroid/view/View;II)Z")
                }
            } else if (name == "setHapticFeedbackEnabled" && descriptor == "(Z)V") {
                if (isViewOwner(classContext, owner, viewOwnerCache)) {
                    return MethodReplacement(MIRRLY_HAPTICS, "(Landroid/view/View;Z)V")
                }
            }
            return null
        }

        private fun isViewOwner(
            classContext: ClassContext,
            internalOwner: String,
            cache: MutableMap<String, Boolean>
        ): Boolean {
            cache[internalOwner]?.let { return it }
            val result = computeIsViewOwner(classContext, internalOwner)
            cache[internalOwner] = result
            return result
        }

        private fun computeIsViewOwner(
            classContext: ClassContext,
            internalOwner: String
        ): Boolean {
            if (isKnownFrameworkViewPrefix(internalOwner)) {
                return true
            }
            if (internalOwner.startsWith("[") || internalOwner.startsWith("java/") || internalOwner.startsWith("kotlin/")) {
                return false
            }
            val dotName = internalOwner.replace('/', '.')
            return isViewClassRecursive(classContext, dotName, HashSet())
        }

        private fun isKnownFrameworkViewPrefix(internalOwner: String): Boolean {
            return internalOwner == VIEW_INTERNAL_NAME ||
                internalOwner == "android/view/ViewGroup" ||
                internalOwner == "android/view/SurfaceView" ||
                internalOwner == "android/view/TextureView" ||
                internalOwner == "android/webkit/WebView" ||
                internalOwner.startsWith("android/widget/")
        }

        private fun isViewClassRecursive(
            classContext: ClassContext,
            dotName: String,
            visited: MutableSet<String>
        ): Boolean {
            if (!visited.add(dotName)) {
                return false
            }
            if (dotName == VIEW_DOT_NAME) {
                return true
            }
            if (dotName == "java.lang.Object") {
                return false
            }
            val internalName = dotName.replace('.', '/')
            if (isKnownFrameworkViewPrefix(internalName)) {
                return true
            }
            val classData = if (dotName == classContext.currentClassData.className) {
                classContext.currentClassData
            } else {
                classContext.loadClassData(dotName)
            } ?: return false

            val supers = classData.superClasses
            if (VIEW_DOT_NAME in supers) {
                return true
            }
            for (superName in supers) {
                if (superName != "java.lang.Object" && isViewClassRecursive(classContext, superName, visited)) {
                    return true
                }
            }
            return false
        }
    }
}

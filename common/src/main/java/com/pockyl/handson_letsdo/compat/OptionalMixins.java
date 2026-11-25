package com.pockyl.handson_letsdo.compat;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Applies the mixins into the Let's Do mods only where that mod is installed: every one of them is optional. Mixin looks a target
 * class up before it skips a missing one and logs an error for each it cannot find; this asks first, quietly, so a game without
 * Vinery starts with a clean log. A pot whose tick is not the one this mod knows is left alone too: its station then opens without
 * stirring, and the pot cooks its own mod's way.
 */
public final class OptionalMixins implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("handson_letsdo");
    private static final String STIRRED = "StirredPotMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        ClassNode target = classNode(targetClassName);
        if (target == null) {
            return false;
        }
        if (mixinClassName.endsWith(STIRRED) && !ticksItself(target)) {
            LOGGER.warn("{} does not tick the way this mod knows: it is not stirred at its station and cooks its own mod's way",
                    targetClassName);
            return false;
        }
        return true;
    }

    /** Whether the class has the tick the stirring waits at: its own method {@code tick}, taking itself last, returning nothing. */
    private static boolean ticksItself(ClassNode target) {
        for (MethodNode method : target.methods) {
            Type[] arguments = Type.getArgumentTypes(method.desc);
            if (method.name.equals("tick") && Type.getReturnType(method.desc) == Type.VOID_TYPE && arguments.length == 4
                    && arguments[3].getSort() == Type.OBJECT && arguments[3].getInternalName().equals(target.name)) {
                return true;
            }
        }
        return false;
    }

    /** The class's bytecode as its jar has it, without loading it; null when no mod has it. */
    private static ClassNode classNode(String className) {
        try {
            // The lookup Mixin makes for a target itself (ModLauncher serves no untransformed bytecode), minus its log line.
            return MixinService.getService().getBytecodeProvider().getClassNode(className.replace('.', '/'));
        } catch (ClassNotFoundException | IOException e) {
            return null;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}

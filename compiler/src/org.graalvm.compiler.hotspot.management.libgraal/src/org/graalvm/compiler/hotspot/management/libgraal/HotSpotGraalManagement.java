/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.hotspot.management.libgraal;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.function.Supplier;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;

import org.graalvm.compiler.hotspot.HotSpotGraalManagementRegistration;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntime;
import org.graalvm.compiler.hotspot.management.HotSpotGraalRuntimeMBean;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.libgraal.jni.HotSpotToSVMScope;
import org.graalvm.libgraal.jni.JNI;
import org.graalvm.libgraal.jni.JNIUtil;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

@ServiceProvider(HotSpotGraalManagementRegistration.class)
public final class HotSpotGraalManagement implements HotSpotGraalManagementRegistration {

    private static final String HS_BEAN_CLASS_NAME = null;
    private static final byte[] HS_BEAN_CLASS = null;
    private static final String HS_BEAN_FACTORY_CLASS_NAME = null;
    private static final byte[] HS_BEAN_FACTORY_CLASS = null;
    private static final String HS_SVM_CALLS_CLASS_NAME = null;
    private static final byte[] HS_SVM_CALLS_CLASS = null;
    private static final String HS_PUSHBACK_ITER_CLASS_NAME = null;
    private static final byte[] HS_PUSHBACK_ITER_CLASS = null;
    private static final String SVM_HS_ENTRYPOINTS_CLASS_NAME = null;
    private static final byte[] SVM_HS_ENTRYPOINTS_CLASS = null;

    // JNI Globals
    private static volatile JNI.JObject factoryInstance;
    private static JNI.JClass svmToHotSpotEntryPoints;

    private static long jniEnvOffset;

    private HotSpotGraalRuntimeMBean bean;
    private String name;
    private volatile boolean needsRegistration = true;

    public HotSpotGraalManagement() {
    }

    @Override
    public void initialize(HotSpotGraalRuntime runtime, GraalHotSpotVMConfig config) {
        JNI.JObject factory = factoryInstance;
        if (factory.isNull()) {
            synchronized (HotSpotGraalManagement.class) {
                factory = factoryInstance;
                if (factory.isNull()) {
                    jniEnvOffset = config.jniEnvironmentOffset;
                    factory = initializeHotSpotMXBean(getCurrentJNIEnv());
                    factoryInstance = factory;
                }
            }
        }

        if (bean == null) {
            if (runtime.getManagement() != this) {
                throw new IllegalArgumentException("Cannot initialize a second management object for runtime " + runtime.getName());
            }
            try {
                name = runtime.getName().replace(':', '_');
                bean = new HotSpotGraalRuntimeMBean(new ObjectName("org.graalvm.compiler.hotspot:type=" + name), runtime);
                Factory.enqueue(this);
                signal(getCurrentJNIEnv(), svmToHotSpotEntryPoints, factory);
            } catch (MalformedObjectNameException err) {
                err.printStackTrace(TTY.out);
            }
        } else if (bean.getRuntime() != runtime) {
            throw new IllegalArgumentException("Cannot change the runtime a management interface is associated with");
        }
    }

    @Override
    public ObjectName poll(boolean sync) {
        if (bean == null || needsRegistration) {
            return null;
        }
        return bean.getObjectName();
    }

    HotSpotGraalRuntimeMBean getBean() {
        return bean;
    }

    void finishRegistration() {
        needsRegistration = false;
    }

    String getName() {
        return name;
    }

    @SuppressWarnings("try")
    private static JNI.JObject initializeHotSpotMXBean(JNI.JNIEnv env) {
        try (HotSpotToSVMScope<Id> s = new HotSpotToSVMScope<>(Id.DefineClasses, env)) {
            JNI.JObject classLoader = SVMToHotSpotCalls.getJVMCIClassLoader(env);

            if (defineClassInHotSpot(env, classLoader, HS_BEAN_CLASS_NAME, HS_BEAN_CLASS).isNull()) {
                throw throwDefineClassError(HS_BEAN_CLASS_NAME);
            }

            if (defineClassInHotSpot(env, classLoader, HS_BEAN_FACTORY_CLASS_NAME, HS_BEAN_FACTORY_CLASS).isNull()) {
                throw throwDefineClassError(HS_BEAN_FACTORY_CLASS_NAME);
            }

            if (defineClassInHotSpot(env, classLoader, HS_PUSHBACK_ITER_CLASS_NAME, HS_PUSHBACK_ITER_CLASS).isNull()) {
                throw throwDefineClassError(HS_PUSHBACK_ITER_CLASS_NAME);
            }

            if (defineClassInHotSpot(env, classLoader, HS_SVM_CALLS_CLASS_NAME, HS_SVM_CALLS_CLASS).isNull()) {
                throw throwDefineClassError(HS_SVM_CALLS_CLASS_NAME);
            }

            JNI.JClass svmHsEntryPoints = defineClassInHotSpot(env, classLoader, SVM_HS_ENTRYPOINTS_CLASS_NAME, SVM_HS_ENTRYPOINTS_CLASS);
            if (svmHsEntryPoints.isNull()) {
                throw throwDefineClassError(SVM_HS_ENTRYPOINTS_CLASS_NAME);
            }
            svmToHotSpotEntryPoints = JNIUtil.NewGlobalRef(env, svmHsEntryPoints, "Class<" + SVM_HS_ENTRYPOINTS_CLASS_NAME + ">");
            JNI.JObject factory = SVMToHotSpotCalls.createFactory(env, svmHsEntryPoints);
            if (factory.isNull()) {
                throw new InternalError("Failed to instantiate MBean factory on HotSpot side.");
            }
            return JNIUtil.NewGlobalRef(env, factory, HS_BEAN_FACTORY_CLASS_NAME);
        }
    }

    private static JNI.JClass defineClassInHotSpot(JNI.JNIEnv env, JNI.JObject classLoader, String clazzName, byte[] clazz) {
        CCharPointer classData = UnmanagedMemory.malloc(clazz.length);
        ByteBuffer buffer = CTypeConversion.asByteBuffer(classData, clazz.length);
        buffer.put(clazz);
        try (CTypeConversion.CCharPointerHolder className = CTypeConversion.toCString(clazzName)) {
            return JNIUtil.DefineClass(
                            env,
                            className.get(),
                            classLoader,
                            classData,
                            clazz.length);
        } finally {
            UnmanagedMemory.free(classData);
        }
    }

    @SuppressWarnings("try")
    private static void signal(JNI.JNIEnv env, JNI.JClass svmHsEntryPoints, JNI.JObject factory) {
        try (HotSpotToSVMScope<Id> s = new HotSpotToSVMScope<>(Id.NewMBean, env)) {
            SVMToHotSpotCalls.signal(env, svmHsEntryPoints, factory);
        }
    }

    private static JNI.JNIEnv getCurrentJNIEnv() {
        if (jniEnvOffset == 0) {
            throw new IllegalStateException("JniEnvOffset is not yet initialized.");
        }
        long currentJavaThreadAddr = HotSpotJVMCIRuntime.runtime().getCurrentJavaThread();
        return WordFactory.pointer(currentJavaThreadAddr + jniEnvOffset);
    }

    private static RuntimeException throwDefineClassError(String name) {
        throw new InternalError(String.format("Failed to define %s.", name));
    }

    static final class Factory implements Supplier<HotSpotGraalManagementRegistration> {

        private static Queue<HotSpotGraalManagement> registrations = new ArrayDeque<>();

        Factory() {
        }

        @Override
        public HotSpotGraalManagementRegistration get() {
            return new HotSpotGraalManagement();
        }

        static synchronized List<HotSpotGraalManagement> drain() {
            if (registrations.isEmpty()) {
                return Collections.emptyList();
            } else {
                List<HotSpotGraalManagement> res = new ArrayList<>(registrations);
                registrations.clear();
                return res;
            }
        }

        private static synchronized HotSpotGraalManagement enqueue(HotSpotGraalManagement instance) {
            registrations.add(instance);
            return instance;
        }
    }
}

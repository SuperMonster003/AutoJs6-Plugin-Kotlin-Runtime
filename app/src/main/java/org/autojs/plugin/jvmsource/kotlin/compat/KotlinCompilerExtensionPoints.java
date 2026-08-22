package org.autojs.plugin.jvmsource.kotlin.compat;

import org.jetbrains.kotlin.com.intellij.openapi.application.Application;
import org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.kotlin.com.intellij.openapi.extensions.ExtensionPoint;
import org.jetbrains.kotlin.com.intellij.openapi.extensions.ExtensionsArea;

/** Programmatic equivalent of Kotlin 2.3.21 META-INF/extensions/compiler.xml. */
public final class KotlinCompilerExtensionPoints {

    private static final Descriptor[] DESCRIPTORS = new Descriptor[] {
            point("org.jetbrains.kotlin.analyzeCompleteHandlerExtension",
                    "org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension"),
            point("org.jetbrains.kotlin.extensions.internal.callResolutionInterceptorExtension",
                    "org.jetbrains.kotlin.extensions.internal.CallResolutionInterceptorExtension"),
            point("org.jetbrains.kotlin.extensions.internal.typeResolutionInterceptorExtension",
                    "org.jetbrains.kotlin.extensions.internal.TypeResolutionInterceptorExtension"),
            point("org.jetbrains.kotlin.extensions.typeAttributeTranslatorExtension",
                    "org.jetbrains.kotlin.extensions.TypeAttributeTranslatorExtension"),
            dynamicPoint("org.jetbrains.kotlin.diagnosticSuppressor",
                    "org.jetbrains.kotlin.resolve.diagnostics.DiagnosticSuppressor"),
            dynamicPoint("org.jetbrains.kotlin.syntheticResolveExtension",
                    "org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension"),
            dynamicPoint("org.jetbrains.kotlin.assignResolutionAltererExtension",
                    "org.jetbrains.kotlin.resolve.extensions.AssignResolutionAltererExtension"),
            dynamicPoint("org.jetbrains.kotlin.irGenerationExtension",
                    "org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension"),
            dynamicPoint("org.jetbrains.kotlin.simpleNameReferenceExtension",
                    "org.jetbrains.kotlin.plugin.references.SimpleNameReferenceExtension"),
            dynamicPoint("org.jetbrains.kotlin.classBuilderFactoryInterceptorExtension",
                    "org.jetbrains.kotlin.codegen.extensions.ClassBuilderInterceptorExtension"),
            dynamicPoint("org.jetbrains.kotlin.classGeneratorExtension",
                    "org.jetbrains.kotlin.backend.jvm.extensions.ClassGeneratorExtension"),
            dynamicPoint("org.jetbrains.kotlin.packageFragmentProviderExtension",
                    "org.jetbrains.kotlin.resolve.jvm.extensions.PackageFragmentProviderExtension"),
            dynamicPoint("org.jetbrains.kotlin.storageComponentContainerContributor",
                    "org.jetbrains.kotlin.extensions.StorageComponentContainerContributor"),
            dynamicPoint("org.jetbrains.kotlin.extraImportsProviderExtension",
                    "org.jetbrains.kotlin.resolve.extensions.ExtraImportsProviderExtension"),
            point("org.jetbrains.kotlin.fir.extensions.firExtensionRegistrar",
                    "org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter"),
            point("org.jetbrains.kotlin.DescriptorSerializerPlugin",
                    "org.jetbrains.kotlin.serialization.DescriptorSerializerPlugin"),
            point("org.jetbrains.kotlin.defaultErrorMessages",
                    "org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages$Extension"),
    };

    private KotlinCompilerExtensionPoints() {
    }

    public static void register() {
        Application application = ApplicationManager.getApplication();
        if (application == null) {
            throw new IllegalStateException("Kotlin compiler application is unavailable");
        }
        ExtensionsArea area = application.getExtensionArea();
        for (Descriptor descriptor : DESCRIPTORS) {
            if (!area.hasExtensionPoint(descriptor.name)) {
                area.registerExtensionPoint(
                        descriptor.name,
                        descriptor.interfaceName,
                        ExtensionPoint.Kind.INTERFACE,
                        descriptor.dynamic
                );
            }
        }
    }

    private static Descriptor point(String name, String interfaceName) {
        return new Descriptor(name, interfaceName, false);
    }

    private static Descriptor dynamicPoint(String name, String interfaceName) {
        return new Descriptor(name, interfaceName, true);
    }

    private static final class Descriptor {
        private final String name;
        private final String interfaceName;
        private final boolean dynamic;

        private Descriptor(String name, String interfaceName, boolean dynamic) {
            this.name = name;
            this.interfaceName = interfaceName;
            this.dynamic = dynamic;
        }
    }
}

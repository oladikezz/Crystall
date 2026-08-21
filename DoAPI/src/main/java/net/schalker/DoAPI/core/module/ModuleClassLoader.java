package net.schalker.DoAPI.core.module;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

public class ModuleClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private volatile boolean remappedAnything;

    public ModuleClassLoader(String name, URL[] urls, ClassLoader parent) {
        super(name, urls, parent);
    }

    public boolean hasLegacyClasses() {
        return remappedAnything;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (LegacyClassRemapper.isLegacyName(name)) {
            return super.loadClass(LegacyClassRemapper.mapClassName(name), resolve);
        }
        return super.loadClass(name, resolve);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        URL resource = findResource(name.replace('.', '/') + ".class");
        if (resource == null) {
            return super.findClass(name);
        }

        byte[] original;
        try (InputStream stream = resource.openStream()) {
            original = stream.readAllBytes();
        } catch (IOException exception) {
            throw new ClassNotFoundException(name, exception);
        }

        if (LegacyClassRemapper.hasLegacyCoreReference(original)) {
            remappedAnything = true;
        }

        byte[] remapped = LegacyClassRemapper.remap(original);
        if (remapped == original) {
            return super.findClass(name);
        }

        definePackageIfAbsent(name);
        return defineClass(name, remapped, 0, remapped.length);
    }

    private void definePackageIfAbsent(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot <= 0) {
            return;
        }

        String packageName = className.substring(0, lastDot);
        if (getDefinedPackage(packageName) != null) {
            return;
        }

        try {
            definePackage(packageName, null, null, null, null, null, null, null);
        } catch (IllegalArgumentException ignored) {
        }
    }
}

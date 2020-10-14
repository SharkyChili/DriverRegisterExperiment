package org.wayne.classloader;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class SelfDefinedClassLoader extends URLClassLoader {

    public SelfDefinedClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public SelfDefinedClassLoader(String path) {
        //父加载器用SelfDefinedClassLoader的加载器，此时多半是ApplicationClassLoader
        this(path, SelfDefinedClassLoader.class.getClassLoader());
        //System.out.println("SelfDefinedClassLoader parent :" + SelfDefinedClassLoader.class.getClassLoader());
    }

    public SelfDefinedClassLoader(String path, ClassLoader parent) {
        this(buildURLs(path), parent);
    }

    private static URL[] buildURLs(String path) {
        //System.out.println("buildURLs : " + path);
        List<URL> urls = new ArrayList<>();
        File jarPath = new File(path);
        URL url;
        try {
            url = jarPath.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException("something goes wrong when load jars.");
        }
        urls.add(url);
        URL[] array = urls.toArray(new URL[0]);
        return array;
    }


    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // First, check if the class has already been loaded
        Class<?> c = findLoadedClass(name);
        if (c == null) {
            long t0 = System.nanoTime();
            try {
                c = findClass(name);
            } catch (ClassNotFoundException e) {
                // ClassNotFoundException thrown if class not found
                // from the non-null parent class loader
            }

            if (c == null) {
                // If still not found, then invoke findClass in order
                // to find the class.
                long t1 = System.nanoTime();

                c = getParent().loadClass(name);

                // this is the defining class loader; record the stats
                sun.misc.PerfCounter.getParentDelegationTime().addTime(t1 - t0);
                sun.misc.PerfCounter.getFindClassTime().addElapsedTimeFrom(t1);
                sun.misc.PerfCounter.getFindClasses().increment();
            }
        }
        if (resolve) {
            resolveClass(c);
        }
        return c;
    }
}

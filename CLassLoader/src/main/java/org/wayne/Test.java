package org.wayne;

import org.wayne.classloader.SelfDefinedClassLoader;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Test {
    /**
     * java -DWithCfn="true" -cp ClassLoader.jar org.wayne.Test
     * or
     * java -DWithCfn="false" -cp ClassLoader.jar org.wayne.Test
     *
     */
    public static void main(String[] args) {
        String jarPath = Test.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        String[] projectPath = jarPath.split("target");
        String dependencyPath =
                projectPath[0] + "target" + File.separator + "WaynePlugin.jar";
        System.out.println(dependencyPath);
        SelfDefinedClassLoader classLoader = new SelfDefinedClassLoader(dependencyPath);

        String withCfn = System.getProperty("WithCfn");
        Class<?> clazz;
        try {
            if("true".equalsIgnoreCase(withCfn)){
                clazz = classLoader.loadClass("org.wayne.TestWithCfn");
            }else {
                clazz = classLoader.loadClass("org.wayne.TestWithoutCfn");
            }
            Method test = clazz.getDeclaredMethod("test");
            test.invoke(clazz.newInstance());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
    }
}

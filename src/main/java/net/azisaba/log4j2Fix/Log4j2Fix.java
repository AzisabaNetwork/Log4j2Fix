package net.azisaba.log4j2Fix;

import net.blueberrymc.native_util.NativeUtil;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Log4j2Fix {
    private static boolean registered = false;
    private static String arg;
    private static Instrumentation inst;

    public static void main(String[] args) throws IOException {
        transformClasses();
        List<String> arguments = new ArrayList<>(Arrays.asList(args));
        if (arguments.isEmpty()) {
            System.out.println("wat");
            return;
        }
        String main = arguments.remove(0);
        String premain = null;
        File file = new File(main);
        if (file.exists()) {
            System.out.println("Using " + file.getAbsolutePath() + " for classpath");
            NativeUtil.appendToSystemClassLoaderSearch(file.getAbsolutePath());
            ZipFile zipFile = new ZipFile(file);
            ZipEntry zipEntry = zipFile.getEntry("META-INF/MANIFEST.MF");
            if (zipEntry == null) {
                if (arguments.isEmpty()) {
                    System.out.println("Could not find Main-Class attribute from " + file.getPath());
                    System.exit(1);
                }
                main = arguments.remove(0);
            } else {
                BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(zipEntry)));
                String read;
                boolean found = false;
                while ((read = reader.readLine()) != null) {
                    if (read.startsWith("Main-Class: ")) {
                        main = read.replace("Main-Class: ", "");
                        found = true;
                    } else if (read.startsWith("Premain-Class")) {
                        premain = read.replace("Premain-Class: " , "");
                    }
                }
                reader.close();
                if (!found) {
                    if (arguments.isEmpty()) {
                        System.out.println("Could not find Main-Class attribute from " + file.getPath());
                        System.exit(1);
                    }
                    main = arguments.remove(0);
                }
            }
        }
        if (premain != null) {
            try {
                Class<?> clazz = Class.forName(premain);
                Method m = clazz.getMethod("premain", String.class, Instrumentation.class);
                m.invoke(null, arg, inst);
            } catch (ReflectiveOperationException e) {
                System.err.println("Failed to invoke premain method of class " + premain);
                e.printStackTrace();
            }
        }
        try {
            Class<?> clazz = Class.forName(main);
            Method m = clazz.getMethod("main", String[].class);
            m.invoke(null, (Object) arguments.toArray(new String[0]));
        } catch (ReflectiveOperationException e) {
            System.err.println("Failed to invoke main method of class " + main);
            e.printStackTrace();
        }
    }

    public static void agentmain(String args, Instrumentation instrumentation) throws IOException {
        transformClasses();
        arg = args;
        inst = instrumentation;
    }

    public static void premain(String args, Instrumentation instrumentation) throws IOException {
        transformClasses();
        arg = args;
        inst = instrumentation;
    }

    public static void transformClasses() throws IOException {
        if (registered) return;
        registered = true;
        NativeUtil.registerClassLoadHook((classLoader, s, aClass, protectionDomain, bytes) -> {
            if ("org/apache/logging/log4j/core/lookup/JndiLookup".equals(s) || s.startsWith("org/apache/logging/log4j/core/lookup/JndiLookup$")) {
                sneakyThrow(new ClassNotFoundException());
            }
            return null;
        });
        if (Boolean.getBoolean("log4j2Fix.loadReflectionUtil")) {
            transformClass("org.apache.logging.log4j.util.ReflectionUtil", true);
            transformClass("org.apache.logging.log4j.util.ReflectionUtil$PrivateSecurityManager", true);
        }
    }

    public static void transformClass(String className, boolean loadNow) throws IOException {
        Class<?> clazz = Arrays.stream(NativeUtil.getLoadedClasses()).filter(clazz2 -> clazz2.getTypeName().equals(className)).findFirst().orElse(null);
        if (clazz == null) {
            String path = "/classes/" + className.replace('.', '/') + ".class";
            InputStream in = Log4j2Fix.class.getResourceAsStream(path);
            if (in == null) throw new RuntimeException("Could not find '" + path + "' in jar file");
            byte[] newClassBytes = readAllBytes(in);
            if (loadNow) {
                System.out.println("Loading class " + className + " from " + path);
                Class<?> actualClass = NativeUtil.defineClass(className.replace('.', '/'), ClassLoader.getSystemClassLoader(), newClassBytes, newClassBytes.length);
                System.out.println("Loaded class '" + actualClass.toGenericString() + "' from " + path);
            } else {
                System.out.println(className + " is not loaded, registering class load hook");
                NativeUtil.registerClassLoadHook((classLoader, s, aClass, protectionDomain, bytes) -> {
                    if (s.equals(className.replace('.', '/'))) {
                        System.out.println("Transformed " + className);
                        return newClassBytes;
                    }
                    return null;
                });
            }
        } else {
            System.err.println(className + " is already loaded, cannot process " + className);
        }
    }

    public static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024 * 16];
        int read;
        while ((read = in.read(buf, 0, 1024 * 16)) > 0) {
            baos.write(buf, 0, read);
        }
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    public static <X extends Throwable> void sneakyThrow(Throwable throwable) throws X {
        throw (X) throwable;
    }
}

# git
https://github.com/fw1036994377/DriverRegisterExperiment.git
# 前言
网上经常看到有人说，Class.forName("com.mysql.jdbc.Driver")类似的注册驱动语句可以不写，Driver中static代码块注册了驱动没错，可是DriverManager中static代码块也通过SPI机制注册了驱动，重复注册了，其实Class.forName("com.mysql.jdbc.Driver")可以不写。<br/>
可是呢，本人是一个喜欢批判性思维的人，在写一些类加载器相关的功能时，再看这段话，感觉不大对，但是又不确定，因此特地做个实验，本博客作为记录。
# 自定义个一个驱动
首先，自定义一个驱动，为什么要自定义驱动呢？因为我们可能想通过打印一些东西来查看代码运行的顺序，并且也是练手嘛，这样不论是对SPI，还是对驱动的了解都会增进一点，当然，必要的逻辑还是要有的，比如Driver中的static注册啊之类的。话不多说，开干。<br/>
创建sources/META-INF/services/java.sql.Driver，写入org.wayne.Driver<br/>
创建驱动类Driver，其中比较重要的部分见如下
``` 
public class Driver implements java.sql.Driver {
    static
    {
        try
        {
            // moved the registerDriver from the constructor to here
            // because some clients call the driver themselves (I know, as
            // my early jdbc work did - and that was based on other examples).
            // Placing it here, means that the driver is registered once only.
            Driver driver = new Driver();
            System.out.println("WayneDriver register " + driver.getClass().getClassLoader());
            java.sql.DriverManager.registerDriver(driver);
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }



    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        System.out.println("WayneDriver-1.0-SNAPSHOT");
        Properties properties = new Properties();
        return makeConnection(url,properties);
    }

    private static Connection makeConnection(String url, Properties props) throws SQLException {
        if(url.contains("wayne")){
            return new WayneConnection();
        }
        return null;
    }
```
创建Connection类，截取部分如图
```
public class WayneConnection implements java.sql.Connection{



    @Override
    public Statement createStatement() throws SQLException {
        System.out.println("WayneDriver-1.0-SNAPSHOT");
        Statement statement = new WayneStatement();
        System.out.println("statement : " + statement);
        System.out.println("statement : " + statement.getClass().getName());
        System.out.println("statement : " + statement.getClass().getClassLoader());
        System.out.println("Statement Class : " + Statement.class.getClassLoader());

        return statement;
    }
```
# psvm执行一下
```
public class Test {
    public static void main(String[] args) {
        try {
            Class.forName("org.wayne.Driver");
            Connection conn = DriverManager.getConnection("jdbc:wayne://172.19.1.49:7300/dwtmppdb");
            Statement statement = conn.createStatement();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
```
执行结果是毫无疑问没问题的

注释掉Class.forName("org.wayne.Driver");<br/>

**执行结果也没有问题，也就是说，在psvm下驱动确实可以不手动注册**

# 我的疑问
我仔细看了一下代码<br/>
## 1.Class.forName("org.wayne.Driver")中
```
    static {
        try {
            DriverManager.registerDriver(new Driver());
        } catch (SQLException var1) {
            throw new RuntimeException("Can't register driver!");
        }
    }
```
注册的是当前类加载器驱动下的Driver，在psvm中，也就是ApplicationClassLoader<br/>
## 2.DriverManager中
```
static {
        loadInitialDrivers();
        println("JDBC DriverManager initialized");
    }
    
private static void loadInitialDrivers() {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {

                ServiceLoader<Driver> loadedDrivers = ServiceLoader.load(Driver.class);
                Iterator<Driver> driversIterator = loadedDrivers.iterator();

                try{
                    while(driversIterator.hasNext()) {
                        driversIterator.next();
                    }
                } catch(Throwable t) {
                // Do nothing
                }
                return null;
            }
        });

    }
```
ServiceLoad中
```
    public static <S> ServiceLoader<S> load(Class<S> service) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return ServiceLoader.load(service, cl);
    }
        public static <S> ServiceLoader<S> load(Class<S> service,
                                            ClassLoader loader)
    {
        return new ServiceLoader<>(service, loader);
    }
        private ServiceLoader(Class<S> svc, ClassLoader cl) {
        service = Objects.requireNonNull(svc, "Service interface cannot be null");
        loader = (cl == null) ? ClassLoader.getSystemClassLoader() : cl;
        acc = (System.getSecurityManager() != null) ? AccessController.getContext() : null;
        reload();
    }
        public void reload() {
        providers.clear();
        lookupIterator = new LazyIterator(service, loader);
    }
    public boolean hasNext() {
    //省略部分代码
                return hasNextService();
        }
    private boolean hasNextService() {
			configs = loader.getResources(fullName);
            while ((pending == null) || !pending.hasNext()) {
                if (!configs.hasMoreElements()) {
                    return false;
                }
                pending = parse(service, configs.nextElement());
            }
            nextName = pending.next();
            return true;
        }
    public S next() {
                return nextService();
        }
    private S nextService() {
                c = Class.forName(cn, false, loader);
        }
```
也就是说，ServiceLoader，也就是所谓的JDK SPI，采用的是Thread.currentThread().getContextClassLoader()，也就是所谓的线程上下文类加载器来执行SPI操作的<br/>
而且，SPI加载到的驱动在driversIterator.next()中，会被线程上下文类加载加载，并在格子的static{}中注册到DriverManager中
## 3.java.sql.DriverManager#getConnection(java.lang.String)
```
    public static Connection getConnection(String url)
        throws SQLException {

        java.util.Properties info = new java.util.Properties();
        return (getConnection(url, info, Reflection.getCallerClass()));
    }
    
    private static Connection getConnection(
        String url, java.util.Properties info, Class<?> caller) throws SQLException {
        ClassLoader callerCL = caller != null ? caller.getClassLoader() : null;
        synchronized(DriverManager.class) {
            if (callerCL == null) {
                callerCL = Thread.currentThread().getContextClassLoader();
            }
        }

        println("DriverManager.getConnection(\"" + url + "\")");

        // Walk through the loaded registeredDrivers attempting to make a connection.
        // Remember the first exception that gets raised so we can reraise it.
        SQLException reason = null;

        for(DriverInfo aDriver : registeredDrivers) {
            // If the caller does not have permission to load the driver then
            // skip it.
            if(isDriverAllowed(aDriver.driver, callerCL)) {
                try {
                    println("    trying " + aDriver.driver.getClass().getName());
                    Connection con = aDriver.driver.connect(url, info);
                    if (con != null) {
                        // Success!
                        println("getConnection returning " + aDriver.driver.getClass().getName());
                        return (con);
                    }
                } catch (SQLException ex) {
                    if (reason == null) {
                        reason = ex;
                    }
                }

            } else {
                println("    skipping: " + aDriver.getClass().getName());
            }

        }
        
            private static boolean isDriverAllowed(Driver driver, ClassLoader classLoader) {
        boolean result = false;
        if(driver != null) {
            Class<?> aClass = null;
            try {
                aClass =  Class.forName(driver.getClass().getName(), true, classLoader);
            } catch (Exception ex) {
                result = false;
            }

             result = ( aClass == driver.getClass() ) ? true : false;
        }

        return result;
    }
```
也就是说，在获取连接时，获取的是当前类加载加载过的驱动，在psvm中执行，就是ApplicationClassLoader<br/>

可能有点长，咱们来回顾一下<br/>
- （据说可被注释）往DriverManager中注册一个驱动，类加载器为当前类加载器
- DriverManager初始化时，SPI利用线程上下文类加载器寻找驱动并注册，类加载器为线程上下文加载器
- getConnection时，获取当前类加载器加载过的驱动

当第一步没被注释过时，一切OK，第一步+第三步，完美对应上<br/>
**第一步如果被注释，那么DriverManager中保存的是线程上下文加载器加载的驱动，获取连接却是获取当前类加载器加载过的驱动，如果线程上下文加载器与当前加载器不一致，那么就拿不到这个驱动。**
# 自定义类加载器
既然要线程上下文加载器与当前加载器不一致，那么我们就自定义一个类加载器吧，当然，完全可以利用JDK中的URLClassLoader等加载器来做，但是我在工作项目中本身就定义了类加载器，基本上拿来就能用，因此我们就自定义一个类加载器吧。<br/>
```
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
```

我们写一个模块，依赖我们的驱动，然后打包成fatjar到某个指定位置，然后在另一个模块中用我们自定义的类加载器来加载这个fatjar并且调用他的方法。  
这里代码过多，就不贴出来了，只贴个入口吧，具体代码请查看git
```
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
```
git仓库内的代码是可以执行的，但是有一些步骤需要做，例如
- install WayneDriver模块
- package整个项目（以后修改的话可以只package ClassLoader模块）
- 本人在windows环境开发，wsl环境执行

结果截图如下
![](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/3689f9fdc18749c986140ad5a88867b5~tplv-k3u1fbpfcp-watermark.image)

很显然，前面的推论成立。

即：**如果线程上下文加载器与当前加载器不一致，那么就拿不到这个驱动。**


# 最后的话
- 其实最近能写博客的东西超级多，可是工作实在太忙，没空写博客，太遗憾了。
- 人云亦云是不行的。
- 写博客真累，就这么点东西折腾我几个小时。
- 线程上下文加载器，我实在没明白这个东西到底有什么意义。在我看来，他就是个类似ThreadLocal<ClassLoader>的东西，如果说加上继承父类的话，那也就是个InheritableThreadLocal<ClassLoader>，他有什么超脱ThreadLocal的功能吗？我现在是没看出来。如果有大佬懂，望指点一二。

类加载机制的奥妙。

1、什么是类的加载

    '类的加载'指的是将'类的.class文件中的二进制数据' '读入到内存中'，将其'放在'运行时数据区的'方法区内'，
    然后'在堆区创建一个java.lang.Class对象'，用来封装'类在方法区内的数据结构'。

    '类的加载'的'最终产品' '是位于堆区中的Class对象'， (lincq:我把最终产品理解为，最终输出的东西)
    'Class对象''封装了' '类在方法区内' 的'数据结构'，并且'向Java程序员提供了'访问方法区内的数据结构的'接口'。

    '类加载器'并不需要'等到某个类被“首次主动使用”时'再加载它，
    'JVM规范'允许'类加载器'在预料某个类将要被使用时就'预先加载'它，如果'在预先加载的过程中'遇到了.class'文件缺失或存在错误'，
    '类加载器'必须'在程序首次主动使用该类时'才'报告错误'（'LinkageError错误'）
    如果这个'类一直没有被'程序'主动使用'，那么'类加载器就不会报告错误'
    加载.class文件的方式
        - 从'本地系统'中直接加载
        - 通过'网络下载.class文件'
        - 从zip，jar等'归档文件'中加载.class文件
        - 从'专有数据库中提取'.class文件
        - 将'Java源文件动态编译'为.class文件


2、类的生命周期
    其中'类加载的过程'包括了'加载、验证、准备、解析、初始化' '五个阶段'。
    在这五个阶段中，'加载、验证、准备和初始化' '这四个阶段发生的顺序是确定的'，而'解析阶段则不一定'，
    它在'某些情况下'可以'在初始化阶段之后开始'，这是'为了支持' 'Java语言的运行时绑定'（也'成为动态绑定或晚期绑定'）。
    另外注意这里的几个阶段'是按顺序开始'，而'不是按顺序进行或完成'，因为'这些阶段'通常都是'互相交叉地混合进行'的，
    通常'在一个阶段执行的过程中' '调用或激活' '另一个阶段'。

    加载 —> '查找并加载' '类的二进制数据' 
        '加载'是类加载过程的'第一个阶段'，在加载阶段，'虚拟机'需要'完成'以下'三件事情'：

        通过一个'类的全限定名'来'获取'其'定义的二进制字节流'。
        将这个'字节流'所代表的'静态存储结构'转化为'方法区的运行时数据结构'。
        在'Java堆中'生成一个'代表这个类的java.lang.Class对象'，作为对'方法区中这些数据的访问入口'。
        相对于'类加载的其他阶段'而言，加载阶段（准确地说，是加载阶段'获取类的二进制字节流的动作'）是'可控性最强的阶段'，
        因为'开发人员'既可以'使用系统提供的类加载器'来完成加载，也可以'自定义自己的类加载器'来完成加载。

        加载阶段完成后，'虚拟机外部的二进制字节流'就按照'虚拟机所需的格式' '存储在方法区之中'，
        而且'在Java堆中'也创建一个java.lang.'Class类的对象'，这样便可以'通过该对象' '访问方法区中的这些数据'。(类在方法区内的数据结构)

    验证：'确保'被加载的'类的正确性'

        验证是'连接阶段的第一步'，'这一阶段的目的'是'为了确保' 'Class文件的字节流中包含的信息' '符合当前虚拟机的要求'，
        '并且' '不会危害' '虚拟机自身的安全'。
        '验证阶段'大致会完成'4个阶段的检验动作'：

            a.文件格式验证：'验证字节流'是否符合'Class文件格式的规范'；
            例如：是否以'0xCAFEBABE'开头、'主次版本号'是否在'当前虚拟机的处理范围之内'、'常量池中的常量'是否有'不被支持的类型'。
            
            b.元数据验证：对'字节码描述的信息'进行'语义分析'（注意：对比javac编译阶段的语义分析），
            以保证'其描述的信息'符合'Java语言规范'的要求；
            例如：这个类'是否有父类'，除了java.lang.Object之外。
            
            c.字节码验证：通过数据流和控制流分析，确定程序语义是合法的、符合逻辑的。
            d.符号引用验证：确保解析动作能正确执行。

        '验证阶段'是'非常重要'的，但'不是必须的'，它'对'程序运行期'没有影响'，
        如果'所引用的类'经过'反复验证'，
        那么可以'考虑采用-Xverifynone参数'来'关闭大部分的类验证措施，以缩短虚拟机类加载的时间'。



    准备：为 '类的静态变量' '分配内存'，并将其'初始化为默认值'

        准备阶段是'正式为类变量分配内存'并设置'类变量初始值'的阶段，这些'内存'都将'在方法区中分配'。
        对于该阶段有以下几点需要注意：

        1、这时候'进行内存分配'的'仅包括类变量'（static），而'不包括实例变量'，实例变量会'在对象实例化时'随着对象一块'分配在Java堆中'。
        2、这里所设置的'初始值'通常情况下是'数据类型默认的零值'（如0、0L、null、false等），'而不是'被在Java代码中'被显式地赋予的值'。
            假设一个类变量的定义为：public static int value = 3；

            那么变量value'在准备阶段过后的初始值为0'，'而不是3'，因为这时候尚未开始执行任何Java方法，
            而'把value赋值为3的指令'是'在程序编译后，存放于类构造器<clinit>（）方法之中的'，所以'把value赋值为3的动作' '将在初始化阶段'才会执行。

            这里还需要注意如下几点：

            对基本数据类型来说，对于'类变量'（static）和'全局变量'，如果'不显式地对其赋值'而直接使用，则系统会'为其赋予默认的零值'，
            而对于'局部变量'来说，'在使用前' '必须显式地为其赋值'，否则'编译时不通过'。(lincq:验证正确)
            
            对于'同时被static和final修饰的常量'，必须'在声明的时候'就'为其显式地赋值'，否则'编译时不通过'；
            
            而只'被final修饰的常量'， 使用final关键字'修饰成员变量'时，虚拟机'在实例化对象的时候' '不会对其进行初始化'。
            因此使用final修饰成员变量时，需要'在定义变量的同时' '赋予一个初始值'。
            
            对于'引用数据类型reference'来说，如'数组引用、对象引用'等，
            如果'没有对其进行显式地赋值'而'直接使用'，系统都会为其赋予'默认的零值'，即'null'。
            
            如果'在数组初始化时'没有'对数组中的各元素赋值'，那么'其中的元素'将'根据对应的数据类型'而被'赋予默认的零值'。
        
        3、如果'类字段的字段属性表'中'存在ConstantValue属性'，即同时被final和static修饰，那么'在准备阶段' 变量value就会被'初始化为ConstValue属性所指定的值'。
        假设上面的类变量value被定义为： public static final int value = 3；
        编译时Javac'将会为value生成ConstantValue属性'，在'准备阶段'虚拟机就会'根据ConstantValue的设置' '将value赋值为3'。
        我们可以理解为'static final常量' '在编译期'就将'其结果放入了'调用它的'类的常量池'中


    解析：把'类中的符号引用'转换为'直接引用'

        '解析阶段'是虚拟机将'常量池内的符号引用'替换为'直接引用'的过程，
        解析动作'主要针对' '类或接口、字段、类方法、接口方法、方法类型、方法句柄和调用点限定符' '7类符号引用'进行。

        '符号引用'就是'一组符号来描述目标'，可以是'任何字面量'。
        '直接引用'就是直接指向'目标的指针'、'相对偏移量'或一个'间接定位到目标的句柄'。



初始化

    初始化，为'类的静态变量'赋予'正确的初始值'，JVM负责对类进行初始化，主要'对类变量进行初始化'。在Java中对类变量进行初始值设定有两种方式：

        ① '声明类变量时' '指定初始值'
        ② '使用静态代码块' '为类变量指定初始值'

JVM初始化步骤

    1、假如这个'类还没有被加载和连接'，则程序'先加载并连接该类'
    2、假如'该类的直接父类' '还没有被初始化'，则'先初始化其直接父类'
    3、假如'类中有初始化语句'，则'系统依次执行这些初始化语句'

类初始化时机：只有'当对类的主动使用的时候' '才会导致类的初始化'，'类的主动使用' 包括以下六种：

    - '创建类的实例'，也就是'new的方式'
    - '访问' '某个类或接口的静态变量'，或者 '对该静态变量赋值'
    - '调用类的静态方法'
    - '反射'（如Class.forName(“com.shengsiyuan.Test”)）
    - '初始化某个类的子类'，则'其父类也会被初始化'
    - Java虚拟机启动时'被标明为启动类的类'（Java Test），直接使用java.exe命令来运行某个主类

结束JVM进程生命周期

    在如下几种情况下，Java虚拟机将结束生命周期

    执行了'System.exit()'方法
    程序'正常执行结束'
    程序在执行过程中'遇到了异常'或'错误而异常终止'
    由于'操作系统出现错误'而'导致Java虚拟机进程终止'

3、类加载器
寻找类加载器，先来一个小例子

    package com.neo.classloader;
    public class ClassLoaderTest {
        public static void main(String[] args) {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            System.out.println(loader);
            System.out.println(loader.getParent());
            System.out.println(loader.getParent().getParent());
        }
    }
    运行后，输出结果：
        sun.misc.Launcher$AppClassLoader@64fef26a
        sun.misc.Launcher$'ExtClassLoader'@1ddd40f3
        null
    从上面的结果可以看出，并没有获取到'ExtClassLoader的父Loader'，
    原因是Bootstrap Loader（引导类加载器）是'用C语言实现'的，找不到一个确定的'返回父Loader的方式'，于是就'返回null'。

这几种类加载器的层次关系如下图所示：

    Bootstrap ClassLoader (启动类加载器)
        ExtClassLoader(扩展类加载器)
            AppclassLoader 应用类加载器
                User ClassLoader 1 (自定义类加载器)
                User ClassLoader 2 (自定义类加载器)

注意：这里'父类加载器'并'不是通过继承关系'来实现的，而是'采用组合实现'的。

站在Java虚拟机的角度来讲，'只存在两种不同的类加载器'：
    '启动类加载器'：它'使用C++实现'（这里仅限于Hotspot，也就是JDK1.5之后默认的虚拟机，有'很多其他的虚拟机'是'用Java语言实现的'），'是虚拟机自身的一部分'；
    '所有其它的类加载器'：'这些类加载器'都'由Java语言实现'，'独立于虚拟机之外'，并且全部'继承自抽象类'java.lang.'ClassLoader'，
    这些类加载器'需要由启动类加载器加载到内存中之后'才能去加载其他的类。

站在Java开发人员的角度来看，类加载器可以大致划分为以下三类：

启动类加载器：Bootstrap ClassLoader，负责加载'存放在JDK\jre\lib(JDK代表JDK的安装目录，下同)下'，或被'-Xbootclasspath参数'指定的路径中的，并且'能被虚拟机识别的类库'（如rt.jar，所有的java.开头的类均被Bootstrap ClassLoader加载）。'启动类加载器是无法被Java程序直接引用的'。
扩展类加载器：Extension ClassLoader，该加载器'由'sun.misc.Launcher$'ExtClassLoader实现'，它'负责加载' 'JDK\jre\lib\ext目录中'，或者由'java.ext.dirs系统变量指定的路径中的所有类库'（如javax.开头的类），'开发者可以直接使用' '扩展类加载器'。
应用程序类加载器：Application ClassLoader，该类加载器'由'sun.misc.Launcher$'AppClassLoader来实现'，'它负责加载' '用户类路径（ClassPath）所指定的类'，'开发者可以直接使用' '该类加载器'，如果应用程序中'没有自定义过自己的类加载器'，一般情况下这个就是程序中'默认的类加载器'。

'应用程序'都是'由这三种类加载器' '互相配合' 进行加载的，'如果有必要'，我们还'可以加入自定义的类加载器'。
因为'JVM自带的ClassLoader'只是懂得'从本地文件系统加载标准的java class文件'，
因此'如果编写了自己的ClassLoader'，便可以做到如下几点：

1、在'执行非置信代码之前'，'自动验证' '数字签名'。
2、'动态地创建' '符合用户特定需要的' '定制化构建类'。
3、'从特定的场所取得'java class，例如'数据库中和网络中'。


JVM类加载机制

'全盘负责'，当一个类加载器'负责加载某个Class时'，'该Class所依赖的和引用的其他Class'也将'由该类加载器负责载入'，除非'显式使用另外一个类加载器来载入'
'父类委托'，'先让父类加载器试图加载'该类，'只有在父类加载器无法加载该类时' '才尝试从自己的类路径中加载该类'
'缓存机制'，缓存机制将'会保证所有加载过的Class都会被缓存'，当程序中需要使用某个Class时，类加载器'先从缓存区寻找该Class，只有缓存区不存在，系统才会读取该类对应的二进制数据，并将其转换成Class对象，存入缓存区'。'这就是为什么修改了Class后，必须重启JVM，程序的修改才会生效'

4、类的加载
    类加载有三种方式：
        1、命令行启动应用时候'由JVM初始化加载'
        2、通过Class.forName()方法'动态加载'
        3、通过ClassLoader.loadClass()方法'动态加载'
    例子：

        package com.neo.classloader;
        public class loaderTest { 
                public static void main(String[] args) throws ClassNotFoundException { 
                        ClassLoader loader = HelloWorld.class.getClassLoader(); 
                        System.out.println(loader); 
                        // 使用ClassLoader.loadClass()来加载类，不会执行初始化块 
                        loader.loadClass("Test2"); 
                        //使用Class.forName()来加载类，默认会执行初始化块 
                        //Class.forName("Test2"); 
                        //使用Class.forName()来加载类，并指定ClassLoader，初始化时不执行静态块 
                        //Class.forName("Test2", false, loader); 
                } 
        }

        demo类
        public class Test2 { 
                static { 
                        System.out.println("静态初始化块执行了！"); 
                } 
        }
        
        '分别切换加载方式，会有不同的输出结果'。

        'Class.forName()'和'ClassLoader.loadClass()' 的区别

            Class.forName()：'将类的.class文件加载到jvm中'，并且对类进行解释，'执行类中的static块'；
            ClassLoader.loadClass()：'只干一件事情，就是将.class文件加载到jvm中'，'不会执行static中的内容','只有在newInstance时候' '才会去执行static块'。
            Class.forName(name, initialize, loader)'带参函数也可控制是否加载static块'。并且'只有调用了newInstance()方法'或采用调用构造函数，'创建类的对象' 。

5、双亲委派模型
双亲委派模型的'工作流程'是：如果一个类加载器'收到了类加载的请求'，它首先不会自己去尝试加载这个类，而是'把请求委托给父加载器'去完成，依次向上，
因此，'所有的类加载请求' 最终都应该'被传递到顶层的启动类加载器中'，只有当'父加载器' '在它的搜索范围中' '没有找到所需的类时'，即无法完成该加载，'子加载器' '才会尝试自己去加载该类'。

双亲委派机制:

1、当AppClassLoader加载一个class时，它首先不会自己去尝试加载这个类，而是把类加载请求委派给父类加载器ExtClassLoader去完成。
2、当ExtClassLoader加载一个class时，它首先也不会自己去尝试加载这个类，而是把类加载请求委派给BootStrapClassLoader```去完成。
3、如果BootStrapClassLoader加载失败（例如在$JAVA_HOME/jre/lib里未查找到该class），会使用ExtClassLoader来尝试加载；
4、若ExtClassLoader也加载失败，则会使用AppClassLoader来加载，如果AppClassLoader也加载失败，则会报出异常ClassNotFoundException。
ClassLoader源码分析：

public Class<?> loadClass(String name)throws ClassNotFoundException {
        return loadClass(name, false);
}

protected synchronized Class<?> loadClass(String name, boolean resolve)throws ClassNotFoundException {
        // 首先判断该类型是否已经被加载
        Class c = findLoadedClass(name);
        if (c == null) {
            //如果没有被加载，就委托给父类加载或者委派给启动类加载器加载
            try {
                if (parent != null) {
                     //如果存在父类加载器，就委派给父类加载器加载
                    c = parent.loadClass(name, false);
                } else {
                //如果不存在父类加载器，就检查是否是由启动类加载器加载的类，通过调用本地方法native Class findBootstrapClass(String name)
                    c = findBootstrapClass0(name);
                }
            } catch (ClassNotFoundException e) {
             // 如果父类加载器和启动类加载器都不能完成加载任务，才调用自身的加载功能
                c = findClass(name);
            }
        }
        if (resolve) {
            resolveClass(c);
        }
        return c;
    }
双亲委派模型意义：

系统类防止内存中出现多份同样的字节码
保证Java程序安全稳定运行
6、自定义类加载器
通常情况下，我们都是直接使用系统类加载器。但是，有的时候，我们也需要自定义类加载器。比如应用是通过网络来传输 Java类的字节码，为保证安全性，这些字节码经过了加密处理，这时系统类加载器就无法对其进行加载，这样则需要自定义类加载器来实现。自定义类加载器一般都是继承自ClassLoader类，从上面对loadClass方法来分析来看，我们只需要重写 findClass 方法即可。下面我们通过一个示例来演示自定义类加载器的流程：

package com.neo.classloader;
import java.io.*;

public class MyClassLoader extends ClassLoader {
    private String root;

    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] classData = loadClassData(name);
        if (classData == null) {
            throw new ClassNotFoundException();
        } else {
            return defineClass(name, classData, 0, classData.length);
        }
    }

    private byte[] loadClassData(String className) {
        String fileName = root + File.separatorChar
                + className.replace('.', File.separatorChar) + ".class";
        try {
            InputStream ins = new FileInputStream(fileName);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            int length = 0;
            while ((length = ins.read(buffer)) != -1) {
                baos.write(buffer, 0, length);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public static void main(String[] args)  {

        MyClassLoader classLoader = new MyClassLoader();
        classLoader.setRoot("E:\\temp");

        Class<?> testClass = null;
        try {
            testClass = classLoader.loadClass("com.neo.classloader.Test2");
            Object object = testClass.newInstance();
            System.out.println(object.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
自定义类加载器的核心在于对字节码文件的获取，如果是加密的字节码则需要在该类中对文件进行解密。由于这里只是演示，我并未对class文件进行加密，因此没有解密的过程。这里有几点需要注意：

1、这里传递的文件名需要是类的全限定性名称，即com.paddx.test.classloading.Test格式的，因为 defineClass 方法是按这种格式进行处理的。
2、最好不要重写loadClass方法，因为这样容易破坏双亲委托模式。
3、这类Test 类本身可以被 AppClassLoader类加载，因此我们不能把com/paddx/test/classloading/Test.class放在类路径下。否则，由于双亲委托机制的存在，会直接导致该类由AppClassLoader加载，而不会通过我们自定义类加载器来加载。